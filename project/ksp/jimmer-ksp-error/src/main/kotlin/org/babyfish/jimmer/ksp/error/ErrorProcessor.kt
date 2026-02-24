package org.babyfish.jimmer.ksp.error

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.babyfish.jimmer.error.ErrorFamily
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.include
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import site.addzero.context.Settings

class ErrorProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    override val phase: Int get() = 1
    override val order: Int get() = 1

    override fun process(): Boolean {
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
            ErrorGenerator(declaration, Settings.jimmerClientCheckedException, ctx.environment.codeGenerator).generate(
                allFiles
            )
        }
    }
}
