plugins {
    `kotlin-publish-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

sourceSets.main {
    java.srcDir("../jimmer-apt/src/main/java")
    kotlin.srcDir("../jimmer-ksp/src/main/kotlin")
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.jimmerMapstructApt)
    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)

    implementation(libs.kotlin.stdlib)
    implementation(libs.ksp.symbolProcessing.api)
    implementation(libs.spring.core)
    implementation(libs.intellij.annotations)
    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.jackson2.databind)
}
