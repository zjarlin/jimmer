package org.babyfish.jimmer.spring.cfg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class JimmerJacksonConfigTest {

    @Test
    public void testConfiguredJacksonCodecsParticipateInDynamicSelection() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("objectMapper", new ObjectMapper());
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        beanFactory.registerSingleton("jsonMapper", jsonMapper);

        JsonCodec codec = new JimmerJacksonConfig.JsonCodecConfig().jsonCodec(beanFactory);
        JacksonOnlyPayload payload = new JacksonOnlyPayload("dynamic");

        assertInstanceOf(JsonCodecDispatcher.class, codec);
        assertEquals("{\"first_name\":\"dynamic\"}", codec.encode(payload));
        assertEquals(payload, codec.decode("{\"first_name\":\"dynamic\"}", JacksonOnlyPayload.class));
    }

    public static class JacksonOnlyPayload {

        private String firstName;

        public JacksonOnlyPayload() {
        }

        public JacksonOnlyPayload(String firstName) {
            this.firstName = firstName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof JacksonOnlyPayload)) {
                return false;
            }
            JacksonOnlyPayload payload = (JacksonOnlyPayload) o;
            return firstName.equals(payload.firstName);
        }

        @Override
        public int hashCode() {
            return firstName.hashCode();
        }
    }
}
