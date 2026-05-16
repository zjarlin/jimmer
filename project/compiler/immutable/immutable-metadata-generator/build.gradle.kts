plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:immutable:immutable-metadata-extractor"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:immutable:immutable-metadata-model"))
    implementation(projects.project.jimmerCore)

    testImplementation(project(":lib:lsi:lsi-ksp"))
    testImplementation(project(":lib:lsi:lsi-apt"))
}
