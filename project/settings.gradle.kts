rootProject.name = "jimmer"

plugins {
  id("site.addzero.gradle.plugin.modules-buddy") version "+"
//   id("me.champeau.includegit") version "+"
}
//gitRepositories {
//    include("lsi") {
//        uri.set("https://github.com/zjarlin/lsi.git")
//        branch.set("main")
//    }
//}

//include(
//    "jimmer-bom",
//    "jimmer-core",
//    "jimmer-mapstruct-apt",
//    "jimmer-apt",
//    "jimmer-sql",
//    "jimmer-core-kotlin",
//    "jimmer-ksp",
//    "jimmer-sql-kotlin",
//    "jimmer-client",
//    "jimmer-spring-boot-starter",
//    "jimmer-dto-compiler",
//    "jimmer-client-swagger",
//)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    dependencyResolutionManagement {
        repositories {
            mavenCentral()
        }
    }
}
