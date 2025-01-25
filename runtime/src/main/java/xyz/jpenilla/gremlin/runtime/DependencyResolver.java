/*
 * gremlin
 *
 * Copyright (c) 2025 Jason Penilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.gremlin.runtime.logging.GremlinLogger;
import xyz.jpenilla.gremlin.runtime.util.HashResult;
import xyz.jpenilla.gremlin.runtime.util.HashingAlgorithm;
import xyz.jpenilla.gremlin.runtime.util.MultiAlgorithmHasher;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class DependencyResolver implements AutoCloseable {
    private static final MultiAlgorithmHasher MULTI_HASHER = new MultiAlgorithmHasher(HashingAlgorithm.SHA1, HashingAlgorithm.SHA256);
    @SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
    private static final Pattern UNIQUE_SNAPSHOT = Pattern.compile("(?:.+)-(\\d{8}\\.\\d{6}-\\d+)");
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT = "gremlin";

    private final GremlinLogger logger;
    private final HttpClient client;
    private final Map<String, ClassLoaderIsolatedJarProcessorProvider> isolatedProcessorProviders = new ConcurrentHashMap<>();
    private final Map<Thread, Object> resolving = new HashMap<>();
    private volatile boolean closed = false;

    public DependencyResolver(final GremlinLogger logger) {
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Closes any isolated {@link ClassLoader ClassLoaders} that were opened in the process of resolving
     * dependencies.
     */
    @Override
    public synchronized void close() {
        if (this.closed) {
            throw new IllegalStateException("Already closed");
        }
        if (!this.resolving.isEmpty()) {
            throw new IllegalStateException("Cannot close while resolving");
        }
        this.closed = true;
        @Nullable Exception e = null;
        for (final ClassLoaderIsolatedJarProcessorProvider provider : this.isolatedProcessorProviders.values()) {
            try {
                provider.loader().close();
            } catch (final Exception ex) {
                if (e == null) {
                    e = ex;
                } else {
                    e.addSuppressed(ex);
                }
            }
        }
        this.isolatedProcessorProviders.clear();
        if (e != null) {
            throw Util.rethrow(e);
        }

        // JDK 21+
        //noinspection ConstantValue,RedundantClassCall
        if (AutoCloseable.class.isInstance(this.client)) {
            try {
                ((AutoCloseable) this.client).close();
            } catch (final Exception ex) {
                throw new RuntimeException("Failed to close HttpClient", ex);
            }
        }
    }

    public ResolvedDependencySet resolve(final DependencySet dependencySet, final DependencyCache cache) {
        return this.resolve(dependencySet, cache, cache);
    }

    public ResolvedDependencySet resolve(
        final DependencySet dependencySet,
        final DependencyCache cache,
        final DependencyCache extensionDependencyCache
    ) {
        synchronized (this) {
            if (this.closed) {
                throw new IllegalStateException("This " + DependencyResolver.class.getSimpleName() + " has been closed");
            }
            this.resolving.put(Thread.currentThread(), new Object());
        }
        try {
            return this.resolve_(dependencySet, cache, extensionDependencyCache);
        } finally {
            synchronized (this) {
                this.resolving.remove(Thread.currentThread());
            }
        }
    }

    private ResolvedDependencySet resolve_(
        final DependencySet dependencySet,
        final DependencyCache cache,
        final DependencyCache extensionDependencyCache
    ) {
        final Map<Dependency, Path> resolved = new ConcurrentHashMap<>();
        final AtomicBoolean didWork = new AtomicBoolean(false);

        final Runnable doingWork = () -> {
            if (didWork.compareAndSet(false, true)) {
                this.logger.info("Resolving dependencies...");
            }
        };

        final ExecutorService executor = this.makeExecutor();

        final Map<String, JarProcessor> processors = this.createJarProcessors(dependencySet, executor, extensionDependencyCache, doingWork);

        final List<Callable<Void>> tasks = dependencySet.dependencies().stream().map(dep -> (Callable<Void>) () -> {
            try {
                final FileWithHashes resolve = this.resolve(dep, dependencySet.repositories(), cache, doingWork);
                if (!resolve.path().getFileName().toString().endsWith(".jar")) {
                    resolved.put(dep, resolve.path());
                    return null;
                }

                final Path processed = processJar(resolve, processors, doingWork);

                resolved.put(dep, processed);
            } catch (final IOException | IllegalArgumentException e) {
                throw new RuntimeException("Exception resolving " + dep, e);
            }
            return null;
        }).toList();

        executeTasks(executor, tasks);

        Util.shutdownExecutor(executor, TimeUnit.MILLISECONDS, 50L);

        if (didWork.get()) {
            this.logger.info("Done resolving dependencies.");
        }

        return new ResolvedDependencySet(Map.copyOf(resolved));
    }

    private static Path processJar(
        final FileWithHashes resolved,
        final Map<String, JarProcessor> processors,
        final Runnable doingWork
    ) throws IOException {
        final Path jarPath = resolved.path();

        Path in = jarPath;

        for (final Map.Entry<String, JarProcessor> processorEntry : processors.entrySet()) {
            final String extName = processorEntry.getKey();
            final JarProcessor processor = processorEntry.getValue();

            final String postfix = extName + '-' + cacheKey(processor, in, resolved);
            final String outputName = jarPath.getFileName().toString().replace(".jar", '-' + postfix + ".jar");
            final Path out = jarPath.resolveSibling(outputName);

            if (Files.isRegularFile(out)) {
                writeLastUsed(out);
                in = out;
                continue;
            }

            doingWork.run();
            final Path outTmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
            Files.deleteIfExists(outTmp);
            processor.process(in, outTmp);
            Files.move(outTmp, out);
            writeLastUsed(out);
            in = out;
        }

        return in;
    }

    private static String cacheKey(final JarProcessor processor, final Path input, final FileWithHashes resolved) throws IOException {
        final String inputHash = input.toAbsolutePath().equals(resolved.path().toAbsolutePath())
            ? resolved.sha1().asHexString()
            : HashingAlgorithm.SHA1.hashFile(input).asHexString();
        final @Nullable String processorKey = processor.cacheKey();
        if (processorKey == null) {
            return inputHash;
        }
        final String processorKeyHash = HashingAlgorithm.SHA1.hashString(processorKey).asHexString();
        return HashingAlgorithm.SHA1.hashString(processorKeyHash + inputHash).asHexString();
    }

    private Map<String, JarProcessor> createJarProcessors(
        final DependencySet dependencySet,
        final ExecutorService executor,
        final DependencyCache extensionDependencyCache,
        final Runnable attemptingDownloadCallback
    ) {
        final Map<String, JarProcessor> processors = new LinkedHashMap<>();
        for (final Map.Entry<String, Extension<?>> entry : dependencySet.extensions().entrySet()) {
            final String extName = entry.getKey();
            @SuppressWarnings("unchecked") final Extension<Object> ext = (Extension<Object>) entry.getValue();
            final @Nullable Object state = dependencySet.extensionData(extName);
            if (state == null) {
                continue;
            }

            final List<Dependency> deps = Util.sorted(ext.dependencies(state));

            if (deps.isEmpty()) {
                try {
                    final Constructor<?> ctr = Class.forName(
                        ext.processorName(),
                        true,
                        ext.getClass().getClassLoader()
                    ).getDeclaredConstructors()[0];
                    processors.put(extName, (JarProcessor) ctr.newInstance(state));
                } catch (final Exception ex) {
                    throw Util.rethrow(ex);
                }
                continue;
            }

            final ClassLoaderIsolatedJarProcessorProvider provider = this.isolatedProcessorProviders.computeIfAbsent(isolatedProcessorProviderKey(ext, deps), $ -> {
                final List<URL> depPaths = new CopyOnWriteArrayList<>();
                final List<Callable<Void>> tasks = deps.stream().map(dep -> (Callable<Void>) () -> {
                    try {
                        depPaths.add(this.resolve(dep, dependencySet.repositories(), extensionDependencyCache, attemptingDownloadCallback).path().toUri().toURL());
                        return null;
                    } catch (final IOException ex) {
                        throw Util.rethrow(ex);
                    }
                }).toList();
                executeTasks(executor, tasks);
                depPaths.add(Util.classpathUrl(ext.getClass()));

                final var loader = new URLClassLoader(depPaths.toArray(URL[]::new), ext.getClass().getClassLoader()) {
                    Class<?> load(final String name) throws ClassNotFoundException {
                        return this.findClass(name);
                    }
                };

                try {
                    final Constructor<?> ctr = loader.load(ext.processorName()).getDeclaredConstructors()[0];
                    return new ClassLoaderIsolatedJarProcessorProvider(loader, ctr);
                } catch (final Exception e) {
                    throw Util.rethrow(e);
                }
            });

            processors.put(extName, provider.processor(state));
        }
        return Collections.unmodifiableMap(processors);
    }

    private static String isolatedProcessorProviderKey(final Extension<Object> ext, final List<Dependency> deps) {
        return ext.getClass().getName() + ':' + ext.processorName() + ':' + deps.hashCode();
    }

    private static void executeTasks(final ExecutorService executor, final List<Callable<Void>> tasks) {
        try {
            final List<Future<Void>> result = executor.invokeAll(tasks, 10, TimeUnit.MINUTES);
            @Nullable RuntimeException err = null;
            for (final Future<Void> f : result) {
                try {
                    f.get();
                } catch (final ExecutionException | CancellationException e) {
                    if (err == null) {
                        err = new RuntimeException("Exception(s) resolving dependencies");
                    }
                    if (e instanceof ExecutionException) {
                        err.addSuppressed(e.getCause());
                    } else {
                        err.addSuppressed(e);
                    }
                }
            }
            if (err != null) {
                throw err;
            }
        } catch (final InterruptedException e) {
            throw Util.rethrow(e);
        }
    }

    private FileWithHashes resolve(final Dependency dependency, final List<String> repositories, final DependencyCache cache, final Runnable attemptingDownloadCallback) throws IOException {
        @Nullable Path resolved = null;
        final String mavenArtifactPath = String.format(
            "%s/%s/%s/%s-%s%s.%s",
            dependency.group().replace('.', '/'),
            dependency.name(),
            nonUniqueSnapshotIfSnapshot(dependency.version()),
            dependency.name(),
            dependency.version(),
            dependency.classifier() == null ? "" : '-' + dependency.classifier(),
            dependency.extension()
        );
        final Path outputFile = cache.cacheDirectory().resolve(mavenArtifactPath);
        if (Files.exists(outputFile)) {
            final FileWithHashes result = withHashes(outputFile);
            if (dependency.sha256().equalsIgnoreCase(result.sha256().asHexString())) {
                writeLastUsed(outputFile);
                return result;
            }
            Files.delete(outputFile);
        }
        attemptingDownloadCallback.run();
        for (String repository : repositories) {
            if (!repository.endsWith("/")) {
                repository = repository + '/';
            }
            final String urlString = repository + mavenArtifactPath;

            final HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(urlString))
                    .GET()
                    .header(USER_AGENT_HEADER, USER_AGENT)
                    .build();
            } catch (final URISyntaxException e) {
                throw Util.rethrow(e);
            }
            final HttpResponse<Path> response;
            try {
                this.logger.debug("Attempting download " + urlString);
                response = this.client.send(request, HttpResponse.BodyHandlers.ofFile(
                    Util.mkParentDirs(outputFile),
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
                ));
            } catch (final InterruptedException e) {
                throw Util.rethrow(e);
            }
            if (response == null || response.statusCode() != 200) {
                this.logger.debug("Failed to download " + urlString + ": " + (response == null ? "null response" : "response code " + response.statusCode()));
                continue;
            }
            this.logger.debug("Successfully downloaded " + urlString);
            resolved = response.body();
            break;
        }
        if (resolved == null) {
            throw new IllegalStateException("Could not resolve %s from any of %s".formatted(dependency, repositories));
        }

        final FileWithHashes result = withHashes(resolved);

        if (!dependency.sha256().equalsIgnoreCase(result.sha256().asHexString())) {
            throw new IllegalStateException("Hash for downloaded file %s was incorrect (expected: %s, got: %s)".formatted(resolved, dependency.sha256(), result.sha256().asHexString()));
        }

        writeLastUsed(resolved);
        return result;
    }

    private static FileWithHashes withHashes(final Path file) throws IOException {
        final var hashes = MULTI_HASHER.hashFile(file);
        return new FileWithHashes(file, hashes.hash(HashingAlgorithm.SHA256), hashes.hash(HashingAlgorithm.SHA1));
    }

    private record FileWithHashes(Path path, HashResult sha256, HashResult sha1) {}

    private static String nonUniqueSnapshotIfSnapshot(final String version) {
        final Matcher matcher = UNIQUE_SNAPSHOT.matcher(version);
        if (matcher.matches()) {
            final String timestamp = matcher.group(1);
            return version.replace(timestamp, "SNAPSHOT");
        }
        return version;
    }

    private static void writeLastUsed(final Path f) {
        final Path file = lastUsedFile(f);
        try {
            Files.writeString(file, String.valueOf(System.currentTimeMillis()));
        } catch (final IOException ex) {
            throw Util.rethrow(ex);
        }
    }

    static Path lastUsedFile(final Path f) {
        return f.resolveSibling(f.getFileName().toString() + ".last-used.txt");
    }

    static long lastUsed(final Path f) {
        final Path file = lastUsedFile(f);
        if (!Files.isRegularFile(file)) {
            return -1;
        }
        try {
            return Long.parseLong(Files.readString(file));
        } catch (final Exception e) {
            throw Util.rethrow(e);
        }
    }

    private ExecutorService makeExecutor() {
        return Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            new ResolverThreadFactory(this.logger)
        );
    }

    private record ClassLoaderIsolatedJarProcessorProvider(URLClassLoader loader, Constructor<?> processorConstructor) {
        JarProcessor processor(final Object config) {
            try {
                return new IsolatedProcessor(this.processorConstructor.newInstance(config));
            } catch (final Exception e) {
                throw Util.rethrow(e);
            }
        }

        private record IsolatedProcessor(Object processor) implements JarProcessor {
            @Override
            public @Nullable String cacheKey() {
                try {
                    final Method mth = this.processor.getClass().getDeclaredMethod("cacheKey");
                    return (String) mth.invoke(this.processor);
                } catch (final InvocationTargetException e) {
                    throw Util.rethrow(e.getCause());
                } catch (final ReflectiveOperationException e) {
                    throw Util.rethrow(e);
                }
            }

            @Override
            public void process(final Path input, final Path output) {
                try {
                    final Method mth = this.processor.getClass().getDeclaredMethod("process", Path.class, Path.class);
                    mth.invoke(this.processor, input, output);
                } catch (final InvocationTargetException e) {
                    throw Util.rethrow(e.getCause());
                } catch (final ReflectiveOperationException e) {
                    throw Util.rethrow(e);
                }
            }
        }
    }

    private static final class ResolverThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final GremlinLogger logger;

        ResolverThreadFactory(final GremlinLogger logger) {
            this.namePrefix = DependencyResolver.class.getSimpleName() + "-pool-" + poolNumber.getAndIncrement() + "-thread-";
            this.logger = logger;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thr = new Thread(
                null,
                runnable,
                this.namePrefix + this.threadNumber.getAndIncrement(),
                0
            );
            thr.setDaemon(true);
            thr.setPriority(Thread.NORM_PRIORITY);
            thr.setUncaughtExceptionHandler((thread, throwable) -> this.logger.warn("Uncaught exception on thread " + thread.getName(), throwable));
            return thr;
        }
    }

}
