plugins {
    `java-convention`
}

dependencies {
    implementation(projects.project.jimmerCore)
    implementation(projects.project.jimmerDtoCompiler)
    implementation(projects.lib.lsi.lsiApt)
    implementation(projects.lib.lsi.lsiJimmer)
    implementation(project(":project:compiler:client:client-metadata-extractor"))
    implementation(project(":project:compiler:client:client-metadata-generator"))
    implementation(project(":project:compiler:dto:dto-metadata-generator"))
    implementation(project(":project:compiler:error:error-metadata-model"))
    implementation(project(":project:compiler:error:error-metadata-extractor"))
    implementation(project(":project:compiler:error:error-metadata-generator"))
    implementation(project(":project:compiler:transactional:tx-metadata-model"))
    implementation(project(":project:compiler:transactional:tx-metadata-extractor"))
    implementation(project(":project:compiler:transactional:tx-metadata-generator"))
    implementation(project(":project:compiler:tuple:tuple-metadata-model"))
    implementation(project(":project:compiler:tuple:tuple-metadata-extractor"))
    implementation(project(":project:compiler:tuple:tuple-metadata-generator"))
    implementation(project(":project:compiler:jimmer-ksp-ext"))
    implementation(project(":project:compiler:immutable:immutable-metadata-model"))
    implementation(project(":project:compiler:immutable:immutable-metadata-extractor"))
    implementation(project(":project:compiler:immutable:immutable-metadata-generator"))
}
