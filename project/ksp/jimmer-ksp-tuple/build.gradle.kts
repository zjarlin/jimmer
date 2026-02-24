plugins {
    `kotlin-convention`
//    `dokka-convention`
}


dependencies {

    implementation(project(":ksp:jimmer-processor-spi"))
    implementation(project(":ksp:jimmer-ksp-ext"))
//    implementation(project(":ksp:jimmer-ksp-immutable"))


//    implementation(libs.kotlin.stdlib)
    implementation(projects.jimmerCore)
//    implementation(projects.jimmerDtoCompiler)
    implementation(libs.ksp.symbolProcessing.api)
//    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
//    implementation(libs.javax.validation.api)
//    implementation(libs.jakarta.validation.api)




//    implementation(project(":project:jimmer-ksp-error"))

//    implementation(project(":project:jimmer-ksp-transactional"))
}
