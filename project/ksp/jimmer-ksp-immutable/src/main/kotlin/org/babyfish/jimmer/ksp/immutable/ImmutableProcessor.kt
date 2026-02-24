package org.babyfish.jimmer.ksp.immutable

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.ksp.*
import org.babyfish.jimmer.ksp.immutable.generator.DraftGenerator
import org.babyfish.jimmer.ksp.immutable.generator.FetcherGenerator
import org.babyfish.jimmer.ksp.immutable.generator.JimmerModuleGenerator
import org.babyfish.jimmer.ksp.immutable.generator.PropsGenerator
import org.babyfish.jimmer.processor.spi.EntityMetaConsumerSpi
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi_impl.impl.ksp.toLsiClass
import site.addzero.context.ImmutableSettings
import site.addzero.context.Settings
import java.util.ServiceLoader
import java.util.regex.Pattern
import kotlin.math.min

class ImmutableProcessor: ProcessorSpi<Context,Collection<KSClassDeclaration>> {
    override var ctx = Context
    override val phase: Int get() = 1
    override val order: Int get() = 0

    override fun process(): Collection<KSClassDeclaration> {
        ImmutableSettings.fromOptions(ctx.environment.options)
        val modelMap = findModelMap()
        generateJimmerTypes(modelMap)
        val declarations = modelMap.values.flatten()
        val lsiClasses: List<LsiClass> = declarations.map { it.toLsiClass(ctx.resolver) }
        notifyEntityMetaConsumers(lsiClasses)
        return declarations
    }

    private fun notifyEntityMetaConsumers(entities: List<LsiClass>) {
        val logger = ctx.environment.logger
        val consumers = ServiceLoader.load(EntityMetaConsumerSpi::class.java, ImmutableProcessor::class.java.classLoader).toList()
        logger.info("[jimmer] EntityMetaConsumerSpi: ${consumers.size} consumer(s) registered, ${entities.size} entity type(s) found")
        consumers.forEach { consumer ->
            logger.info("[jimmer] EntityMetaConsumerSpi -> invoking: ${consumer::class.qualifiedName}")
            consumer.consume(entities)
        }
    }

    private fun findModelMap(): Map<KSFile, List<KSClassDeclaration>> {
        val modelMap = mutableMapOf<KSFile, MutableList<KSClassDeclaration>>()
        for (file in ctx.resolver.getNewFiles()) {
            for (classDeclaration in file.declarations.filterIsInstance<KSClassDeclaration>()) {
                if (classDeclaration.include(Settings.jimmerSourceIncludes, Settings.jimmerSourceExcludes)) {
                    val annotation = ctx.typeAnnotationOf(classDeclaration)
                    if (classDeclaration.qualifiedName !== null && annotation != null) {
                        if (classDeclaration.classKind != ClassKind.INTERFACE) {
                            throw GeneratorException(
                                "The immutable interface '${classDeclaration.fullName}' " +
                                        "must be interface"
                            )
                        }
                        if (classDeclaration.typeParameters.isNotEmpty()) {
                            throw GeneratorException(
                                "The immutable interface '${classDeclaration.fullName}' " +
                                        "cannot have type parameters"
                            )
                        }
                        if (classDeclaration.isPrivate() || classDeclaration.isProtected()) {
                            throw GeneratorException(
                                "The immutable interface '${classDeclaration.fullName}' " +
                                        "cannot be private or protected'"
                            )
                        }
                        modelMap.computeIfAbsent(file) { mutableListOf() } += classDeclaration
                    }
                }
            }
        }
        for (declarations in modelMap.values) {
            for (declaration in declarations) {
                ctx.typeOf(declaration)
            }
        }
        ctx.resolve()
        return modelMap
    }

    private fun generateJimmerTypes(
        classDeclarationMultiMap: Map<KSFile, List<KSClassDeclaration>>
    ) {
        val allFiles = ctx.resolver.getAllFiles().toList()
        for ((file, classDeclarations) in classDeclarationMultiMap) {
            DraftGenerator(
                ctx.environment.codeGenerator,
                ctx,
                file,
                classDeclarations,
                ImmutableSettings.jimmerExcludedUserAnnotationPrefixes
            )
                .generate(allFiles)
            if (classDeclarations.size > 1) {
                throw GeneratorException(
                    "The $file declares several types decorated by " +
                            "@${Immutable::class.qualifiedName}, " +
                            "@${Entity::class.qualifiedName}, " +
                            "@${MappedSuperclass::class.qualifiedName} " +
                            "or ${Embeddable::class.qualifiedName}: " +
                            classDeclarations.joinToString { it.fullName }
                )
            }
            val sqlClassDeclarations = classDeclarations.filter {
                it.annotation(Entity::class) !== null ||
                        it.annotation(MappedSuperclass::class) !== null ||
                        it.annotation(Embeddable::class) != null
            }
            if (sqlClassDeclarations.isNotEmpty()) {
                val sqlClassDeclaration = sqlClassDeclarations[0]
                PropsGenerator(ctx.environment.codeGenerator, ctx, file, sqlClassDeclaration)
                    .generate(allFiles)
                if (sqlClassDeclaration.annotation(Entity::class) !== null || sqlClassDeclaration.annotation(Embeddable::class) !== null) {
                    FetcherGenerator(ctx.environment.codeGenerator, ctx, file, sqlClassDeclaration)
                        .generate(allFiles)
                }
            }
        }

        val packageCollector = PackageCollector()
        for (file in ctx.resolver.getNewFiles()) {
            for (classDeclaration in file.declarations.filterIsInstance<KSClassDeclaration>()) {
                if (classDeclaration.include(
                        Settings.jimmerSourceIncludes,
                        Settings.jimmerSourceExcludes
                    ) && classDeclaration.annotation(Entity::class) !== null
                ) {
                    packageCollector.accept(classDeclaration)
                }
            }
        }
        if (!Settings.jimmerBuddyIgnoreResourceGeneration) {
            JimmerModuleGenerator(
                ctx.environment.codeGenerator,
                packageCollector.toString(),
                packageCollector.declarations,
                Settings.jimmerImmutableIsModuleRequired
            ).generate(allFiles)
        }
    }

    private class PackageCollector {

        private var paths: MutableList<String>? = null

        private var str: String? = null

        private val _declarations: MutableList<KSClassDeclaration> = ArrayList()

        fun accept(declaration: KSClassDeclaration) {
            _declarations.add(declaration)
            if (paths != null && paths!!.isEmpty()) {
                return
            }
            str = null
            var newPaths = DOT_PATTERN.split(declaration.packageName.asString()).toMutableList()
            if (paths == null) {
                paths = newPaths
            } else {
                val len = min(paths!!.size, newPaths.size)
                var index = 0
                while (index < len) {
                    if (paths!![index] != newPaths[index]) {
                        break
                    }
                    index++
                }
                if (index < paths!!.size) {
                    paths!!.subList(index, paths!!.size).clear()
                }
            }
        }

        val declarations: List<KSClassDeclaration>
            get() = _declarations

        override fun toString(): String {
            var s = str
            if (s == null) {
                val ps = paths
                s = if (ps.isNullOrEmpty()) "" else java.lang.String.join(".", ps)
                str = s
            }
            return s!!
        }

        companion object {
            private val DOT_PATTERN = Pattern.compile("\\.")
        }
    }
}
