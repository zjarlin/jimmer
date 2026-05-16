package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoSharedSurfaceAuditTest {

    @Test
    fun `dto processor support exposes lsi dto surface only`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoProcessorSupport.kt"
        )

        assertTrue(source.contains("fun collectDtoFiles("))
        assertTrue(source.contains("): Set<LsiDtoFile> ="))
        assertTrue(source.contains("fun generateFileSpecs("))
        assertTrue(source.contains("): List<LsiFileSpec> {"))
        assertTrue(source.contains("defaultNullableInputModifier: LsiDtoModifier"))
        assertTrue(source.contains("resolver: LsiResolver"))
        assertTrue(source.contains("includeDtoSourceType: (LsiClass) -> Boolean"))
        assertTrue(source.contains("draftImplDocMapOf: Function3<LsiClass, String, String, Map<String, String>>"))
        assertFalse(source.contains("Set<DtoFile>"))
        assertFalse(source.contains("Collection<DtoFile>"))
        assertFalse(source.contains("List<GeneratedSourceFileArtifact>"))
        assertFalse(source.contains("genericTypeCountProvider: Function<String, Int?>"))
        assertFalse(source.contains("resolveDtoSourceType: BiFunction<LsiDtoFile, String, LsiClass?>"))
        assertFalse(source.contains("BiFunction<DtoFile, String, LsiClass?>"))
        assertFalse(source.contains("defaultNullableInputModifier: DtoModifier"))
        assertFalse(source.contains("docMetadata: DocMetadata"))
    }

    @Test
    fun `dto compiler bridge constructor consumes lsi dto surface`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/codegen/LsiDtoCompiler.kt"
        )

        assertTrue(source.contains("dtoFile: LsiDtoFile"))
        assertTrue(source.contains("private val defaultNullableInputModifier: LsiDtoModifier"))
        assertTrue(source.contains("fun compileToLsiDtoTypes(immutableType: ImmutableType): List<LsiDtoType> ="))
        assertFalse(source.contains("dtoFile: DtoFile"))
    }

    @Test
    fun `dto generator top level path accepts lsi dto type wrapper`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt"
        )

        assertTrue(source.contains("internal val dtoType: LsiDtoType"))
        assertTrue(source.contains("dtoType: LsiDtoType,"))
        assertTrue(source.contains("analyzeDtoInterfaceMembers(ctx.lsiResolver, dtoType)"))
        assertFalse(source.contains("internal val dtoType: DtoType<ImmutableType, ImmutableProp>"))
        assertFalse(source.contains("analyzeDtoInterfaceMembers(ctx.lsiResolver, dtoType.rawDtoType)"))
    }

    @Test
    fun `dto config wrapper keeps predicate path semantics behind lsi views`() {
        val source = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/LsiDtoConfigView.kt"
        )

        assertTrue(source.contains("internal class LsiDtoPropConfigView"))
        assertTrue(source.contains("internal sealed interface LsiDtoPredicateView"))
        assertTrue(source.contains("internal class LsiDtoOrderItemView"))
        assertTrue(source.contains("internal class LsiDtoPathNodeView"))
        assertTrue(source.contains("internal val DtoProp<ImmutableType, ImmutableProp>.lsiConfigView"))
    }

    @Test
    fun `dto prop wrappers expose shared prop view aggregation`() {
        val typeSource = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/LsiDtoType.kt"
        )
        val propSource = DtoTestSupport.readSource(
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/LsiDtoPropView.kt"
        )

        assertTrue(typeSource.contains("val propViews: List<LsiDtoAbstractPropView>"))
        assertTrue(typeSource.contains("val hiddenFlatPropViews: List<LsiDtoPropView>"))
        assertTrue(typeSource.contains("internal fun analyzeDtoInterfaceMembers("))
        assertFalse(typeSource.contains("internal val rawDtoType"))
        assertTrue(propSource.contains("val declaredAlias: String?"))
        assertTrue(propSource.contains("val doc: String?"))
        assertTrue(propSource.contains("val baseProp: ImmutableProp"))
        assertTrue(propSource.contains("val tailBaseProp: ImmutableProp"))
        assertTrue(propSource.contains("val targetType: LsiDtoType?"))
        assertTrue(propSource.contains("val configView: LsiDtoPropConfigView?"))
        assertTrue(propSource.contains("val tailFieldAnnotations: List<LsiAnnotation>"))
        assertTrue(propSource.contains("internal fun AbstractProp.toLsiDtoAbstractPropView(): LsiDtoAbstractPropView"))
        assertFalse(propSource.contains("internal val rawDtoProp"))
        assertFalse(propSource.contains("internal val rawUserProp"))
        assertFalse(propSource.contains("val rawAbstractProp"))
    }
}
