plugins {
    `kotlin-convention`
    alias(libs.plugins.site.addzero.gradle.plugin.processor.buddy)
}
processorBuddy {
//    val sharedDir = rootDir.resolve("shared/src/commonMain/kotlin").absolutePath
//    packageName="org.babyfish.jimmer.processor.context"
//    interfaceName = "SettingContext"
//    objectName = "Settings"
    mustMap.set(
        mapOf(
            "jimmer.source.includes" to ",",
            "jimmer.source.excludes" to ",",
            "jimmer.dto.defaultNullableInputModifier" to "static",
            "jimmer.dto.dirs" to """listOf("src/mian/dto")""",
            "jimmer.dto.testDirs" to """listOf("src/test/dto")""",
            "jimmer.dto.mutable" to "true",
            "jimmer.client.checkedException" to "true",
            "jimmer.excludedUserAnnotationPrefixes" to ",",
            "jimmer.immutable.isModuleRequired" to "true",
            "jimmer.dto.hibernateValidatorEnhancement" to "true",
            //普通用户false,jimmer buddy那边true
            "jimmer.buddy.ignoreResourceGeneration" to "false",
        )
    )
}


dependencies {
//    implementation(libs.kotlin.stdlib)

//    implementation(project(":project:ksp:jimmer-ksp-constants"))
    implementation(projects.project.jimmerCore)
    compileOnly(libs.bundles.jackson)
//    implementation(libs.javax.validation.api)
//    implementation(libs.jakarta.validation.api)
    api(project(":lib:lsi:lsi-core"))
    api(project(":lib:lsi:lsi-jimmer"))
}
