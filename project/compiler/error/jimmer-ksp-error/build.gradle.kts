plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}


dependencies {
    ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:error:error-metadata-model"))
    implementation(project(":project:compiler:error:error-metadata-extractor"))
    implementation(project(":project:compiler:error:error-metadata-generator"))
}
