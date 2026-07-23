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
    description = "验证 LSI core 不依赖 Jimmer 语义、编译器平台与渲染器"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    })
    allowedImportPrefixes.set(
        setOf(
            "java.",
            "kotlin.",
            "site.addzero.lsi.codegen.",
            "site.addzero.lsi.core.",
            "site.addzero.lsi.diagnostic.",
            "site.addzero.lsi.model.",
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
        allowedDirectIds = setOf("module:org.jetbrains.kotlin:kotlin-stdlib"),
        allowedResolvedProjectIds = emptySet(),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_MODULE_PREFIXES,
    )
}

tasks.named("check") {
    dependsOn(verifyLsiArchitecture)
}

private val FORBIDDEN_LSI_MODULE_PREFIXES = setOf(
    "module:com.google.devtools.ksp:",
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
