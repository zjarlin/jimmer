plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.jimmerCompilerInput)
    implementation(projects.jimmerCompilerImmutable)
    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)
    implementation(projects.lsiJimmer)
    implementation(projects.lsiPoet)
    implementation(projects.lsiPoetJavapoet)
    implementation(projects.lsiPoetKotlinpoet)
    implementation(libs.kotlin.stdlib)
    implementation(libs.intellij.annotations)
    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.jackson2.databind)

    testImplementation(projects.jimmerCompiler)
    testImplementation(testFixtures(projects.jimmerCompilerImmutable))
    testImplementation(projects.lsiApt)
    testImplementation(projects.lsiKsp)
    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.hibernate.validation)
    testImplementation(libs.jackson3.databind)
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.api)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

tasks.test {
    useJUnit()
    forkEvery = 1
    maxParallelForks = 1
}
