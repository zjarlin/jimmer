rootProject.name = "jimmer"

val moduleDirectories = linkedMapOf(
    "jimmer-bom" to "bom/jimmer-bom",
    "jimmer-client" to "client/jimmer-client",
    "jimmer-client-scalar" to "client/jimmer-client-scalar",
    "jimmer-client-swagger" to "client/jimmer-client-swagger",
    "jimmer-compiler" to "compiler/jimmer-compiler",
    "jimmer-compiler-client" to "compiler/jimmer-compiler-client",
    "jimmer-compiler-core" to "compiler/jimmer-compiler-core",
    "jimmer-compiler-ddl" to "compiler/jimmer-compiler-ddl",
    "jimmer-compiler-dto" to "compiler/jimmer-compiler-dto",
    "jimmer-compiler-error" to "compiler/jimmer-compiler-error",
    "jimmer-compiler-exportdoc" to "compiler/jimmer-compiler-exportdoc",
    "jimmer-compiler-immutable" to "compiler/jimmer-compiler-immutable",
    "jimmer-compiler-input" to "compiler/jimmer-compiler-input",
    "jimmer-compiler-module" to "compiler/jimmer-compiler-module",
    "jimmer-compiler-runtime" to "compiler/jimmer-compiler-runtime",
    "jimmer-compiler-transactional" to "compiler/jimmer-compiler-transactional",
    "jimmer-compiler-tuple" to "compiler/jimmer-compiler-tuple",
    "jimmer-ddl-compiler" to "compiler/jimmer-ddl-compiler",
    "jimmer-dto-compiler" to "compiler/jimmer-dto-compiler",
    "jimmer-mapstruct-apt" to "compiler/jimmer-mapstruct-apt",
    "jimmer-core" to "core/jimmer-core",
    "jimmer-core-kotlin" to "core/jimmer-core-kotlin",
    "jimmer-spring-boot-starter" to "spring/jimmer-spring-boot-starter",
    "jimmer-sql" to "sql/jimmer-sql",
    "jimmer-sql-kotlin" to "sql/jimmer-sql-kotlin",
    "jimmer-sql-test:jimmer-sql-test-model-base" to
        "sql/jimmer-sql-test/jimmer-sql-test-model-base",
    "jimmer-sql-test:jimmer-sql-test-model" to
        "sql/jimmer-sql-test/jimmer-sql-test-model",
    "jimmer-sql-test:jimmer-sql-test-model-kotlin" to
        "sql/jimmer-sql-test/jimmer-sql-test-model-kotlin",
    "jimmer-sql-test:jimmer-sql-test-support" to
        "sql/jimmer-sql-test/jimmer-sql-test-support",
)
include(*moduleDirectories.keys.toTypedArray())
moduleDirectories.forEach { (projectPath, directory) ->
    val moduleDirectory = file(directory)
    require(moduleDirectory.resolve("build.gradle.kts").isFile) {
        "Jimmer module '$projectPath' is missing from '$directory'."
    }
    project(":$projectPath").projectDir = moduleDirectory
}
project(":jimmer-sql-test").projectDir = file("sql/jimmer-sql-test")

val lsiRootDirectory = file("lib/lsi")
require(lsiRootDirectory.isDirectory) {
    "LSI submodule is missing. Run 'git submodule update --init --recursive'."
}
val lsiModules = listOf(
    "lsi-core",
    "lsi-apt",
    "lsi-ksp",
    "lsi-poet",
    "lsi-poet-javapoet",
    "lsi-poet-kotlinpoet",
    "lsi-jimmer",
)
include(*lsiModules.toTypedArray())
lsiModules.forEach { moduleName ->
    val moduleDirectory = lsiRootDirectory.resolve(moduleName)
    require(moduleDirectory.resolve("build.gradle.kts").isFile) {
        "LSI submodule module '$moduleName' is missing. Run 'git submodule update --init --recursive'."
    }
    project(":$moduleName").projectDir = moduleDirectory
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    dependencyResolutionManagement {
        repositories {
            mavenCentral()
        }
    }
}
