plugins {
    `kotlin-convention`
//    `dokka-convention`
    alias(libs.plugins.ksp)
}
dependencies {
     ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)

    implementation(project(":project:compiler:jimmer-processor-spi"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(libs.ksp.symbolProcessing.api)
    implementation(projects.project.jimmerCore)
    implementation(libs.kotlinpoet.ksp)
}
