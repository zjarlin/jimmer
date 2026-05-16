plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}


dependencies {
    ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:client:client-metadata-extractor"))
    implementation(project(":project:compiler:dto:dto-metadata-generator"))
    implementation(projects.lib.lsi.lsiJimmer)
}
