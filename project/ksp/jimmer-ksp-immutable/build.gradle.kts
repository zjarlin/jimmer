plugins {
    `kotlin-convention`
    `dokka-convention`
    alias(libs.plugins.ksp)
    alias(libs.plugins.site.addzero.gradle.plugin.processor.buddy)
}

processorBuddy {
    val sharedDir = rootDir.resolve("shared/src/commonMain/kotlin").absolutePath

    interfaceName = "ImmutableSettingContext"
    objectName = "ImmutableSettings"

    mustMap.set(
        mapOf(
            "jimmerImmutableIsModuleRequired" to "true",
//            "jimmerDtoDirs" to "src/main/dto,src/main/dto1",
//            "jimmerDtoTestDirs" to "src/test/dto,src/test/dto1",
//            "jimmerDtoDefaultNullableInputModifier" to "static",
//            "jimmerDtoMutable" to "true",
//            "jimmerClientCheckedException" to "true",
            //(默认值含逗号表示集合)

            "jimmerExcludedUserAnnotationPrefixes" to ",",
            //服务端生成
//            "serverGenerated" to "false",
            //显式客户端api(留空,用@EnableImplicitApi推导)
//            "explicitClientApi" to "null",
            //元组生成
//            "tupleGenerated" to "false",
            //延迟元组类型名称 (默认值含逗号表示集合)
//            "delayedTupleTypeNames" to ","
        )
    )
}

dependencies {

    implementation(project(":ksp:jimmer-processor-spi"))
//
    implementation(libs.kotlin.stdlib)
    implementation(projects.jimmerCore)
    implementation(projects.jimmerDtoCompiler)
    implementation(libs.ksp.symbolProcessing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.javax.validation.api)
    implementation(libs.jakarta.validation.api)

    implementation(project(":ksp:jimmer-ksp-ext"))
    implementation(project(":checkouts:lsi:lsi-ksp"))
}
