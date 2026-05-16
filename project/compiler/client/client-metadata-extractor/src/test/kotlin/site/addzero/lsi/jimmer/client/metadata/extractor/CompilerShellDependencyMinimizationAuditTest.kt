package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Test

class CompilerShellDependencyMinimizationAuditTest {

    @Test
    fun `ksp client shell keeps only shared compiler dependencies it still needs`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/compiler/client/jimmer-ksp-client/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "implementation(project(\":project:compiler:jimmer-ksp-ext\"))",
                "implementation(project(\":project:compiler:client:client-metadata-extractor\"))",
                "implementation(project(\":project:compiler:client:client-metadata-generator\"))",
            ),
            "KSP client shell build script",
        )
        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf(
                "implementation(project(\":project:compiler:client:client-metadata-model\"))",
                "implementation(projects.project.jimmerCore)",
                "implementation(projects.project.jimmerDtoCompiler)",
            ),
            "KSP client shell build script",
        )
    }

    @Test
    fun `ksp immutable shell drops metadata model and dto compiler direct deps after shared extraction cutover`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/compiler/immutable/jimmer-ksp-immutable/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "implementation(project(\":project:compiler:jimmer-ksp-ext\"))",
                "implementation(project(\":project:compiler:immutable:immutable-metadata-extractor\"))",
                "implementation(project(\":project:compiler:immutable:immutable-metadata-generator\"))",
            ),
            "KSP immutable shell build script",
        )
        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf(
                "implementation(project(\":project:compiler:immutable:immutable-metadata-model\"))",
                "implementation(projects.project.jimmerDtoCompiler)",
            ),
            "KSP immutable shell build script",
        )
    }

    @Test
    fun `top level ksp bootstrap keeps a single dto compiler dependency declaration`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/jimmer-ksp/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsAll(
            source,
            listOf(
                "implementation(project(\":project:jimmer-dto-compiler\"))",
                "implementation(project(\":lib:lsi:lsi-ksp\"))",
            ),
            "top level KSP bootstrap build script",
        )
        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf("implementation(projects.project.jimmerDtoCompiler)"),
            "top level KSP bootstrap build script",
        )
    }
}
