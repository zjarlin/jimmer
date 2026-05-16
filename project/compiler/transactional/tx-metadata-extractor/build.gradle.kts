plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:transactional:tx-metadata-model"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":lib:lsi:lsi-core"))
}
