package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JimmerLsiFrontendOptionsTest {

    @Test
    fun `Jimmer frontend options keep semantic identifiers outside core`() {
        val options = emptyMap<String, String>().toJimmerLsiFrontendOptions()

        assertFalse(options.keepJavaBooleanGetterIsPrefix)
        assertEquals(setOf(JIMMER_T_NULLABLE_ANNOTATION_TYPE_ID), options.nullableAnnotationTypeIds)
        assertEquals(JIMMER_MANAGED_TYPE_ANNOTATION_TYPE_IDS, options.fullExternalDeclarationAnnotationTypeIds)
        val documentation = requireNotNull(options.documentationConvention)
        assertEquals(JIMMER_DESCRIPTION_ANNOTATION_TYPE_ID, documentation.annotationTypeId)
        assertEquals("value", documentation.valueMemberName)
        val generatedPeer = requireNotNull(documentation.generatedPeer)
        assertEquals("Draft", generatedPeer.typeSuffix)
        assertEquals(JIMMER_MANAGED_TYPE_ANNOTATION_TYPE_IDS, generatedPeer.ownerAnnotationTypeIds)
        assertEquals("set", generatedPeer.propertySetterPrefix)
    }

    @Test
    fun `Jimmer frontend options parse keep is prefix`() {
        assertTrue(
            mapOf(JIMMER_KEEP_IS_PREFIX_OPTION to "true")
                .toJimmerLsiFrontendOptions()
                .keepJavaBooleanGetterIsPrefix,
        )
    }
}
