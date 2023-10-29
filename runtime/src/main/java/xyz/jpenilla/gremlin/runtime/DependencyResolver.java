package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
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
import java.util.HashMap;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import xyz.jpenilla.gremlin.runtime.util.HashingAlgorithm;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class DependencyResolver implements AutoCloseable {
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT = "gremlin";

    private final Logger logger;
    private final HttpClient client;
    private final Map<String, ClassLoaderIsolatedJarProcessorProvider> isolatedProcessorProviders = new ConcurrentHashMap<>();
    private final Map<Thread, Object> resolving = new HashMap<>();
    private volatile boolean closed = false;

    public DependencyResolver(final Logger logger) {
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
        for (final ClassLoaderIsolatedJarProcessorProvider provider : this.isolatedProcessorProviders.values()) {
            try {
                provider.loader().close();
            } catch (final Exception e) {
                throw Util.rethrow(e);
            }
        }
        this.isolatedProcessorProviders.clear();
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
                final Path resolve = this.resolve(dep, dependencySet.repositories(), cache, doingWork);
                if (!resolve.getFileName().toString().endsWith(".jar")) {
                    return null;
                }

                Path in = resolve;
                for (final Map.Entry<String, JarProcessor> e : processors.entrySet()) {
                    final Path out = resolve.resolveSibling(resolve.getFileName().toString().replace(".jar", "-" + e.getKey() + ".jar"));
                    if (Files.isRegularFile(out)) {
                        writeLastUsed(out);
                        in = out;
                        continue;
                    }
                    doingWork.run();
                    final Path outTmp = out.resolveSibling(out.getFileName().toString() + ".tmp");
                    Files.deleteIfExists(outTmp);
                    e.getValue().process(in, outTmp);
                    Files.move(outTmp, out);
                    writeLastUsed(out);
                    in = out;
                }
                resolved.put(dep, in);
            } catch (final IOException | IllegalArgumentException e) {
                throw new RuntimeException("Exception resolving " + dep, e);
            }
            return null;
        }).toList();

        this.executeTasks(executor, tasks);

        Util.shutdownExecutor(executor, TimeUnit.MILLISECONDS, 50L);

        if (didWork.get()) {
            this.logger.info("Done resolving dependencies.");
        }

        return new ResolvedDependencySet(Map.copyOf(resolved));
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

    private Map<String, JarProcessor> createJarProcessors(
        final DependencySet dependencySet,
        final ExecutorService executor,
        final DependencyCache extensionDependencyCache,
        final Runnable attemptingDownloadCallback
    ) {
        final Map<String, JarProcessor> processors = new HashMap<>();
        for (final Map.Entry<String, Extension<?>> entry : dependencySet.extensions().entrySet()) {
            final String extName = entry.getKey();
            @SuppressWarnings("unchecked") final Extension<Object> ext = (Extension<Object>) entry.getValue();
            final @Nullable Object state = dependencySet.extensionData(extName);
            if (state == null) {
                continue;
            }

            final List<Dependency> deps = ext.dependencies(state);

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

            final ClassLoaderIsolatedJarProcessorProvider provider = this.isolatedProcessorProviders.computeIfAbsent(ext.getClass().getName() + ":" + ext.processorName(), $ -> {
                final List<URL> depPaths = new CopyOnWriteArrayList<>();
                final List<Callable<Void>> tasks = deps.stream().map(dep -> (Callable<Void>) () -> {
                    try {
                        depPaths.add(this.resolve(dep, dependencySet.repositories(), extensionDependencyCache, attemptingDownloadCallback).toUri().toURL());
                        return null;
                    } catch (final IOException ex) {
                        throw Util.rethrow(ex);
                    }
                }).toList();
                this.executeTasks(executor, tasks);
                depPaths.add(currentClasspathURL(ext.getClass()));

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
        return processors;
    }

    private static URL currentClasspathURL(final Class<?> cls) {
        try {
            URL sourceUrl = cls.getProtectionDomain().getCodeSource().getLocation();
            // Some class loaders give the full url to the class, some give the URL to its jar.
            // We want the containing jar, so we will unwrap jar-schema code sources.
            if (sourceUrl.getProtocol().equals("jar")) {
                final int exclamationIdx = sourceUrl.getPath().lastIndexOf('!');
                if (exclamationIdx != -1) {
                    sourceUrl = new URL(sourceUrl.getPath().substring(0, exclamationIdx));
                }
            }
            return sourceUrl;
        } catch (final MalformedURLException ex) {
            throw new RuntimeException("Could not locate classpath", ex);
        }
    }

    private void executeTasks(final ExecutorService executor, final List<Callable<Void>> tasks) {
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
                    err.addSuppressed(e);
                }
            }
            if (err != null) {
                throw err;
            }
        } catch (final InterruptedException e) {
            throw Util.rethrow(e);
        }
    }

    private Path resolve(final Dependency dependency, final List<String> repositories, final DependencyCache cache, final Runnable attemptingDownloadCallback) throws IOException {
        @Nullable Path resolved = null;
        final String mavenArtifactPath = String.format(
            "%s/%s/%s/%s-%s%s.jar",
            dependency.group().replace('.', '/'),
            dependency.name(),
            dependency.version(),
            dependency.name(),
            dependency.version(),
            dependency.classifier() == null ? "" : '-' + dependency.classifier()
        );
        final Path outputFile = cache.cacheDirectory().resolve(mavenArtifactPath);
        if (Files.exists(outputFile)) {
            if (checkHash(dependency, outputFile)) {
                writeLastUsed(outputFile);
                return outputFile;
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
                throw new RuntimeException(e);
            }
            final HttpResponse<Path> response;
            try {
                //this.logger.info("attempting to download " + urlString);
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
                //this.logger.info("Download " + urlString + " failed");
                continue;
            }
            //this.logger.info("Download " + urlString + " success");
            resolved = response.body();
            break;
        }
        if (resolved == null) {
            throw new IllegalStateException(String.format("Could not resolve dependency %s from any of %s", dependency, repositories));
        }
        if (!checkHash(dependency, resolved)) {
            throw new IllegalStateException("Hash for downloaded file %s was incorrect (expected: %s, got: %s)".formatted(resolved, dependency.sha256(), HashingAlgorithm.SHA256.hashFile(resolved).asHexString()));
        }
        writeLastUsed(resolved);
        return resolved;
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

    private static boolean checkHash(final Dependency dependency, final Path resolved) throws IOException {
        return HashingAlgorithm.SHA256.hashFile(resolved).asHexString().equalsIgnoreCase(dependency.sha256());
    }

    private ExecutorService makeExecutor() {
        return Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            new DownloaderThreadFactory(this.logger)
        );
    }

    private static final class DownloaderThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final Logger logger;

        DownloaderThreadFactory(final Logger logger) {
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
            thr.setUncaughtExceptionHandler((thread, throwable) -> this.logger.warn("Uncaught exception on thread {}", thread.getName(), throwable));
            return thr;
        }
    }

}
