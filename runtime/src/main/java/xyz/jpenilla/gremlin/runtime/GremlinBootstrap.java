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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.logging.GremlinLogger;
import xyz.jpenilla.gremlin.runtime.logging.JavaGremlinLogger;

@NullMarked
public final class GremlinBootstrap {
    private static final String DOT_GREMLIN = ".gremlin";
    private static final String NESTED_JARS = "nested-jars";
    private static final String NESTED_JARS_INDEX = NESTED_JARS + "/index.txt";
    private static final String DEPENDENCY_CACHE = "dependency-cache";
    private static final String GREMLIN_MAIN_CLASS = "Gremlin-Main-Class";

    public GremlinBootstrap() {
    }

    public static void main(final String[] args) {
        final List<Path> jars = new ArrayList<>(
            extractNestedJars(Path.of(DOT_GREMLIN + "/" + NESTED_JARS))
        );

        final DependencySet dependencies = getDependencies(jars);
        final DependencyCache cache = new DependencyCache(Path.of(DOT_GREMLIN + "/" + DEPENDENCY_CACHE));
        final GremlinLogger logger = new JavaGremlinLogger(Logger.getLogger(GremlinBootstrap.class.getName()));
        try (final DependencyResolver resolver = new DependencyResolver(logger)) {
            jars.addAll(resolver.resolve(dependencies, cache).jarFiles());
        }

        final String mainClassName = getMainClassName();
        final ClassLoader loader = buildClassLoader(jars);

        final Thread applicationThread = new Thread(() -> {
            final Method main = getMainMethod(mainClassName, loader);
            try {
                main.invoke(null, (Object) args);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException("Failed to invoke main method of class: " + mainClassName, e);
            } catch (final InvocationTargetException e) {
                throw new RuntimeException("Uncaught exception during application execution", e.getCause());
            }
        }, "bootstrapped-main");
        applicationThread.setContextClassLoader(loader);
        applicationThread.start();

        try {
            cache.cleanup();
        } catch (final Throwable e) {
            //noinspection CallToPrintStackTrace
            new RuntimeException("Exception cleaning up dependency cache", e).printStackTrace();
        }
    }

    private static Method getMainMethod(final String mainClassName, final ClassLoader loader) {
        final Class<?> mainClass;
        try {
            mainClass = Class.forName(mainClassName, true, loader);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Main class not found: " + mainClassName, e);
        }
        try {
            return mainClass.getMethod("main", String[].class);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Main method not found in class: " + mainClassName, e);
        }
    }

    private static String getMainClassName() {
        final String mainClassName;
        try (final InputStream manifestStream = GremlinBootstrap.class.getClassLoader().getResourceAsStream(JarFile.MANIFEST_NAME)) {
            if (manifestStream == null) {
                throw new IllegalStateException("Could not find " + JarFile.MANIFEST_NAME + " in classpath");
            }
            final Manifest manifest = new Manifest(manifestStream);
            mainClassName = manifest.getMainAttributes().getValue(GREMLIN_MAIN_CLASS);
            if (mainClassName == null || mainClassName.isEmpty()) {
                throw new IllegalStateException(GREMLIN_MAIN_CLASS + " not specified in manifest");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to read " + JarFile.MANIFEST_NAME, e);
        }
        return mainClassName;
    }

    private static DependencySet getDependencies(final List<Path> nestedJars) {
        DependencySet dependencies = null;
        for (final Path jar : nestedJars) {
            try (final JarFile jarFile = new JarFile(jar.toFile())) {
                final JarEntry entry = jarFile.getJarEntry("dependencies.txt");
                if (entry != null) {
                    try (final InputStream inputStream = jarFile.getInputStream(entry)) {
                        dependencies = DependencySet.read(inputStream);
                    }
                    break;
                }
            } catch (final Exception e) {
                throw new RuntimeException("Failed to read dependencies.txt from jar: " + jar, e);
            }
        }
        if (dependencies == null) {
            throw new IllegalStateException("No dependencies.txt found in nested jars");
        }
        return dependencies;
    }

    private static ClassLoader buildClassLoader(final List<Path> jars) {
        final URL[] classpath = jars.stream()
            .map(Path::toUri)
            .map(uri -> {
                try {
                    return uri.toURL();
                } catch (final Exception e) {
                    throw new RuntimeException("Failed to convert path to URL: " + uri, e);
                }
            })
            .toArray(URL[]::new);

        return new URLClassLoader(classpath, GremlinBootstrap.class.getClassLoader());
    }

    private static List<Path> extractNestedJars(final Path into) {
        final InputStream nestedJarsIndexStream = GremlinBootstrap.class.getClassLoader().getResourceAsStream(NESTED_JARS_INDEX);
        if (nestedJarsIndexStream == null) {
            throw new IllegalStateException("Could not find " + NESTED_JARS_INDEX + " in classpath");
        }
        final Set<String> nestedJarsPaths = new LinkedHashSet<>();
        try (nestedJarsIndexStream) {
            final String indexContent = new String(nestedJarsIndexStream.readAllBytes());
            for (final String line : indexContent.split("\n")) {
                nestedJarsPaths.add(line.trim());
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read " + NESTED_JARS_INDEX, e);
        }

        final List<Path> paths = new ArrayList<>();
        for (final String path : nestedJarsPaths) {
            final InputStream resourceStream = GremlinBootstrap.class.getClassLoader().getResourceAsStream("nested-jars/" + path);
            if (resourceStream == null) {
                throw new IllegalStateException("Could not find nested jar: " + path);
            }
            final Path outputPath = into.resolve(path);
            try {
                Files.createDirectories(outputPath.getParent());
                try (resourceStream) {
                    Files.copy(resourceStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                paths.add(outputPath);
            } catch (final Exception e) {
                throw new RuntimeException("Failed to extract nested jar: " + path, e);
            }
        }

        try (final Stream<Path> s = Files.list(into)) {
            for (final Path path : s.toList()) {
                if (Files.isRegularFile(path) && !nestedJarsPaths.contains(path.getFileName().toString())) {
                    // Delete no longer needed nested jars
                    Files.delete(path);
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to clean up nested jars directory: " + into, e);
        }

        return paths;
    }
}
