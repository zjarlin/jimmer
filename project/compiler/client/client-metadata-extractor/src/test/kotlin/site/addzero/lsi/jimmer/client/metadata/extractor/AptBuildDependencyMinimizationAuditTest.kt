package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class AptBuildDependencyMinimizationAuditTest {

    @Test
    fun `apt top level build keeps only active shared compiler dependencies`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-apt/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "implementation(projects.project.jimmerCore)",
                "implementation(projects.project.jimmerDtoCompiler)",
                "implementation(projects.lib.lsi.lsiApt)",
                "implementation(projects.lib.lsi.lsiJimmer)",
                "implementation(project(\":project:compiler:client:client-metadata-extractor\"))",
                "implementation(project(\":project:compiler:client:client-metadata-generator\"))",
                "implementation(project(\":project:compiler:dto:dto-metadata-generator\"))",
                "implementation(project(\":project:compiler:jimmer-ksp-ext\"))",
                "implementation(project(\":project:compiler:immutable:immutable-metadata-model\"))",
                "implementation(project(\":project:compiler:immutable:immutable-metadata-extractor\"))",
                "implementation(project(\":project:compiler:immutable:immutable-metadata-generator\"))",
            ),
            "APT top level build script",
        )
        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf(
                "implementation(projects.project.jimmerMapstructApt)",
                "implementation(project(\":project:compiler:client:client-metadata-model\"))",
                "implementation(libs.spring.core)",
                "implementation(libs.intellij.annotations)",
                "implementation(libs.jackson2.databind)",
            ),
            "APT top level build script",
        )
    }
}
