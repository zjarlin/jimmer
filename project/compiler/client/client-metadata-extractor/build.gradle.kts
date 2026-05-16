plugins {
    `kotlin-convention`
    `dokka-convention`
}

val jimmerCoreProject = project(":project:jimmer-core")

dependencies {
    implementation(project(":project:compiler:client:client-metadata-model"))
    implementation(projects.lib.lsi.lsiCore)
    implementation(projects.lib.lsi.lsiJimmer)
    implementation(projects.project.jimmerCore)
    compileOnly(libs.bundles.jackson)
    testRuntimeOnly(
        files(
            jimmerCoreProject.layout.buildDirectory.dir("classes/kotlin/main"),
            jimmerCoreProject.layout.buildDirectory.dir("classes/java/main"),
            jimmerCoreProject.layout.buildDirectory.dir("resources/main"),
        )
    )
}
