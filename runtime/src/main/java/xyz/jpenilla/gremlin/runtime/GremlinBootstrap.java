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

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import xyz.jpenilla.gremlin.runtime.logging.GremlinLogger;
import xyz.jpenilla.gremlin.runtime.logging.JavaGremlinLogger;

public final class GremlinBootstrap {
    public GremlinBootstrap() {
    }

    public static void main(final String[] args) {
        final InputStream nestedJarsIndexStream = GremlinBootstrap.class.getClassLoader().getResourceAsStream("nested-jars/index.txt");
        if (nestedJarsIndexStream == null) {
            throw new IllegalStateException("Could not find nested-jars/index.txt in classpath");
        }
        final List<String> nestedJarsPaths = new ArrayList<>();
        try (nestedJarsIndexStream) {
            final String indexContent = new String(nestedJarsIndexStream.readAllBytes());
            for (final String line : indexContent.split("\n")) {
                nestedJarsPaths.add(line.trim());
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read nested-jars/index.txt", e);
        }
        final List<Path> jars = new ArrayList<>(
            extractNestedJars(nestedJarsPaths, Path.of(".gremlin/nested-jars"))
        );

        DependencySet dependencies = null;
        for (final Path jar : jars) {
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

        final DependencyCache cache = new DependencyCache(Path.of(".gremlin/dependency-cache"));

        final GremlinLogger logger = new JavaGremlinLogger(Logger.getLogger(GremlinBootstrap.class.getName()));
        try (final DependencyResolver resolver = new DependencyResolver(logger)) {
            jars.addAll(resolver.resolve(dependencies, cache).jarFiles());
        }

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

        final String mainClassName;
        try (final InputStream manifestStream = GremlinBootstrap.class.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF")) {
            if (manifestStream == null) {
                throw new IllegalStateException("Could not find META-INF/MANIFEST.MF in classpath");
            }
            final Manifest manifest = new Manifest(manifestStream);
            mainClassName = manifest.getMainAttributes().getValue("Gremlin-Main-Class");
            if (mainClassName == null || mainClassName.isEmpty()) {
                throw new IllegalStateException("Gremlin-Main-Class not specified in manifest");
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to invoke main method", e);
        }

        final ClassLoader loader = new URLClassLoader(classpath, GremlinBootstrap.class.getClassLoader());
        final Class<?> mainClass;
        try {
            mainClass = Class.forName(mainClassName, true, loader);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Main class not found: " + mainClassName, e);
        }
        final Method main;
        try {
            main = mainClass.getMethod("main", String[].class);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Main method not found in class: " + mainClassName, e);
        }
        final Thread thread = new Thread(() -> {
            try {
                main.invoke(null, (Object) args);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException("Failed to invoke main method of class: " + mainClassName, e);
            } catch (final InvocationTargetException e) {
                throw new RuntimeException("Uncaught exception in main method of class: " + mainClassName, e.getCause());
            }
        }, "Gremlin Bootstrap Thread");
        thread.setContextClassLoader(loader);
        thread.start();

        try {
            cache.cleanup();
        } catch (final Throwable e) {
            //noinspection CallToPrintStackTrace
            new RuntimeException("Exception cleaning up dependency cache", e).printStackTrace();
        }
    }

    private static List<Path> extractNestedJars(final List<String> nestedJarsPaths, final Path into) {
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
        return paths;
    }
}
