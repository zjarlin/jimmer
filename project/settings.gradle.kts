rootProject.name = "jimmer"
include(
    "jimmer-bom",
    "jimmer-core",
    "jimmer-mapstruct-apt",
    "lsi-core",
    "lsi-apt",
    "lsi-ksp",
    "lsi-poet",
    "lsi-poet-javapoet",
    "lsi-poet-kotlinpoet",
    "lsi-jimmer",
    "jimmer-compiler-core",
    "jimmer-compiler",
    "jimmer-sql",
    "jimmer-core-kotlin",
    "jimmer-sql-kotlin",
    "jimmer-client",
    "jimmer-spring-boot-starter",
    "jimmer-dto-compiler",
    "jimmer-client-swagger",
    "jimmer-client-scalar",
    "jimmer-ddl-compiler",
    "jimmer-sql-test:jimmer-sql-test-model-base",
    "jimmer-sql-test:jimmer-sql-test-model",
    "jimmer-sql-test:jimmer-sql-test-model-kotlin",
    "jimmer-sql-test:jimmer-sql-test-support",
)

val lsiRootDirectory = file("../lib/lsi")
require(lsiRootDirectory.isDirectory) {
    "LSI submodule is missing. Run 'git submodule update --init --recursive'."
}
listOf(
    "lsi-core",
    "lsi-apt",
    "lsi-ksp",
    "lsi-poet",
    "lsi-poet-javapoet",
    "lsi-poet-kotlinpoet",
    "lsi-jimmer",
).forEach { moduleName ->
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
