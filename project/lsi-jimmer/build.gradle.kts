import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
    `dokka-convention`
}

dependencies {
    api(projects.lsiCore)
    api(projects.jimmerDtoCompiler)
    testImplementation(libs.kotlin.test)
}

val verifyLsiJimmerArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 Jimmer LSI 语义扩展不依赖编译器平台与渲染器"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    })
    allowedImportPrefixes.set(
        setOf(
            "java.",
            "kotlin.",
            "org.babyfish.jimmer.dto.compiler.",
            "site.addzero.lsi.codegen.",
            "site.addzero.lsi.core.",
            "site.addzero.lsi.diagnostic.",
            "site.addzero.lsi.jimmer.",
            "site.addzero.lsi.model.",
        )
    )
    additionalForbiddenNamespaces.set(
        setOf(
            "org.babyfish.jimmer.client.",
            "org.babyfish.jimmer.compiler.",
            "org.babyfish.jimmer.meta.",
            "org.babyfish.jimmer.runtime.",
            "org.babyfish.jimmer.sql.",
            "site.addzero.lsi.poet.",
        )
    )
    captureDependencies(
        configurations = configurations,
        allowedDirectIds = setOf(
            "module:org.jetbrains.kotlin:kotlin-stdlib",
            "project:jimmer-dto-compiler",
            "project:lsi-core",
        ),
        allowedResolvedProjectIds = setOf(
            "project:jimmer-dto-compiler",
            "project:lsi-core",
        ),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_JIMMER_MODULE_PREFIXES,
        allowedResolvedModuleIds = setOf(
            "module:org.babyfish.jimmer:jimmer-dto-compiler",
            "module:org.babyfish.jimmer:lsi-core",
        ),
    )
}

tasks.named("check") {
    dependsOn(verifyLsiJimmerArchitecture)
}

private val FORBIDDEN_LSI_JIMMER_MODULE_PREFIXES = setOf(
    "module:com.google.devtools.ksp:",
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
