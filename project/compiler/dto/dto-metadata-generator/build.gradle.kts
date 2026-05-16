plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:client:client-metadata-extractor"))
    implementation(projects.project.jimmerCore)
    implementation(projects.project.jimmerDtoCompiler)
    implementation(projects.lib.lsi.lsiJimmer)
}
