plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    ksp("dev.zacsweers.autoservice:auto-service-ksp:+")
    implementation("com.google.auto.service:auto-service-annotations:+")

    implementation(project(":ksp:jimmer-processor-spi"))
    implementation(project(":ksp:jimmer-ksp-ext"))

    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)
    implementation(libs.ksp.symbolProcessing.api)
//    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
//    implementation(libs.javax.validation.api)

}
