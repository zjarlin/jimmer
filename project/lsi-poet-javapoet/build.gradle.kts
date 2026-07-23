plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiPoet)

    api(libs.javapoet)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.kotlin.test)
}

val verifyJavaPoetBoundary by tasks.registering {
    group = "verification"
    description = "Verifies that the JavaPoet adapter does not depend on KotlinPoet or compiler frontends"

    val sourceFiles = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(sourceFiles)
    doLast {
        sourceFiles.files.forEach { file ->
            val content = file.readText()
            check("com.squareup.kotlinpoet" !in content) {
                "JavaPoet adapter must not import KotlinPoet: ${file.invariantSeparatorsPath}"
            }
            check("com.google.devtools.ksp" !in content) {
                "JavaPoet adapter must not import KSP: ${file.invariantSeparatorsPath}"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyJavaPoetBoundary)
}
