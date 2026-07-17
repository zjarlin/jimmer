package org.babyfish.jimmer.sql.util;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Default;
import org.babyfish.jimmer.sql.ExcludeFromAllScalars;
import org.babyfish.jimmer.sql.model.inheritance.overrideprop.DefaultOverrideBase;
import org.babyfish.jimmer.sql.model.inheritance.overrideprop.DefaultOverrideEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InheritedPropAnnotationOverrideTest {

    @Test
    public void testDefaultOverride() {
        ImmutableProp prop = ImmutableType
                .get(DefaultOverrideEntity.class)
                .getProp("status");

        assertEquals(1, prop.getDefaultValueRef().getValue());
        assertEquals("1", prop.getAnnotation(Default.class).value());
        assertEquals(1, prop.getAnnotations(Default.class).length);
        assertEquals("OVERRIDDEN_STATUS", prop.getAnnotation(Column.class).name());
        assertNotNull(prop.getAnnotation(ExcludeFromAllScalars.class));
        assertTrue(prop.isExcludedFromAllScalars());
        assertSame(
                ImmutableType.get(DefaultOverrideBase.class).getProp("status"),
                prop.toOriginal()
        );
    }
}
