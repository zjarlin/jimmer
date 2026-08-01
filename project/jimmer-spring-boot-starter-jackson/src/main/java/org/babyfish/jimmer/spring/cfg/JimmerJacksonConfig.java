package org.babyfish.jimmer.spring.cfg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecDispatcher;
import org.babyfish.jimmer.json.jackson.v2.ImmutableModuleV2;
import org.babyfish.jimmer.json.jackson.v2.JsonCodecProviderV2;
import org.babyfish.jimmer.json.jackson.v3.ImmutableModuleV3;
import org.babyfish.jimmer.json.jackson.v3.JsonCodecProviderV3;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class JimmerJacksonConfig {

    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(ImmutableModuleV2.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JacksonConfigV2 {
        @Bean
        public ImmutableModuleV2 immutableModuleV2() {
            return new ImmutableModuleV2();
        }
    }

    @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean(ImmutableModuleV3.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JacksonConfigV3 {
        @Bean
        public ImmutableModuleV3 immutableModuleV3() {
            return new ImmutableModuleV3();
        }
    }

    @ConditionalOnMissingBean(JsonCodec.class)
    @Configuration(proxyBeanMethods = false)
    protected static class JsonCodecConfig {
        @Bean
        public JsonCodec jsonCodec(BeanFactory beanFactory) {
            ObjectMapper objectMapper = beanFactory.getBeanProvider(ObjectMapper.class).getIfAvailable();
            JsonMapper jsonMapper = beanFactory.getBeanProvider(JsonMapper.class).getIfAvailable();
            if (objectMapper != null && jsonMapper != null) {
                return JsonCodecDispatcher.load(
                        new JsonCodecProviderV2(objectMapper),
                        new JsonCodecProviderV3(jsonMapper)
                );
            }
            if (objectMapper != null) {
                return JsonCodecDispatcher.load(new JsonCodecProviderV2(objectMapper));
            }
            if (jsonMapper != null) {
                return JsonCodecDispatcher.load(new JsonCodecProviderV3(jsonMapper));
            }
            return JsonCodecDispatcher.load();
        }
    }
}
