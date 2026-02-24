package org.babyfish.jimmer.ksp.dto

import com.google.devtools.ksp.getClassDeclarationByName
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.Anno.EnumValue
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoType
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.KspDtoCompiler
import org.babyfish.jimmer.ksp.annotation
import org.babyfish.jimmer.ksp.client.DocMetadata
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableProp
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.ksp.include
import org.babyfish.jimmer.processor.spi.ProcessorSpi
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import site.addzero.context.Settings

class DtoProcessor : ProcessorSpi<Context, Boolean> {
    override var ctx = Context
    override val phase: Int get() = 1
    override val order: Int get() = 2

    private val mutable: Boolean get() = Settings.jimmerDtoMutable
    private val dtoDirs: Collection<String>
        get() = if (ctx.resolver.getAllFiles().toList().isNotEmpty() && isTest(ctx.resolver.getAllFiles().first().filePath)) {
            Settings.jimmerDtoTestDirs
        } else {
            Settings.jimmerDtoDirs
        }
    private val defaultNullableInputModifier: DtoModifier get() = Settings.jimmerDtoDefaultNullableInputModifier

    override fun process(): Boolean {
        val dtoTypeMap = findDtoTypeMap()
        generateDtoTypes(dtoTypeMap)
        return dtoTypeMap.isNotEmpty()
    }

    private fun findDtoTypeMap(): Map<ImmutableType, MutableList<DtoType<ImmutableType, ImmutableProp>>> {
        val dtoTypeMap = mutableMapOf<ImmutableType, MutableList<DtoType<ImmutableType, ImmutableProp>>>()
        val dtoCtx = DtoContext(Context.resolver.getAllFiles().firstOrNull(), dtoDirs)
        val immutableTypeMap = mutableMapOf<KspDtoCompiler, ImmutableType>()
        for (dtoFile in dtoCtx.dtoFiles) {
            val compiler = try {
                KspDtoCompiler(dtoFile, Context.resolver, defaultNullableInputModifier)
            } catch (ex: DtoAstException) {
                throw DtoException(
                    "Failed to parse \"" +
                        dtoFile.absolutePath +
                        "\": " +
                        ex.message,
                    ex
                )
            } catch (ex: Throwable) {
                throw DtoException(
                    "Failed to read \"" +
                        dtoFile.absolutePath +
                        "\": " +
                        ex.message,
                    ex
                )
            }
            val classDeclaration = Context.resolver.getClassDeclarationByName(compiler.sourceTypeName)
            if (classDeclaration === null) {
                throw DtoException(
                    "Failed to parse \"" +
                        dtoFile.absolutePath +
                        "\": No immutable type \"" +
                        compiler.sourceTypeName +
                        "\""
                )
            }
            if (!classDeclaration.include()) {
                continue
            }
            if (classDeclaration.annotation(Entity::class) == null &&
                classDeclaration.annotation(Embeddable::class) == null &&
                classDeclaration.annotation(Immutable::class) == null) {
                throw DtoException(
                    "Failed to parse \"" +
                        dtoFile.absolutePath +
                        "\": the \"" +
                        compiler.sourceTypeName +
                        "\" is not decorated by \"@" +
                        Entity::class.qualifiedName +
                        "\", \"" +
                        Embeddable::class.qualifiedName +
                        "\" or \"" +
                        Immutable::class.qualifiedName +
                        "\""
                )
            }
            immutableTypeMap[compiler] = Context.typeOf(classDeclaration)
        }
        Context.resolve()
        for ((compiler, immutableType) in immutableTypeMap) {
            dtoTypeMap.computeIfAbsent(immutableType) {
                mutableListOf()
            } += compiler.compile(immutableType)
        }
        return dtoTypeMap
    }

    private fun generateDtoTypes(
        dtoTypeMap: Map<ImmutableType, List<DtoType<ImmutableType, ImmutableProp>>>
    ) {
        val allFiles = Context.resolver.getAllFiles().toList()
        val docMetadata = DocMetadata()
        for (dtoTypes in dtoTypeMap.values) {
            for (dtoType in dtoTypes) {
                val mutable = dtoType.annotations.firstOrNull {
                    it.qualifiedName == "org.babyfish.jimmer.kt.dto.KotlinDto"
                }?.let {
                    val value = it.valueMap["immutability"] as EnumValue
                    when (value.constant) {
                        "IMMUTABLE" -> false
                        "MUTABLE" -> true
                        else -> null
                    }
                } ?: mutable
                DtoGenerator( docMetadata, mutable, dtoType).generate(allFiles)
            }
        }
    }

    companion object {
        private fun isTest(path: String): Boolean {
            val testIndex = path.indexOf("/src/test/")
            if (testIndex == -1) return false
            val mainIndex = path.indexOf("/src/main/")
            return mainIndex == -1 || testIndex < mainIndex
        }
    }
}
