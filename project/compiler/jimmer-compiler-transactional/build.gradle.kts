plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.lsiJimmer)
    implementation(projects.lsiPoet)
    implementation(projects.lsiPoetJavapoet)
    implementation(projects.lsiPoetKotlinpoet)
    implementation(libs.kotlin.stdlib)

    testImplementation(projects.jimmerCompiler)
    testImplementation(projects.lsiApt)
    testImplementation(projects.lsiKsp)
    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.api)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

tasks.test {
    useJUnit()
    forkEvery = 1
    maxParallelForks = 1
}
