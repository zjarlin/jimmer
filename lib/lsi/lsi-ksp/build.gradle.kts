plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    api(project(":lib:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2026.02.23")
    // KSP API dependencies
    api("com.google.devtools.ksp:symbol-processing-api:2.3.6")
    // kotlinpoet: LsiClass → ClassName bridge
    api("com.squareup:kotlinpoet:2.2.0")
    implementation("com.squareup:kotlinpoet-ksp:2.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}


description = "LSI系统的KSP实现模块"
