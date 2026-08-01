plugins {
    `kotlin-publish-convention`
    `dokka-convention`
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

dependencies {
    api(projects.jimmerCore)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(projects.jimmerSqlTest.jimmerSqlTestModel)
    testImplementation(projects.jimmerJackson2)
    testImplementation(projects.jimmerJackson3)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.jackson.annotations)

    kspTest(projects.jimmerKsp)
    kspTest(projects.jimmerJackson2)
}

ksp {
    arg("jimmer.dto.kotlinxSerialization", "true")
}
