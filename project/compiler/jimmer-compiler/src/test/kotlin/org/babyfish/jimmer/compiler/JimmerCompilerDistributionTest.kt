package org.babyfish.jimmer.compiler

import java.util.ServiceLoader
import org.mapstruct.ap.spi.BuilderProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class JimmerCompilerDistributionTest {

    @Test
    fun `distribution exposes jimmer mapstruct builder provider`() {
        val providerTypeNames = ServiceLoader.load(BuilderProvider::class.java)
            .mapTo(sortedSetOf()) { provider -> provider.javaClass.name }

        assertTrue(
            "org.babyfish.jimmer.mapstruct.ap.ImmutableBuilderProvider" in providerTypeNames,
            "Missing Jimmer MapStruct builder provider: $providerTypeNames",
        )
    }
}
