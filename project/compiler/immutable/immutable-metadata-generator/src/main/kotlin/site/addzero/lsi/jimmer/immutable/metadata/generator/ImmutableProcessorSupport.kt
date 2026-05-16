package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceAccumulator
import site.addzero.lsi.jimmer.immutable.metadata.extractor.ImmutableCollectedSourceResolution
import site.addzero.lsi.jimmer.immutable.metadata.extractor.toCollectedImmutableSourceMetadata
import site.addzero.lsi.jimmer.processor.spi.EntityMetaConsumerSpi
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.resolver.LsiResolver
import java.io.File
import java.util.ServiceLoader
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

object ImmutableProcessorSupport {

    private val immutableTopLevelAnnotationQualifiedNames = listOf(
        Immutable::class.java.name,
        Entity::class.java.name,
        Embeddable::class.java.name,
        MappedSuperclass::class.java.name,
    )

    private fun validateImmutableTopLevelAnnotatedTypes(
        resolver: LsiResolver,
    ) {
        for (annotationQualifiedName in immutableTopLevelAnnotationQualifiedNames) {
            validateTopLevelAnnotatedTypes(
                resolver = resolver,
                annotationQualifiedName = annotationQualifiedName,
            )
        }
    }

    private fun validateTopLevelAnnotatedTypes(
        resolver: LsiResolver,
        annotationQualifiedName: String,
    ) {
        for (lsiClass in resolver.findClassesAnnotatedWith(annotationQualifiedName)) {
            if (!lsiClass.isTopLevel) {
                throw MetaException(
                    lsiClass,
                    "The type decorated by \"@$annotationQualifiedName\" must be top level type",
                    null,
                )
            }
        }
    }

    @JvmStatic
    fun collectRoundSources(
        accumulator: ImmutableCollectedSourceAccumulator,
        resolver: LsiResolver,
        include: Predicate<LsiClass>,
    ) {
        validateImmutableTopLevelAnnotatedTypes(resolver)
        accumulator.collect(
            resolver.newClasses().toCollectedImmutableSourceMetadata(
                include = { lsiClass -> include.test(lsiClass) },
            )
        )
    }

    @JvmStatic
    fun resolveCollectedSources(
        accumulator: ImmutableCollectedSourceAccumulator,
        resolver: LsiResolver,
        toImmutableType: Function<LsiClass, ImmutableType>,
    ): ImmutableCollectedSourceResolution =
        accumulator.resolve(
            findClassByQualifiedName = resolver::findClassByQualifiedName,
            toImmutableType = toImmutableType::apply,
        )

    @JvmStatic
    fun hasImmutableTypes(
        resolvedSources: ImmutableCollectedSourceResolution,
    ): Boolean =
        resolvedSources.sources.any { source ->
            source.immutableTypes.isNotEmpty()
        }

    private fun generateOutput(
        resolvedSources: ImmutableCollectedSourceResolution,
        excludedUserTypePrefixes: List<String>,
        jacksonTypes: JacksonTypes,
        existingEntitiesResourceFile: File?,
        isResourceGenerationIgnored: Boolean,
        isModuleRequired: Boolean,
        generationMode: ImmutableGenerationMode,
        currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
    ): ImmutableGeneratedOutput =
        resolvedSources.toGeneratedOutput(
            excludedUserTypePrefixes = excludedUserTypePrefixes,
            jacksonTypes = jacksonTypes,
            existingEntitiesResourceFile = existingEntitiesResourceFile,
            isResourceGenerationIgnored = isResourceGenerationIgnored,
            isModuleRequired = isModuleRequired,
            generationMode = generationMode,
            currentVersionValue = currentVersionValue,
        )

    @JvmStatic
    fun generateKspOutput(
        resolvedSources: ImmutableCollectedSourceResolution,
        excludedUserTypePrefixes: List<String>,
        jacksonTypes: JacksonTypes,
        existingEntitiesResourceFile: File?,
        isResourceGenerationIgnored: Boolean,
        isModuleRequired: Boolean,
        currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
    ): ImmutableGeneratedOutput =
        generateOutput(
            resolvedSources = resolvedSources,
            excludedUserTypePrefixes = excludedUserTypePrefixes,
            jacksonTypes = jacksonTypes,
            existingEntitiesResourceFile = existingEntitiesResourceFile,
            isResourceGenerationIgnored = isResourceGenerationIgnored,
            isModuleRequired = isModuleRequired,
            generationMode = ImmutableGenerationMode.KOTLIN_FULL,
            currentVersionValue = currentVersionValue,
        )

    private fun generateJavaSharedOutput(
        resolvedSources: ImmutableCollectedSourceResolution,
        excludedUserTypePrefixes: List<String>,
        jacksonTypes: JacksonTypes,
        existingEntitiesResourceFile: File?,
        isResourceGenerationIgnored: Boolean,
        isModuleRequired: Boolean,
        currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
    ): ImmutableGeneratedOutput =
        generateOutput(
            resolvedSources = resolvedSources,
            excludedUserTypePrefixes = excludedUserTypePrefixes,
            jacksonTypes = jacksonTypes,
            existingEntitiesResourceFile = existingEntitiesResourceFile,
            isResourceGenerationIgnored = isResourceGenerationIgnored,
            isModuleRequired = isModuleRequired,
            generationMode = ImmutableGenerationMode.JAVA_SHARED,
            currentVersionValue = currentVersionValue,
        )

    @JvmStatic
    fun generateAptOutput(
        resolvedSources: ImmutableCollectedSourceResolution,
        excludedUserTypePrefixes: List<String>,
        jacksonTypes: JacksonTypes,
        existingEntitiesResourceFile: File?,
        isResourceGenerationIgnored: Boolean,
        isModuleRequired: Boolean,
        currentVersionValue: String = org.babyfish.jimmer.currentVersion(),
    ): ImmutableGeneratedOutput =
        generateJavaSharedOutput(
            resolvedSources = resolvedSources,
            excludedUserTypePrefixes = excludedUserTypePrefixes,
            jacksonTypes = jacksonTypes,
            existingEntitiesResourceFile = existingEntitiesResourceFile,
            isResourceGenerationIgnored = isResourceGenerationIgnored,
            isModuleRequired = isModuleRequired,
            currentVersionValue = currentVersionValue,
        )

    @JvmStatic
    fun logResolvedImmutableTypes(
        entities: List<LsiClass>,
        infoLogger: Consumer<String>,
    ) {
        for (entity in entities) {
            val qualifiedName = entity.qualifiedName
            if (!qualifiedName.isNullOrEmpty()) {
                infoLogger.accept("[jimmer] Immutable: $qualifiedName")
            }
        }
    }

    @JvmStatic
    fun notifyEntityMetaConsumers(
        entities: List<LsiClass>,
        infoLogger: Consumer<String>,
    ) {
        val consumers = ServiceLoader.load(
            EntityMetaConsumerSpi::class.java,
            ImmutableProcessorSupport::class.java.classLoader,
        ).toList()
        infoLogger.accept("[jimmer] EntityMetaConsumerSpi: ${consumers.size} consumer(s) registered, ${entities.size} entity type(s) found")
        consumers.forEach { consumer ->
            infoLogger.accept("[jimmer] EntityMetaConsumerSpi -> invoking: ${consumer::class.qualifiedName}")
            consumer.consume(entities)
        }
    }
}
