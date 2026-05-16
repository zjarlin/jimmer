plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:transactional:tx-metadata-extractor"))
    implementation(project(":project:compiler:transactional:tx-metadata-model"))
    implementation(project(":lib:lsi:lsi-core"))

    testImplementation(project(":lib:lsi:lsi-ksp"))
}
