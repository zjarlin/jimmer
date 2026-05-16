plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:immutable:immutable-metadata-model"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(projects.project.jimmerCore)
    implementation(project(":lib:lsi:lsi-core"))
    implementation(project(":lib:lsi:lsi-jimmer"))
}
