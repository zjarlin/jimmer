package org.babyfish.jimmer.sql.kt.meta

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.meta.TargetLevel
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.ExcludeFromAllScalars
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop.KDefaultOverrideBase
import org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop.KDefaultOverrideEntity
import org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop.KDefaultOverrideEntityDraft
import org.babyfish.jimmer.sql.kt.model.inheritance.overrideprop.KGenericOverrideEntity
import javax.validation.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InheritedPropAnnotationOverrideTest {

    @Test
    fun testDefaultOverride() {
        val prop = ImmutableType
            .get(KDefaultOverrideEntity::class.java)
            .getProp("status")

        assertEquals(1, prop.defaultValueRef.value)
        assertEquals("1", prop.getAnnotation(Default::class.java).value)
        assertEquals(1, prop.getAnnotations(Default::class.java).size)
        assertEquals("OVERRIDDEN_STATUS", prop.getAnnotation(Column::class.java).name)
        assertNotNull(prop.getAnnotation(ExcludeFromAllScalars::class.java))
        assertTrue(prop.isExcludedFromAllScalars)
        assertSame(
            ImmutableType.get(KDefaultOverrideBase::class.java).getProp("status"),
            prop.toOriginal(),
        )
    }

    @Test
    fun testGetterAndTypeAnnotationOverride() {
        val statusMethod = KDefaultOverrideEntityDraft.Builder::class.java
            .declaredMethods
            .single { method -> method.name == "status" }

        assertNotNull(statusMethod.getAnnotation(JsonIgnore::class.java))
        assertEquals("child", statusMethod.getAnnotation(JsonFormat::class.java).pattern)

        val accepted = KDefaultOverrideEntity {
            id = 1L
            status = 5
        }
        assertEquals(5, accepted.status)

        assertFailsWith<ValidationException> {
            KDefaultOverrideEntity {
                id = 2L
                status = 0
            }
        }
    }

    @Test
    fun testGenericMappedSuperclassOverride() {
        val prop = ImmutableType
            .get(KGenericOverrideEntity::class.java)
            .getProp("parent")

        assertEquals(KGenericOverrideEntity::class.java, prop.returnClass)
        assertEquals(KGenericOverrideEntity::class.java, prop.elementClass)
        assertTrue(prop.isReference(TargetLevel.ENTITY))
        assertNotNull(prop.getAnnotation(ManyToOne::class.java))
        assertEquals("PARENT_ID", prop.getAnnotation(JoinColumn::class.java).name)
    }
}
