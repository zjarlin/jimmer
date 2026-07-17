plugins {
    `kotlin-publish-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.jimmerDdlCompiler)
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

    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(projects.jimmerSql)
}

tasks.test {
    useJUnit()
}
