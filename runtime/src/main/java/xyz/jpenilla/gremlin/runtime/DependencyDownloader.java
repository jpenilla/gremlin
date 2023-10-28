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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import xyz.jpenilla.gremlin.runtime.util.HashingAlgorithm;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class DependencyDownloader {
    private final Logger logger;
    private final Path cacheDir;
    private final DependencySet dependencySet;
    private final HttpClient client;

    public DependencyDownloader(
        final Logger logger,
        final Path cacheDir,
        final DependencySet dependencySet
    ) {
        this.logger = logger;
        this.cacheDir = cacheDir;
        this.dependencySet = dependencySet;
        this.client = HttpClient.newHttpClient();
    }

    public Set<Path> resolve() {
        final Set<Path> ret = ConcurrentHashMap.newKeySet();
        final AtomicBoolean didWork = new AtomicBoolean(false);

        final Runnable doingWork = () -> {
            if (didWork.compareAndSet(false, true)) {
                this.logger.info("Resolving dependencies...");
            }
        };

        final ExecutorService executor = this.makeExecutor();

        final Map<String, ClassLoaderIsolatedJarProcessor> processors = this.createJarProcessors(executor, doingWork);

        final List<Callable<Void>> tasks = this.dependencySet.dependencies().stream().map(dep -> (Callable<Void>) () -> {
            try {
                final Path resolve = this.resolve(dep, this.dependencySet.repositories(), doingWork);
                if (!resolve.getFileName().toString().endsWith(".jar")) {
                    return null;
                }

                Path in = resolve;
                for (final Map.Entry<String, ClassLoaderIsolatedJarProcessor> e : processors.entrySet()) {
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
                ret.add(in);
            } catch (final IOException | IllegalArgumentException e) {
                throw new RuntimeException("Exception resolving " + dep, e);
            }
            return null;
        }).toList();

        this.executeTasks(executor, tasks);

        for (final ClassLoaderIsolatedJarProcessor processor : processors.values()) {
            try {
                processor.loader().close();
            } catch (final Exception e) {
                throw Util.rethrow(e);
            }
        }

        Util.shutdownExecutor(executor, TimeUnit.MILLISECONDS, 50L);

        try {
            this.cleanCache();
        } catch (final IOException ex) {
            this.logger.warn("Failed to clean cache", ex);
        }

        if (didWork.get()) {
            this.logger.info("Done resolving dependencies.");
        }

        return ret;
    }

    private record ClassLoaderIsolatedJarProcessor(URLClassLoader loader, Object processor) implements JarProcessor {
        @Override
        public void process(final Path input, final Path output) throws IOException {
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

    private Map<String, ClassLoaderIsolatedJarProcessor> createJarProcessors(
        final ExecutorService executor,
        final Runnable attemptingDownloadCallback
    ) {
        final Map<String, ClassLoaderIsolatedJarProcessor> processors = new HashMap<>();
        for (final Map.Entry<String, Extension<?>> entry : this.dependencySet.extensions().entrySet()) {
            final String extName = entry.getKey();
            @SuppressWarnings("unchecked") final Extension<Object> ext = (Extension<Object>) entry.getValue();
            final @Nullable Object state = this.dependencySet.extensionData(extName);
            if (state == null) {
                continue;
            }

            final List<URL> depPaths = new CopyOnWriteArrayList<>();
            final List<Callable<Void>> tasks = ext.dependencies(state).stream().map(dep -> (Callable<Void>) () -> {
                try {
                    depPaths.add(this.resolve(dep, this.dependencySet.repositories(), attemptingDownloadCallback).toUri().toURL());
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
                processors.put(extName, new ClassLoaderIsolatedJarProcessor(loader, ctr.newInstance(state)));
            } catch (final Exception ex) {
                throw Util.rethrow(ex);
            }
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
            this.logger.error("Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private Path resolve(final Dependency dependency, final List<String> repositories, final Runnable attemptingDownloadCallback) throws IOException {
        @Nullable Path resolved = null;
        final Path outputFile = this.cacheDir.resolve(String.format(
            "%s/%s/%s/%s-%s.jar",
            dependency.group().replace('.', '/'),
            dependency.name(),
            dependency.version(),
            dependency.name(),
            dependency.version()
        ));
        if (Files.exists(outputFile)) {
            if (checkHash(dependency, outputFile)) {
                writeLastUsed(outputFile);
                return outputFile;
            }
            Files.delete(outputFile);
        }
        attemptingDownloadCallback.run();
        for (final String repository : repositories) {
            final String urlString = String.format(
                "%s%s/%s/%s/%s-%s.jar",
                repository,
                dependency.group().replace('.', '/'),
                dependency.name(),
                dependency.version(),
                dependency.name(),
                dependency.version()
            );

            final HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(urlString)).GET().build();
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
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            if (response == null || response.statusCode() != 200) {
                //this.logger.info("Download " + urlString + " failed");
                continue;
            }
            //this.logger.info("Download " + urlString + " success");
            resolved = response.body();
            writeLastUsed(resolved);
            break;
        }
        if (resolved == null) {
            throw new IllegalStateException(String.format("Could not resolve dependency %s from any of %s", dependency, repositories));
        }
        if (!checkHash(dependency, resolved)) {
            throw new IllegalStateException("Hash for downloaded file %s was incorrect (expected: %s, got: %s)".formatted(resolved, dependency.sha256(), HashingAlgorithm.SHA256.hashFile(resolved).asHexString()));
        }
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

    private static Path lastUsedFile(final Path f) {
        return f.resolveSibling(f.getFileName().toString() + ".last-used.txt");
    }

    private static long lastUsed(final Path f) {
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

    private void cleanCache() throws IOException {
        try (final Stream<Path> s = Files.walk(this.cacheDir)) {
            s.forEach(f -> {
                if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar")) {
                    final long lastUsed = lastUsed(f);
                    if (lastUsed == -1) {
                        return;
                    }
                    final long sinceUsed = System.currentTimeMillis() - lastUsed;
                    if (sinceUsed > Duration.ofHours(1).toMillis()) {
                        try {
                            Files.delete(f);
                        } catch (final IOException e) {
                            throw Util.rethrow(e);
                        }
                    }
                }
            });
        }
    }

    private static final class DownloaderThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final Logger logger;

        DownloaderThreadFactory(final Logger logger) {
            this.namePrefix = DependencyDownloader.class.getSimpleName() + "-pool-" + poolNumber.getAndIncrement() + "-thread-";
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
