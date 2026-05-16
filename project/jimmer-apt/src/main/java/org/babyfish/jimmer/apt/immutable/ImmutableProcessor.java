package org.babyfish.jimmer.apt.immutable;

import site.addzero.context.Context;
import site.addzero.context.LsiSourceFilterKt;
import site.addzero.context.Settings;
import site.addzero.lsi.clazz.LsiClass;
import site.addzero.lsi.codegen.GeneratedResourceArtifact;
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceAccumulator;
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceResolution;
import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableGeneratedOutput;
import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableProcessorSupport;
import site.addzero.lsi.poet.LsiFileSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

public class ImmutableProcessor {

    private final boolean buddyIgnoreResourceGeneration;

    public ImmutableProcessor(boolean buddyIgnoreResourceGeneration) {
        this.buddyIgnoreResourceGeneration = buddyIgnoreResourceGeneration;
    }

    public Collection<LsiClass> process() {
        ImmutableCollectedSourceResolution resolvedSources = resolveImmutableSources();
        generateJimmerTypes(resolvedSources);
        return new ArrayList<>(resolvedSources.getLsiClasses());
    }

    private ImmutableCollectedSourceResolution resolveImmutableSources() {
        ImmutableCollectedSourceAccumulator accumulator = new ImmutableCollectedSourceAccumulator();
        ImmutableProcessorSupport.collectRoundSources(
                accumulator,
                Context.INSTANCE.getLsiResolver(),
                LsiSourceFilterKt::matchesConfiguredSourceFilters
        );
        ImmutableCollectedSourceResolution resolvedSources = ImmutableProcessorSupport.resolveCollectedSources(
                accumulator,
                Context.INSTANCE.getLsiResolver(),
                Context.INSTANCE::typeOf
        );
        if (ImmutableProcessorSupport.hasImmutableTypes(resolvedSources)) {
            Context.INSTANCE.resolve();
        }
        return resolvedSources;
    }

    private void generateJimmerTypes(ImmutableCollectedSourceResolution resolvedSources) {
        if (!ImmutableProcessorSupport.hasImmutableTypes(resolvedSources)) {
            return;
        }
        ImmutableGeneratedOutput generatedOutput = ImmutableProcessorSupport.generateAptOutput(
                resolvedSources,
                Settings.INSTANCE.getJimmerExcludedUserAnnotationPrefixes(),
                Context.INSTANCE.getJacksonTypes(),
                findGeneratedEntitiesResourceFile(),
                buddyIgnoreResourceGeneration,
                Settings.INSTANCE.getJimmerImmutableIsModuleRequired(),
                org.babyfish.jimmer.JimmerVersionsKt.currentVersion()
        );
        for (LsiFileSpec fileSpec : generatedOutput.getSourceFileSpecs()) {
            writeFileSpec(fileSpec);
        }
        for (GeneratedResourceArtifact artifact : generatedOutput.getResourceArtifacts()) {
            writeResourceArtifact(artifact);
        }
        ImmutableProcessorSupport.logResolvedImmutableTypes(
                resolvedSources.getLsiClasses(),
                new Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        Context.INSTANCE.logInfo(message);
                    }
                }
        );
        ImmutableProcessorSupport.notifyEntityMetaConsumers(
                resolvedSources.getLsiClasses(),
                new Consumer<String>() {
                    @Override
                    public void accept(String message) {
                        Context.INSTANCE.logInfo(message);
                    }
                }
        );
    }

    private File findGeneratedEntitiesResourceFile() {
        return Context.INSTANCE.guessGeneratedJimmerResourceFile("entities");
    }

    private void writeFileSpec(LsiFileSpec fileSpec) {
        Context.INSTANCE.getLsiFiler().createSourceFile(fileSpec);
    }

    private void writeResourceArtifact(GeneratedResourceArtifact resourceArtifact) {
        Context.INSTANCE.getLsiFiler().createResourceFile(resourceArtifact.getPath(), resourceArtifact.getContent());
    }
}
