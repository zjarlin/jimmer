import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
}

val verifyLsiArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "Verifies that LSI core remains platform and renderer independent"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt")
    })
}

tasks.named("check") {
    dependsOn(verifyLsiArchitecture)
}
