plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.jimmerCompilerInput)
    implementation(projects.lsiApt)
    implementation(projects.lsiKsp)
    implementation(projects.lsiJimmer)
    implementation(libs.kotlin.stdlib)
    implementation(libs.ksp.symbolProcessing.api)

    testImplementation(projects.jimmerDtoCompiler)
    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

tasks.test {
    useJUnit()
    forkEvery = 1
    maxParallelForks = 1
}
