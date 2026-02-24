package org.babyfish.jimmer.ksp.error

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.error.ErrorFamily
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.Context.serverGenerated
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.include

class ErrorProcessor(
    private val checkedException: Boolean
) {
    private val ctx = Context


    fun process(): Boolean {
        if (serverGenerated) {
            return false
        }
        val errorTypes = findErrorTypes()
        generateErrorTypes(errorTypes)
        return errorTypes.isNotEmpty()
    }

    private fun findErrorTypes() = ctx.resolver.getNewFiles().flatMap { file ->
        file.declarations.filterIsInstance<KSClassDeclaration>().filter {
            it.classKind == ClassKind.ENUM_CLASS && it.annotation(ErrorFamily::class) != null && it.include()
        }
    }.toList()

    private fun generateErrorTypes(declarations: Collection<KSClassDeclaration>) {
        val allFiles = ctx.resolver.getNewFiles().toList()
        for (declaration in declarations) {
            ErrorGenerator(ctx, declaration, checkedException, ctx.environment.codeGenerator).generate(allFiles)
        }
    }
}
