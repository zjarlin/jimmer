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

    implementation(project(":ksp:jimmer-ksp-immutable"))
    implementation(project(":ksp:jimmer-ksp-client"))
    implementation(project(":ksp:jimmer-ksp-dto"))
    implementation(project(":ksp:jimmer-ksp-error"))
    implementation(project(":ksp:jimmer-ksp-transactional"))
    implementation(project(":ksp:jimmer-ksp-tuple"))
}
