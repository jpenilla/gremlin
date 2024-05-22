# gremlin
[![latest release](https://img.shields.io/gradle-plugin-portal/v/xyz.jpenilla.gremlin-gradle?label=latest%20version)](https://plugins.gradle.org/plugin/xyz.jpenilla.gremlin-gradle)

Although runtime dependency resolution is generally looked at as an anti-pattern, there are cases where it can be a necessary evil.
gremlin is a Gradle plugin and Java library for resolving Gradle `Configuration`s at runtime.

## Usage

### Gradle configuration
First, apply the `gremlin-gradle` plugin to your project.
```kotlin
plugins {
  id("xyz.jpenilla.gremlin-gradle") version "VERSION"
}
```

Applying the plugin will set up the following:
##### Extensions
- `gremlin`: The gremlin extension allows configuring plugin-wide options
  - `defaultGremlinRuntimeDependency`: Whether `gremlin-runtime` (with the same version as the Gradle plugin) should be added to the `implementation` `Configuration`
  - `defaultJarRelocatorDependencies`: Whether the default jar relocator dependencies should be added to the `jarRelocatorRuntime` `Configuration`
##### Configurations
- `runtimeDownload`: The `Configuration` that is exported by the default `writeDependencies` task
- `jarRelocatorRuntime`: The `Configuration` containing the `jar-relocator` runtime, used when there are relocations
##### Tasks
- `writeDependencies`: The default `WriteDependencyTask` registered to export the `runtimeDownload` configuration. The output (`dependencies.txt`) is added as a resource to the main source set.

#### Relocation and extensions
gremlin supports extending the runtime and Gradle plugin with custom `JarProcessor`s, and includes the `RelocationProcessor`.
Relocations set using gremlin will also need to be applied to the project output. The `ShadowGremlin` utility is provided to simplify
adding the same relocations to gremlin and `shadowJar`.
```kotlin
fun reloc(fromPkg: String, toPkg: String) {
    listOf(tasks.shadowJar, tasks.writeDependencies).forEach { task ->
        task.configure {
            ShadowGremlin.relocate(this, fromPkg, toPkg)
        }
    }
}

reloc("some.package", "relocated.some.package")
```

#### Adding runtime-downloaded dependencies
To add a runtime-downloaded dependency, simply add it to the `runtimeDownload` configuration in the same way you would for `implementation` or `compileOnly`.
Snapshot dependencies will be pinned to the currently resolved version in the output, and exclusions or other resolution rules will also apply. This is because
`gremlin-gradle` stores a flattened and resolved view of the dependencies, rather than forcing the runtime to deal with transitive dependencies and other complicated logic.

A common configuration is to also add `runtimeDownload` dependencies to `compileOnly` and `testImplementation`:
```kotlin
configurations.compileOnly {
    extendsFrom(configurations.runtimeDownload.get())
}
configurations.testImplementation {
    extendsFrom(configurations.runtimeDownload.get())
}
```

#### Advanced configurations
It is possible to register more dependency sets than the default one by manually configuring new `WriteDependencySet` tasks.

### Runtime setup

The runtime is available at `xyz.jpenilla:gremlin-runtime` on Maven Central.
You only need to manually add a dependency on it when you have disabled the automatic runtime dependency mentioned above.

The entrypoint to the runtime component is the `DependencyResolver`. The following is an example that resolves the default `dependencies.txt` set:
```java
final DependencySet deps = DependencySet.readDefault(this.getClass().getClassLoader());
final DependencyCache cache = new DependencyCache(/* cache directory */);
try (final DependencyResolver downloader = new DependencyResolver(/* slf4j logger */)) {
    final Set<Path> jars = downloader.resolve(deps, cache).jarFiles();
    // ...
}
cache.cleanup();
```

`gremlin-runtime` also provides utilities for appending to the classpath in common environments:
- `PaperClasspathAppender`: utility to append jars to a Paper plugin's classpath using the Paper `PluginLoader` API
- `DefaultsPaperPluginLoader`: prebuilt Paper `PluginLoader` that resolves the default `dependencies.txt` set and appends it to the plugin classpath using `PaperClasspathAppender`.
- `FabricClasspathAppender`: utility to append jars to the Fabric classpath
- `VelocityClasspathAppender`: utility to append jars to a Velocity plugin's classpath
