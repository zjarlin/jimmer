plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.jimmerDtoCompiler)
    implementation(projects.lsiJimmer)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnit()
}
