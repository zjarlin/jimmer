plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}


dependencies {
     ksp("dev.zacsweers.autoservice:auto-service-ksp:+")
  // NOTE: It's important that you _don't_ use compileOnly here, as it will fail to resolve at compile-time otherwise
  implementation("com.google.auto.service:auto-service-annotations:+")

    implementation(project(":ksp:jimmer-processor-spi"))
    implementation(project(":ksp:jimmer-ksp-ext"))
    implementation(project(":ksp:jimmer-ksp-constants"))
    implementation(project(":ksp:jimmer-ksp-immutable"))


//    implementation(libs.kotlin.stdlib)
    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)
    implementation(libs.ksp.symbolProcessing.api)
//    implementation(libs.kotlinpoet)
//    implementation(libs.kotlinpoet.ksp)
//    implementation(libs.javax.validation.api)
//    implementation(libs.jakarta.validation.api)


    implementation(project(":ksp:jimmer-ksp-client"))

    implementation(project(":ksp:jimmer-ksp-dto"))

    implementation(project(":ksp:jimmer-ksp-error"))

    implementation(project(":ksp:jimmer-ksp-transactional"))

    implementation(project(":ksp:jimmer-ksp-tuple"))
}
