rootProject.name = "gremlin"
include("runtime")
findProject(":runtime")?.name = "gremlin-runtime"
include("gradle-plugin")
findProject(":gradle-plugin")?.name = "gremlin-gradle"
