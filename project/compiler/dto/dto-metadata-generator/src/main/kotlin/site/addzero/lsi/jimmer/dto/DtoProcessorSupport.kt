package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.Anno.EnumValue
import org.babyfish.jimmer.dto.compiler.DtoAstException
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.LsiDtoCompiler
import site.addzero.lsi.jimmer.client.DocMetadata
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.resolver.LsiResolver
import kotlin.Function3
import java.util.function.Function

object DtoProcessorSupport {

    private const val KOTLIN_DTO = "org.babyfish.jimmer.kt.dto.KotlinDto"
    private const val IMMUTABILITY = "immutability"
    private const val IMMUTABLE = "IMMUTABLE"
    private const val MUTABLE = "MUTABLE"

    @JvmStatic
    fun collectDtoFiles(
        sourceAnchorFilePath: String?,
        dtoDirs: Collection<String>,
    ): Set<LsiDtoFile> =
        LinkedHashSet(DtoContext(sourceAnchorFilePath, dtoDirs).dtoFiles.map { it.toLsiDtoFile() })

    @JvmStatic
    fun generateFileSpecs(
        dtoFiles: Collection<LsiDtoFile>,
        defaultNullableInputModifier: LsiDtoModifier,
        resolver: LsiResolver,
        includeDtoSourceType: (LsiClass) -> Boolean,
        toImmutableType: Function<LsiClass, ImmutableType?>,
        resolveTypes: Runnable,
        draftImplDocMapOf: Function3<LsiClass, String, String, Map<String, String>>,
        fallbackMutable: Boolean,
    ): List<LsiFileSpec> {
        val docMetadata = createDocMetadata(draftImplDocMapOf)
        val dtoTypeMap = compileDtoTypes(
            dtoFiles = dtoFiles,
            defaultNullableInputModifier = defaultNullableInputModifier,
            resolver = resolver,
            includeDtoSourceType = includeDtoSourceType,
            toImmutableType = toImmutableType,
            resolveTypes = resolveTypes,
        )
        if (dtoTypeMap.isEmpty()) {
            return emptyList()
        }
        val fileSpecs = mutableListOf<LsiFileSpec>()
        for (dtoTypes in dtoTypeMap.values) {
            for (dtoType in dtoTypes) {
                val generator = DtoGenerator(
                    docMetadata = docMetadata,
                    mutable = determineMutable(dtoType, fallbackMutable),
                    dtoType = dtoType,
                )
                generator.generate()?.let(fileSpecs::add)
            }
        }
        return fileSpecs
    }

    private fun createDocMetadata(
        draftImplDocMapOf: Function3<LsiClass, String, String, Map<String, String>>,
    ): DocMetadata =
        DocMetadata { type, annotationQualifiedName, valueAttributeName ->
            draftImplDocMapOf.invoke(type, annotationQualifiedName, valueAttributeName)
        }

    private fun determineMutable(
        dtoType: LsiDtoType,
        fallbackMutable: Boolean,
    ): Boolean =
        dtoType.annotations
            .firstNotNullOfOrNull { annotation ->
                if (annotation.qualifiedName != KOTLIN_DTO) {
                    return@firstNotNullOfOrNull null
                }
                val value = annotation.valueMap[IMMUTABILITY] as? EnumValue ?: return@firstNotNullOfOrNull null
                when (value.constant) {
                    IMMUTABLE -> false
                    MUTABLE -> true
                    else -> null
                }
            }
            ?: fallbackMutable

    private fun compileDtoTypes(
        dtoFiles: Collection<LsiDtoFile>,
        defaultNullableInputModifier: LsiDtoModifier,
        resolver: LsiResolver,
        includeDtoSourceType: (LsiClass) -> Boolean,
        toImmutableType: Function<LsiClass, ImmutableType?>,
        resolveTypes: Runnable,
    ): Map<ImmutableType, List<LsiDtoType>> {
        if (dtoFiles.isEmpty()) {
            return emptyMap()
        }
        val dtoTypeMap = linkedMapOf<ImmutableType, MutableList<LsiDtoType>>()
        val immutableTypeMap = linkedMapOf<LsiDtoCompiler, ImmutableType>()
        for (dtoFile in dtoFiles) {
            val compiler = createCompiler(
                dtoFile = dtoFile,
                defaultNullableInputModifier = defaultNullableInputModifier,
                resolver = resolver,
            )
            val sourceType = resolveDtoSourceTypeOrNull(
                dtoFile = dtoFile.rawDtoFile,
                sourceTypeName = compiler.sourceTypeName,
                resolver = resolver,
                include = includeDtoSourceType,
                fail = { message -> throw DtoException(message) },
            ) ?: continue
            val immutableType = toImmutableType.apply(sourceType) ?: continue
            immutableTypeMap[compiler] = immutableType
        }
        if (immutableTypeMap.isEmpty()) {
            return emptyMap()
        }
        resolveTypes.run()
        for ((compiler, immutableType) in immutableTypeMap) {
            dtoTypeMap.computeIfAbsent(immutableType) {
                mutableListOf()
            } += compiler.compileToLsiDtoTypes(immutableType)
        }
        return dtoTypeMap
    }

    private fun createCompiler(
        dtoFile: LsiDtoFile,
        defaultNullableInputModifier: LsiDtoModifier,
        resolver: LsiResolver,
    ): LsiDtoCompiler =
        try {
            LsiDtoCompiler(
                dtoFile = dtoFile,
                defaultNullableInputModifier = defaultNullableInputModifier,
                genericTypeCountProvider = resolver::genericTypeCount,
            )
        } catch (ex: DtoAstException) {
            throw DtoException(
                "Failed to parse \"" +
                    dtoFile.absolutePath +
                    "\": " +
                    ex.message,
                ex,
            )
        } catch (ex: Throwable) {
            throw DtoException(
                "Failed to read \"" +
                    dtoFile.absolutePath +
                    "\": " +
                    ex.message,
                ex,
            )
        }
}
