import org.babyfish.jimmer.build.VerifyCompilerArchitecture

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

val verifyDdlCompilerArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "Verifies that the DDL compiler remains a platform-independent semantic library"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*")
    })
    forbiddenRelativePaths.set(
        setOf(
            "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
            "src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider",
        )
    )
}

tasks.named("check") {
    dependsOn(verifyDdlCompilerArchitecture)
}
