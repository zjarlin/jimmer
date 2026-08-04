import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiCore)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.metadata.jvm)
    testImplementation(libs.kotlin.test)
}

val verifyLsiAptArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 LSI APT 适配层只依赖 LSI core 与 JDK APT API"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src") {
        include("**/*.kt", "**/*.java")
    })
    allowedPlatformPathSegments.set(setOf("apt"))
    allowedImportPrefixes.set(
        setOf(
            "com.sun.source.",
            "java.",
            "javax.",
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
            "module:org.jetbrains.kotlin:kotlin-stdlib",
            "module:org.jetbrains.kotlin:kotlin-metadata-jvm",
            "project:lsi-core",
        ),
        allowedResolvedProjectIds = setOf("project:lsi-core"),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_APT_MODULE_PREFIXES,
        allowedResolvedModuleIds = setOf(
            "module:org.jetbrains.kotlin:kotlin-stdlib",
            "module:org.jetbrains.kotlin:kotlin-metadata-jvm",
        ),
    )
}

tasks.named("check") {
    dependsOn(verifyLsiAptArchitecture)
}

private val FORBIDDEN_LSI_APT_MODULE_PREFIXES = setOf(
    "module:com.google.devtools.ksp:",
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
