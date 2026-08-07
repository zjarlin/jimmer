package org.babyfish.jimmer;

import org.babyfish.jimmer.model.Hospital;
import org.babyfish.jimmer.model.Immutables;
import org.junit.jupiter.api.Test;

import static org.babyfish.jimmer.support.JsonAssertions.assertJsonEquals;

public class Issue748Test {
    @Test
    public void test() throws Exception {
        Hospital hospital = Immutables.createHospital(it -> it.setName("XieHe"));
        String json = hospital.toString();
        assertJsonEquals("{\"name\":\"XieHe\"}", json);

        Hospital hospital2 = ImmutableObjects.fromString(Hospital.class, json);
        assertJsonEquals("{\"name\":\"XieHe\"}", hospital2.toString());
    }
}
