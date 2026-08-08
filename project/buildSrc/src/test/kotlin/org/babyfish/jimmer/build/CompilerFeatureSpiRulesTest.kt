package org.babyfish.jimmer.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompilerFeatureSpiRulesTest {

    @Test
    fun `拒绝旧服务入口和非规范功能实现名`() {
        val violations = findCompilerFeatureSpiViolations(
            listOf(
                source(
                    "jimmer-compiler-client/src/main/resources/META-INF/services/" +
                        "site.addzero.lsi.compiler.CompilerFeatureProvider",
                    "org.example.LegacyFeatureProvider",
                ),
                source(
                    "jimmer-compiler-client/src/main/resources/META-INF/services/" +
                        "site.addzero.lsi.compiler.CompilerFeature",
                    "org.example.JimmerClientFeatureProvider",
                ),
            )
        )

        assertEquals(
            listOf(
                "jimmer-compiler-client/src/main/resources/META-INF/services/" +
                    "site.addzero.lsi.compiler.CompilerFeature:1: " +
                    "invalid feature implementation org.example.JimmerClientFeatureProvider",
                "jimmer-compiler-client/src/main/resources/META-INF/services/" +
                    "site.addzero.lsi.compiler.CompilerFeatureProvider: forbidden legacy feature service",
            ),
            violations,
        )
    }

    @Test
    fun `拒绝旧 SPI 命名和手工状态强转`() {
        val violations = findCompilerFeatureSpiViolations(
            listOf(
                source(
                    "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt",
                    listOf(
                        "class JimmerClientFeatureState",
                        "class ClientFeatureProvider : CompilerFeatureProvider",
                        "val id = FEATURE_ID",
                        "val state = result.state as ClientFeatureState",
                    ).joinToString("\n"),
                )
            )
        )

        assertEquals(
            listOf(
                "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt:1: " +
                    "forbidden feature identifier JimmerClientFeatureState",
                "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt:2: " +
                    "forbidden feature identifier ClientFeatureProvider",
                "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt:2: " +
                    "forbidden feature identifier CompilerFeatureProvider",
                "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt:3: " +
                    "forbidden feature identifier FEATURE_ID",
                "jimmer-compiler-client/src/main/kotlin/example/Legacy.kt:4: " +
                    "manual feature state cast as ClientFeatureState",
            ),
            violations,
        )
    }

    @Test
    fun `忽略注释和普通字符串并接受 typed feature`() {
        val violations = findCompilerFeatureSpiViolations(
            listOf(
                source(
                    "jimmer-compiler-client/src/main/kotlin/example/ClientFeature.kt",
                    listOf(
                        "// class ClientFeatureProvider",
                        "val legacy = \"JimmerClientFeatureState\"",
                        "class ClientFeature : CompilerFeature<EmptyState, ClientFeatureState>",
                    ).joinToString("\n"),
                ),
                source(
                    "jimmer-compiler-client/src/main/resources/META-INF/services/" +
                        "site.addzero.lsi.compiler.CompilerFeature",
                    "org.example.ClientFeature\n",
                ),
            )
        )

        assertTrue(violations.isEmpty(), violations.toString())
    }

    private fun source(relativePath: String, content: String): CompilerFeatureSpiSource {
        return CompilerFeatureSpiSource(relativePath, content)
    }
}
