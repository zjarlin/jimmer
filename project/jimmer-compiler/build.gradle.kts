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
    implementation(libs.kotlin.metadata.jvm)
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
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

tasks.test {
    useJUnit()
    forkEvery = 1
    maxParallelForks = 1
}
