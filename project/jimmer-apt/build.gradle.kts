plugins {
    `java-convention`
}

dependencies {
    implementation(projects.project.jimmerMapstructApt)
    implementation(projects.project.jimmerCore)
    implementation(projects.project.jimmerDtoCompiler)

    implementation(libs.spring.core)
    implementation(libs.intellij.annotations)
    implementation(libs.javapoet)
    implementation(libs.jackson2.databind)
}
