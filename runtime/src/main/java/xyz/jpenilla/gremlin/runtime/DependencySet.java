package xyz.jpenilla.gremlin.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record DependencySet(
    List<String> repositories,
    List<Dependency> dependencies,
    Map<String, Extension<?>> extensions,
    Map<String, ?> extensionData
) {
    @SuppressWarnings("unchecked")
    <S> @Nullable S extensionData(final String name) {
        final @Nullable Object o = this.extensionData.get(name);
        if (o == null) {
            return null;
        }
        return (S) o;
    }

    public static Map<String, Extension<?>> defaultExtensions() {
        return Map.of("relocation", new RelocationExtension());
    }

    public static DependencySet readDefault(final ClassLoader loader) {
        return readDefault(loader, defaultExtensions());
    }

    public static DependencySet readDefault(
        final ClassLoader loader,
        final Map<String, Extension<?>> extensions
    ) {
        return readFromClasspathResource(loader, "dependencies.txt", extensions);
    }

    public static DependencySet readFromClasspathResource(final ClassLoader loader, final String resourceName) {
        return readFromClasspathResource(loader, resourceName, defaultExtensions());
    }

    public static DependencySet readFromClasspathResource(
        final ClassLoader loader,
        final String resourceName,
        final Map<String, Extension<?>> extensions
    ) {
        try (final InputStream stream = Objects.requireNonNull(
            loader.getResourceAsStream(resourceName),
            "Could not get InputStream for " + resourceName
        )) {
            return DependencySet.read(stream, extensions);
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to read dependency set " + resourceName, ex);
        }
    }

    public static DependencySet read(final InputStream inputStream) throws IOException {
        return read(inputStream, defaultExtensions());
    }

    public static DependencySet read(
        final InputStream inputStream,
        final Map<String, Extension<?>> extensions
    ) throws IOException {
        final List<String> repositories = new ArrayList<>();
        final List<Dependency> dependencies = new ArrayList<>();
        final Map<String, List<String>> extraSections = new HashMap<>();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            @Nullable String currentSection = null;
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.equals("__end__")) {
                    if (currentSection == null) {
                        throw new IllegalStateException("Encountered section end when not in a section");
                    }
                    currentSection = null;
                    continue;
                }
                if (line.startsWith("__") && line.endsWith("__")) {
                    if (currentSection != null) {
                        throw new IllegalStateException("Encountered section header when already in a section");
                    }
                    currentSection = line.substring(2, line.length() - 2);
                    continue;
                }
                if (currentSection == null) {
                    throw new IllegalStateException("Received content when not in a section");
                }

                switch (currentSection) {
                    case "repos" -> repositories.add(line);
                    case "deps" -> {
                        final String[] split = line.split(" ");
                        dependencies.add(Dependency.parse(split[0], split[1]));
                    }
                    default -> extraSections.computeIfAbsent(currentSection, $ -> new ArrayList<>()).add(line);
                }
            }
        }

        return new DependencySet(
            repositories,
            dependencies,
            extensions,
            parseExtensionConfigs(extensions, extraSections)
        );
    }

    private static Map<String, ?> parseExtensionConfigs(
        final Map<String, Extension<?>> extensions,
        final Map<String, List<String>> extraSections
    ) {
        return extraSections.entrySet().stream().map(e -> {
            final @Nullable Extension<?> extension = extensions.get(e.getKey());
            if (extension == null) {
                throw new IllegalStateException("No such extension '" + e.getKey() + "'");
            }
            return Map.entry(e.getKey(), extension.parseConfig(e.getValue()));
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
