plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}


dependencies {
     ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)

    implementation(project(":ksp:jimmer-processor-spi"))
    implementation(project(":ksp:jimmer-ksp-ext"))
    implementation("site.addzero:lsi-ksp:2026.02.26")

    implementation(projects.jimmerDtoCompiler)
    implementation(projects.jimmerCore)
    implementation(libs.ksp.symbolProcessing.api)
    implementation(libs.kotlinpoet.ksp)
}
