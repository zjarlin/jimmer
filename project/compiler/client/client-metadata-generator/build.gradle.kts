plugins {
    `kotlin-convention`
    `dokka-convention`
}

val jimmerCoreProject = project(":project:jimmer-core")

dependencies {
    implementation(project(":project:compiler:client:client-metadata-model"))
    implementation(project(":project:compiler:client:client-metadata-extractor"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(projects.project.jimmerCore)
    testRuntimeOnly(libs.bundles.jackson)
    testRuntimeOnly(
        files(
            jimmerCoreProject.layout.buildDirectory.dir("classes/kotlin/main"),
            jimmerCoreProject.layout.buildDirectory.dir("classes/java/main"),
            jimmerCoreProject.layout.buildDirectory.dir("resources/main"),
        )
    )
}
