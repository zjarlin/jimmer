import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiCore)
    api(libs.ksp.symbolProcessing.api)

    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.ksp.symbolProcessing.aa.embeddable)
    testImplementation(libs.ksp.symbolProcessing.common.deps)
}

val verifyLsiKspArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 LSI KSP 适配层只依赖 LSI core 与 KSP API"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src") {
        include("**/*.kt", "**/*.java")
    })
    allowedPlatformPathSegments.set(setOf("ksp"))
    allowedImportPrefixes.set(
        setOf(
            "com.google.devtools.ksp.",
            "java.",
            "kotlin.",
            "site.addzero.lsi.",
        )
    )
    additionalForbiddenNamespaces.set(
        setOf(
            "org.babyfish.jimmer.",
            "site.addzero.lsi.jimmer.",
            "site.addzero.lsi.poet.",
        )
    )
    captureDependencies(
        configurations = configurations,
        allowedDirectIds = setOf(
            "module:com.google.devtools.ksp:symbol-processing-api",
            "module:org.jetbrains.kotlin:kotlin-stdlib",
            "project:lsi-core",
        ),
        allowedResolvedProjectIds = setOf("project:lsi-core"),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_KSP_MODULE_PREFIXES,
        allowedResolvedModuleIds = setOf(
            "module:com.google.devtools.ksp:symbol-processing-api",
            "module:org.jetbrains.kotlin:kotlin-stdlib",
        ),
    )
}

tasks.named("check") {
    dependsOn(verifyLsiKspArchitecture)
}

private val FORBIDDEN_LSI_KSP_MODULE_PREFIXES = setOf(
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
