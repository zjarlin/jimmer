import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiCore)

    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
}

val verifyLsiPoetArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "Verifies that the shared LSI Poet model stays platform and Poet independent"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    })
}

tasks.named("check") {
    dependsOn(verifyLsiPoetArchitecture)
}
