import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(projects.jimmerCompilerCore)
    implementation(projects.lsiPoet)
    implementation(projects.lsiPoetJavapoet)
    implementation(projects.lsiPoetKotlinpoet)
    implementation(projects.lsiJimmer)
    implementation(projects.jimmerDdlCompiler)
    implementation(projects.jimmerMapstructApt)
    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.metadata.jvm)
    implementation(libs.ksp.symbolProcessing.api)
    implementation(libs.spring.core)
    implementation(libs.intellij.annotations)
    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.jackson2.databind)

    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

tasks.test {
    useJUnit()
    forkEvery = 1
    maxParallelForks = 1
}

val verifySharedCompilerArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "Verifies that shared compiler code stays outside platform and Poet boundaries"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main/kotlin/org/babyfish/jimmer/compiler") {
        include("**/*.kt")
    })
    allowedPlatformPathSegments.set(setOf("apt", "ksp"))
    allowedPoetPathSegments.set(setOf("render"))
    allowedPoetFileSuffixes.set(
        setOf(
            "ErrorJavaRenderer.kt",
            "ErrorKotlinRenderer.kt",
            "JimmerImmutableDraftJavaRenderer.kt",
            "JimmerImmutableDraftJavaValidationRenderer.kt",
            "JimmerImmutableDraftKotlinRenderer.kt",
            "JimmerImmutableDraftKotlinRuntimeRenderer.kt",
            "JimmerImmutableDraftKotlinValidationRenderer.kt",
            "JimmerImmutableEmbeddableJavaRenderer.kt",
            "JimmerImmutableEmbeddableKotlinRenderer.kt",
            "JimmerImmutableFetcherJavaRenderer.kt",
            "JimmerImmutableFetcherKotlinRenderer.kt",
            "JimmerImmutableQueryJavaRenderer.kt",
            "JimmerImmutableQueryKotlinRenderer.kt",
        )
    )
}

tasks.named("check") {
    dependsOn(verifySharedCompilerArchitecture)
}
