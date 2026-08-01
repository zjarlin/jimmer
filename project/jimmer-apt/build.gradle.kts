plugins {
    `java-publish-convention`
}

dependencies {
    implementation(projects.jimmerMapstructApt)
    implementation(projects.jimmerCore)
    implementation(project(path = ":jimmer-jackson2", configuration = "runtimeElements"))
    implementation(projects.jimmerDtoCompiler)

    implementation(libs.spring.core)
    implementation(libs.intellij.annotations)
    implementation(libs.javapoet)
}
