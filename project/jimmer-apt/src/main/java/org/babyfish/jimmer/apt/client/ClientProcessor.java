package org.babyfish.jimmer.apt.client;

import site.addzero.context.Context;
import site.addzero.lsi.apt.diagnostic.AptLsiDiagnostics;
import site.addzero.context.LsiSourceFilterKt;
import site.addzero.lsi.codegen.GeneratedResourceArtifact;
import site.addzero.lsi.jimmer.client.metadata.generator.ClientProcessorSupport;
import site.addzero.lsi.resolver.LsiResolver;

import java.util.Set;

public class ClientProcessor {

    private static final String JIMMER_CLIENT = "META-INF/jimmer/client";

    private final boolean explicitApi;

    public ClientProcessor(
            boolean explicitApi
    ) {
        this.explicitApi = explicitApi;
    }

    public void process() {
        LsiResolver resolver = Context.INSTANCE.getLsiResolver();
        Set<String> serviceTypeNames = ClientProcessorSupport.collectClientSchemaServiceTypeNames(
                resolver,
                Context.INSTANCE.getDelayedClientTypeNames(),
                explicitApi,
                LsiSourceFilterKt::matchesConfiguredSourceFilters
        );
        checkJdkVersion(serviceTypeNames);
        GeneratedResourceArtifact artifact = ClientProcessorSupport.generateClientSchemaArtifact(
                resolver,
                explicitApi,
                serviceTypeNames,
                Context.INSTANCE.guessGeneratedJimmerResourceFile("client"),
                Context.INSTANCE::convertedLsiTypeNameOf,
                Context.INSTANCE::findDraftImplDocMap
        );
        writeArtifact(artifact);
    }

    /**
     * Find this problem on `zulu-1.8 jdk`,
     * `TypeMirror.getAnnotationMirrors` always returns empty list if
     * the current `TypeMirror` is not top type but generic argument.
     */
    private void checkJdkVersion(Set<String> serviceTypeNames) {
        try {
            String.class.getMethod("isBlank");
            return;
        } catch (NoSuchMethodException e) {
            // Do nothing
        }
        // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.checkJdkVersion
        // 迁移说明：JDK8 `@FetchBy` 防护仍保留在 APT 入口，但服务探测改为复用共享 LSI client API 规则
        if (!serviceTypeNames.isEmpty()) {
            throw new FetchByUnsupportedException();
        }
    }

    private void writeArtifact(GeneratedResourceArtifact artifact) {
        // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.process 的 resource 输出
        // 迁移说明：APT 入口只负责把 metadata generator 返回的 resource artifact 落盘，
        // resource overwrite 语义统一收口到 LsiFiler adapter，processor 不再直接依赖 APT helper
        try {
            Context.INSTANCE.getLsiFiler().overwriteResourceFile(artifact.getPath(), artifact.getContent());
            Context.INSTANCE.setDelayedClientTypeNames(null);
        } catch (Exception ex) {
            throw AptLsiDiagnostics.generatorException("Cannot write \"" + JIMMER_CLIENT + "\"", ex);
        }
    }

}
