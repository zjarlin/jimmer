plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:tuple:tuple-metadata-extractor"))
    implementation(project(":project:compiler:tuple:tuple-metadata-model"))
    implementation(project(":lib:lsi:lsi-core"))

    testImplementation(project(":lib:lsi:lsi-ksp"))
    testImplementation(project(":lib:lsi:lsi-apt"))
}
