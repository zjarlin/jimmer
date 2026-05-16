plugins {
    `kotlin-convention`
    `dokka-convention`
}

dependencies {
    implementation(project(":project:compiler:tuple:tuple-metadata-model"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":lib:lsi:lsi-core"))
    implementation(project(":lib:lsi:lsi-jimmer"))
}
