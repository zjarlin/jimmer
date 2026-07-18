package org.babyfish.jimmer.ksp.immutable

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.GeneratorException
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.fullName
import org.babyfish.jimmer.ksp.immutable.generator.DraftGenerator
import org.babyfish.jimmer.ksp.immutable.generator.FetcherGenerator
import org.babyfish.jimmer.ksp.immutable.generator.PropsGenerator
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass

class ImmutableProcessor(
    private val ctx: Context,
    private val excludeUserAnnotationPrefixes: List<String>
) {
    fun process(): Collection<KSClassDeclaration> {
        val modelMap = findModelMap()
        generateJimmerTypes(modelMap)
        return modelMap.values.flatten()
    }

    private fun findModelMap(): Map<KSFile, List<KSClassDeclaration>> {
        val modelMap = mutableMapOf<KSFile, MutableList<KSClassDeclaration>>()
        for (file in ctx.resolver.getNewFiles()) {
            for (classDeclaration in file.declarations.filterIsInstance<KSClassDeclaration>()) {
                if (!classDeclaration.validate()) {
                    continue
                }
                if (ctx.include(classDeclaration)) {
                    val annotation = ctx.typeAnnotationOf(classDeclaration)
                    if (classDeclaration.qualifiedName !== null && annotation != null) {
                        if (classDeclaration.classKind != ClassKind.INTERFACE) {
                            throw GeneratorException(
                                "The immutable interface '${classDeclaration.fullName}' " +
                                        "must be interface"
                            )
                        }
                        if (classDeclaration.typeParameters.isNotEmpty() &&
                            classDeclaration.annotation(MappedSuperclass::class) == null) {
                            throw GeneratorException(
                                "The immutable interface '${classDeclaration.fullName}' " +
                                        "cannot have type parameters unless it is mapped super class"
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
        for ((file, classDeclarations) in classDeclarationMultiMap) {
            DraftGenerator(ctx.environment.codeGenerator, ctx, file, classDeclarations, excludeUserAnnotationPrefixes)
                .generate()
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
                if (sqlClassDeclaration.typeParameters.isEmpty()) {
                    PropsGenerator(ctx.environment.codeGenerator, ctx, file, sqlClassDeclaration)
                        .generate()
                }
                if (sqlClassDeclaration.annotation(Entity::class) !== null || sqlClassDeclaration.annotation(Embeddable::class) !== null) {
                    FetcherGenerator(ctx.environment.codeGenerator, ctx, file, sqlClassDeclaration)
                        .generate()
                }
            }
        }
    }
}
