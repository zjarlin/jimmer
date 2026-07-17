plugins {
    `kotlin-publish-convention`
    `dokka-convention`
}

dependencies {
    api(projects.jimmerCompilerCore)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnit()
}
