plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {

    api(project(":lib:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2026.02.23")
    api("com.squareup:javapoet:1.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
