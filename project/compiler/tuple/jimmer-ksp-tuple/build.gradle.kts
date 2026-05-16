plugins {
    `kotlin-convention`
//    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:tuple:tuple-metadata-model"))
    implementation(project(":project:compiler:tuple:tuple-metadata-extractor"))
    implementation(project(":project:compiler:tuple:tuple-metadata-generator"))
}
