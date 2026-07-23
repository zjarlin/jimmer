plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiPoet)

    api(libs.kotlinpoet)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.kotlin.test)
}

val verifyKotlinPoetBoundary by tasks.registering {
    group = "verification"
    description = "Verifies that the KotlinPoet adapter does not depend on JavaPoet or compiler frontends"

    val sourceFiles = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(sourceFiles)
    doLast {
        sourceFiles.files.forEach { file ->
            val content = file.readText()
            check("com.squareup.javapoet" !in content) {
                "KotlinPoet adapter must not import JavaPoet: ${file.invariantSeparatorsPath}"
            }
            check("com.google.devtools.ksp" !in content) {
                "KotlinPoet adapter must not import KSP: ${file.invariantSeparatorsPath}"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyKotlinPoetBoundary)
}
