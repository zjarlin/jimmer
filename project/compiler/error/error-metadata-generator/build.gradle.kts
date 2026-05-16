plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:error:error-metadata-extractor"))
    implementation(project(":project:compiler:error:error-metadata-model"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":lib:lsi:lsi-core"))
    implementation(project(":lib:lsi:lsi-jimmer"))

    testImplementation(project(":lib:lsi:lsi-ksp"))
    testImplementation(project(":lib:lsi:lsi-apt"))
}
