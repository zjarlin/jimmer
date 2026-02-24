package org.babyfish.jimmer.ksp.tuple

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.MetaException
import org.babyfish.jimmer.ksp.annotations
import org.babyfish.jimmer.ksp.fullName
import org.babyfish.jimmer.ksp.util.fastResolve
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import org.babyfish.jimmer.sql.TypedTuple

class TypedTupleProcessor : ProcessorSpi<Context, List<KSClassDeclaration>> {
    override var ctx = Context
    override val phase: Int get() = 2
    override val order: Int get() = 0

    override fun process(): List<KSClassDeclaration> {
        val processedDeclarations = mutableListOf<KSClassDeclaration>()
        for (file in ctx.resolver.getAllFiles()) {
            for (declaration in file.declarations) {
                if (declaration.annotations { it.fullName == TypedTuple::class.qualifiedName }.isNotEmpty()) {
                    generate(declaration as KSClassDeclaration)
                    processedDeclarations += declaration
                }
            }
        }
        if (ctx.delayedTupleTypeNames != null) {
            for (delayedClientTypeName in ctx.delayedTupleTypeNames) {
                val declaration = ctx.resolver.getClassDeclarationByName(delayedClientTypeName)!!
                generate(declaration)
                processedDeclarations += declaration
            }
        }
        return processedDeclarations
    }

    private fun generate(declaration: KSClassDeclaration) {
        if (!declaration.modifiers.contains(Modifier.DATA)) {
            throw MetaException(
                declaration,
                "The type decorated by @${TypedTuple::class.qualifiedName} must be data class"
            )
        }
        if (declaration.parentDeclaration is KSClassDeclaration) {
            throw MetaException(
                declaration,
                "The type decorated by @${TypedTuple::class.qualifiedName} must be top-level class"
            )
        }
        if (declaration.superTypes.any {
                val superType = it.fastResolve().declaration as KSClassDeclaration
                superType.classKind == ClassKind.CLASS &&
                        superType.fullName != "kotlin.Any"
            }) {
            throw MetaException(
                declaration,
                "The type decorated by @${TypedTuple::class.qualifiedName} cannot inherit other class"
            )
        }
        TypedTupleGenerator(declaration).generate()
    }
}
