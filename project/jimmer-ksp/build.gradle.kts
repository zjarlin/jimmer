plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    ksp(libs.auto.service.ksp)
    implementation(libs.auto.service.annotations)
    implementation(project(":project:compiler:jimmer-processor-spi"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:immutable:jimmer-ksp-immutable"))
    implementation(project(":project:compiler:client:jimmer-ksp-client"))
    implementation(project(":project:compiler:dto:jimmer-ksp-dto"))
    implementation(project(":project:compiler:error:jimmer-ksp-error"))
    implementation(project(":project:compiler:transactional:jimmer-ksp-transactional"))
    implementation(project(":project:compiler:tuple:jimmer-ksp-tuple"))
    implementation(libs.ksp.symbolProcessing.api)
    implementation(projects.project.jimmerDtoCompiler)
    implementation(project(":project:jimmer-dto-compiler"))
}
