plugins {
    `kotlin-convention`
//    `dokka-convention`
    alias(libs.plugins.ksp)

}


dependencies {
    ksp("dev.zacsweers.autoservice:auto-service-ksp:+")
    implementation("com.google.auto.service:auto-service-annotations:+")

    implementation(project(":ksp:jimmer-processor-spi"))
    implementation(project(":ksp:jimmer-ksp-ext"))
    implementation(libs.ksp.symbolProcessing.api)
    implementation(projects.jimmerCore)
    implementation(libs.kotlinpoet.ksp)
}
