import org.babyfish.jimmer.build.VerifyCompilerArchitecture
import org.babyfish.jimmer.build.VerifyCompilerEntrypoints

plugins {
    `kotlin-publish-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(projects.jimmerCompilerRuntime)
    runtimeOnly(projects.jimmerCompilerClient)
    runtimeOnly(projects.jimmerCompilerDdl)
    runtimeOnly(projects.jimmerCompilerDto)
    runtimeOnly(projects.jimmerCompilerError)
    runtimeOnly(projects.jimmerCompilerExportdoc)
    runtimeOnly(projects.jimmerCompilerImmutable)
    runtimeOnly(projects.jimmerCompilerModule)
    runtimeOnly(projects.jimmerCompilerTransactional)
    runtimeOnly(projects.jimmerCompilerTuple)

    implementation(libs.kotlin.stdlib)
    implementation(libs.ksp.symbolProcessing.api)

    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(projects.jimmerCore)
    testImplementation(projects.jimmerSql)
    testImplementation(projects.jimmerSqlKotlin)
    testImplementation(libs.hibernate.validation)
    testImplementation(libs.jackson3.databind)
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

    val compilerDirectory = layout.projectDirectory.dir("..")
    baseDirectory.set(compilerDirectory)
    sourceFiles.from(fileTree(compilerDirectory) {
        include("jimmer-compiler*/src/main/**/*.kt", "jimmer-compiler*/src/main/**/*.java")
    })
    allowedPlatformPathSegments.set(setOf("apt", "ksp"))
    allowedPoetRelativePathPrefixes.set(
        setOf(
            "jimmer-compiler-dto/src/main/kotlin/org/babyfish/jimmer/compiler/render/apt",
            "jimmer-compiler-dto/src/main/kotlin/org/babyfish/jimmer/compiler/render/ksp",
            "jimmer-compiler-immutable/src/main/kotlin/org/babyfish/jimmer/compiler/render/apt",
            "jimmer-compiler-immutable/src/main/kotlin/org/babyfish/jimmer/compiler/render/ksp",
        )
    )
    additionalForbiddenNamespaces.set(
        setOf(
            "org.babyfish.jimmer.apt",
            "org.babyfish.jimmer.ksp",
        )
    )
}

val verifyCompilerEntrypoints by tasks.registering(VerifyCompilerEntrypoints::class) {
    group = "verification"
    description = "验证聚合 compiler 只保留 APT 与 KSP 两个入口"

    val sourceRoot = layout.projectDirectory.dir("src/main")
    this.sourceRoot.set(sourceRoot)
    sourceFiles.from(fileTree(sourceRoot) { include("**/*.kt", "**/*.java") })
    expectedRelativePaths.set(
        setOf(
            "kotlin/org/babyfish/jimmer/compiler/apt/JimmerProcessor.kt",
            "kotlin/org/babyfish/jimmer/compiler/ksp/JimmerProcessorProvider.kt",
        )
    )
    maxNonBlankLineCount.set(3)
}

tasks.named("check") {
    dependsOn(
        ":jimmer-compiler-client:check",
        ":jimmer-compiler-ddl:check",
        ":jimmer-compiler-dto:check",
        ":jimmer-compiler-error:check",
        ":jimmer-compiler-exportdoc:check",
        ":jimmer-compiler-immutable:check",
        ":jimmer-compiler-input:check",
        ":jimmer-compiler-module:check",
        ":jimmer-compiler-runtime:check",
        ":jimmer-compiler-transactional:check",
        ":jimmer-compiler-tuple:check",
    )
    dependsOn(verifySharedCompilerArchitecture)
    dependsOn(verifyCompilerEntrypoints)
}
