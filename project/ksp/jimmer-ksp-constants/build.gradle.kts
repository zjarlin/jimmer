plugins {
    `kotlin-convention`
//    `dokka-convention`
}
dependencies {
    implementation(projects.jimmerCore)
    implementation(libs.kotlinpoet)
}
