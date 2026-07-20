package org.babyfish.jimmer.compiler.dto

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.immutable.JimmerAssociationKind
import org.babyfish.jimmer.compiler.immutable.JimmerFormulaKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrimaryMapping
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableProp
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerDtoAnnotationContractTest {
    @Test
    fun `freezes declaration kind placements and kotlin value vararg`() {
        val fixture = fixture()
        val contract = fixture.freeze()
        val kotlinDeclaration = contract.declarationsByTypeId.getValue(KOTLIN_TAG)
        val javaDeclaration = contract.declarationsByTypeId.getValue(JAVA_TAG)

        assertEquals(JimmerDtoAnnotationDeclarationKind.KOTLIN, kotlinDeclaration.kind)
        assertTrue(kotlinDeclaration.targetDeclared)
        assertEquals(
            listOf(
                JimmerDtoAnnotationPlacement.FIELD,
                JimmerDtoAnnotationPlacement.GETTER,
                JimmerDtoAnnotationPlacement.PROPERTY,
            ),
            kotlinDeclaration.allowedPlacements,
        )
        assertEquals(listOf("value"), kotlinDeclaration.argumentNames)
        assertTrue(kotlinDeclaration.kotlinValueVararg)

        assertEquals(JimmerDtoAnnotationDeclarationKind.JAVA, javaDeclaration.kind)
        assertTrue(javaDeclaration.targetDeclared)
        assertEquals(
            listOf(
                JimmerDtoAnnotationPlacement.TYPE,
                JimmerDtoAnnotationPlacement.FIELD,
                JimmerDtoAnnotationPlacement.GETTER,
            ),
            javaDeclaration.allowedPlacements,
        )
        assertEquals(listOf("value"), javaDeclaration.argumentNames)
        assertFalse(javaDeclaration.kotlinValueVararg)
    }

    @Test
    fun `keeps dto bean validation nullity annotations while filtering immutable nullity`() {
        val fixture = fixture()
        val baseProp = fixture.schema.types.single().props.single()
        val dtoProp = fixture.graph.props.single() as JimmerDtoBaseProp
        val javaxNull = LsiSymbolId.type("javax.validation.constraints.Null")
        val jakartaNotNull = LsiSymbolId.type("jakarta.validation.constraints.NotNull")
        val schema = immutableSchema(
            typeAnnotations = fixture.schema.types.single().annotations,
            propAnnotations = baseProp.annotations + listOf(
                lsiAnnotation("demo.Nullable"),
                lsiAnnotation("javax.validation.constraints.Null"),
            ),
        )
        val graph = JimmerDtoRenderGraph(
            source = fixture.graph.source,
            rootTypeIds = fixture.graph.rootTypeIds,
            types = fixture.graph.types,
            props = listOf(
                dtoProp.copy(
                    annotations = dtoProp.annotations + listOf(
                        dtoAnnotation("javax.validation.constraints.Null", null),
                        dtoAnnotation("jakarta.validation.constraints.NotNull", null),
                    ),
                )
            ),
        )
        val declarations = javaAnnotationDeclaration(
            "javax.validation.constraints.Null",
            listOf("FIELD", "METHOD"),
            emptyList(),
        ) + javaAnnotationDeclaration(
            "jakarta.validation.constraints.NotNull",
            listOf("FIELD", "METHOD"),
            emptyList(),
        )
        val contract = JimmerDtoAnnotationContractFreezer(
            LsiWorkspace(declarations = fixture.workspace.declarations + declarations),
            schema,
        ).freeze(graph)
        val validationApplications = contract.propPlansByPropId
            .getValue(NAME_DTO_PROP)
            .propertyApplications
            .filter { application -> application.annotation.typeId in setOf(javaxNull, jakartaNotNull) }

        assertEquals(setOf(javaxNull, jakartaNotNull), validationApplications.map { it.annotation.typeId }.toSet())
        assertTrue(validationApplications.all { application -> application.origin == JimmerDtoAnnotationOrigin.DTO })
        assertTrue(contract.declarationsByTypeId.keys.none { typeId -> typeId.value == "demo.Nullable" })
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
    }

    @Test
    fun `uses no target annotation on dto types and skips dto properties`() {
        val fixture = fixture()
        val annotationTypeId = LsiSymbolId.type("demo.NoTarget")
        val dtoType = fixture.graph.types.single()
        val dtoProp = fixture.graph.props.single() as JimmerDtoBaseProp
        val graph = JimmerDtoRenderGraph(
            source = fixture.graph.source,
            rootTypeIds = fixture.graph.rootTypeIds,
            types = listOf(
                dtoType.copy(annotations = dtoType.annotations + dtoAnnotation("demo.NoTarget", null))
            ),
            props = listOf(
                dtoProp.copy(annotations = dtoProp.annotations + dtoAnnotation("demo.NoTarget", null))
            ),
        )
        val declaration = javaAnnotationDeclaration(
            qualifiedName = "demo.NoTarget",
            targetNames = null,
            argumentNames = emptyList(),
        )
        val contract = JimmerDtoAnnotationContractFreezer(
            LsiWorkspace(declarations = fixture.workspace.declarations + declaration),
            fixture.schema,
        ).freeze(graph)
        val frozenDeclaration = contract.declarationsByTypeId.getValue(annotationTypeId)
        val typeApplications = contract.typePlansByTypeId.getValue(ROOT_DTO_TYPE).applications
        val propApplications = contract.propPlansByPropId.getValue(NAME_DTO_PROP).propertyApplications

        assertFalse(frozenDeclaration.targetDeclared)
        assertTrue(frozenDeclaration.allowedPlacements.isEmpty())
        assertEquals(
            listOf(JimmerDtoAnnotationPlacement.TYPE),
            typeApplications.single { application -> application.annotation.typeId == annotationTypeId }.placements,
        )
        assertTrue(propApplications.none { application -> application.annotation.typeId == annotationTypeId })
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })

        val targetDeclaredContract = contract.copy(
            declarations = contract.declarations.map { annotationDeclaration ->
                if (annotationDeclaration.typeId == annotationTypeId) {
                    annotationDeclaration.copy(targetDeclared = true)
                } else {
                    annotationDeclaration
                }
            }
        )
        assertNotEquals(contract.normalizedSnapshot(), targetDeclaredContract.normalizedSnapshot())
        assertNotEquals(contract.fingerprint(), targetDeclaredContract.fingerprint())
    }

    @Test
    fun `uses exact fqn override and excludes mapping nullity and kotlin dto annotations`() {
        val contract = fixture().freeze()
        val typeApplications = contract.typePlansByTypeId
            .getValue(ROOT_DTO_TYPE)
            .applications
        val propApplications = contract.propPlansByPropId
            .getValue(NAME_DTO_PROP)
            .propertyApplications
        val typeApplicationsByName = typeApplications.associateBy { application ->
            application.annotation.typeId.requireTypeQualifiedName()
        }
        val propApplicationsByName = propApplications.groupBy { application ->
            application.annotation.typeId.requireTypeQualifiedName()
        }

        assertEquals(
            setOf(
                "demo.BaseOnly",
                "demo.Shared",
                "org.babyfish.jimmer.client.ApiIgnore",
                "other.demo.Shared",
            ),
            typeApplicationsByName.keys,
        )
        assertEquals(
            JimmerDtoAnnotationOrigin.DTO,
            typeApplicationsByName.getValue("demo.Shared").origin,
        )
        assertEquals(
            JimmerDtoAnnotationOrigin.DTO,
            typeApplicationsByName.getValue("other.demo.Shared").origin,
        )
        assertEquals(
            JimmerDtoAnnotationOrigin.IMMUTABLE,
            typeApplicationsByName.getValue("demo.BaseOnly").origin,
        )
        assertEquals(
            listOf("value"),
            typeApplicationsByName.getValue("demo.BaseOnly").annotation.arguments.map { argument -> argument.name },
        )
        assertEquals(
            setOf("demo.JavaTag", "demo.KotlinTag"),
            propApplicationsByName.keys,
        )
        assertEquals(1, propApplicationsByName.getValue("demo.JavaTag").size)
        assertEquals(
            JimmerDtoAnnotationOrigin.DTO,
            propApplicationsByName.getValue("demo.JavaTag").single().origin,
        )
        assertEquals(
            listOf(JimmerDtoAnnotationPlacement.FIELD, JimmerDtoAnnotationPlacement.GETTER),
            propApplicationsByName.getValue("demo.JavaTag").single().placements,
        )
        assertEquals(
            listOf(
                JimmerDtoAnnotationPlacement.FIELD,
                JimmerDtoAnnotationPlacement.GETTER,
                JimmerDtoAnnotationPlacement.PROPERTY,
            ),
            propApplicationsByName.getValue("demo.KotlinTag").single().placements,
        )
        val allNames = (typeApplications + propApplications).map { application ->
            application.annotation.typeId.requireTypeQualifiedName()
        }
        assertTrue(allNames.none { name -> name.startsWith("org.babyfish.jimmer.sql.") })
        assertTrue(allNames.none { name -> name.substringAfterLast('.') in setOf("Nullable", "NotNull") })
        assertTrue("org.babyfish.jimmer.kt.dto.KotlinDto" !in allNames)
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
    }

    @Test
    fun `snapshot preserves application order and declaration semantics`() {
        val first = fixture().freeze()
        val reordered = fixture(reversed = true).freeze()
        val changedTargets = fixture(kotlinTargets = listOf("FIELD", "PROPERTY_GETTER")).freeze()
        val changedVararg = fixture(kotlinValueVararg = false).freeze()

        assertNotEquals(first.normalizedSnapshot(), reordered.normalizedSnapshot())
        assertNotEquals(first.fingerprint(), reordered.fingerprint())
        assertEquals(64, first.fingerprint().length)
        assertNotEquals(first.fingerprint(), changedTargets.fingerprint())
        assertNotEquals(first.fingerprint(), changedVararg.fingerprint())
        assertTrue("KOTLIN" in first.normalizedSnapshot())
        assertTrue("PROPERTY" in first.normalizedSnapshot())
    }

    @Test
    fun `preserves repeatable annotations within each layer and overrides only the parent fqn`() {
        val fixture = fixture()
        val inheritedOnly = LsiSymbolId.type("demo.InheritedRepeat")
        val overridden = LsiSymbolId.type("demo.OverriddenRepeat")
        val baseType = fixture.schema.types.single()
        val dtoType = fixture.graph.types.single()
        val baseAnnotations = listOf(
            lsiAnnotation("demo.InheritedRepeat", "base-first"),
            lsiAnnotation("demo.InheritedRepeat", "base-first"),
            lsiAnnotation("demo.InheritedRepeat", "base-last"),
            lsiAnnotation("demo.OverriddenRepeat", "base-hidden"),
        )
        val dtoAnnotations = listOf(
            dtoAnnotation("demo.OverriddenRepeat", "\"dto-first\""),
            dtoAnnotation("demo.OverriddenRepeat", "\"dto-first\""),
            dtoAnnotation("demo.OverriddenRepeat", "\"dto-last\""),
        )
        val schema = JimmerImmutableSchema(
            listOf(baseType.copy(annotations = baseType.annotations + baseAnnotations))
        )
        val graph = JimmerDtoRenderGraph(
            source = fixture.graph.source,
            rootTypeIds = fixture.graph.rootTypeIds,
            types = listOf(dtoType.copy(annotations = dtoType.annotations + dtoAnnotations)),
            props = fixture.graph.props,
        )
        val declarations = javaAnnotationDeclaration(
            "demo.InheritedRepeat",
            listOf("TYPE"),
            listOf("value"),
        ) + javaAnnotationDeclaration(
            "demo.OverriddenRepeat",
            listOf("TYPE"),
            listOf("value"),
        )
        val contract = JimmerDtoAnnotationContractFreezer(
            LsiWorkspace(declarations = fixture.workspace.declarations + declarations),
            schema,
        ).freeze(graph)
        val applications = contract.typePlansByTypeId.getValue(ROOT_DTO_TYPE).applications

        assertEquals(
            listOf("base-first", "base-first", "base-last"),
            applications.filter { application -> application.annotation.typeId == inheritedOnly }
                .map(::annotationStringValue),
        )
        assertEquals(
            listOf("\"dto-first\"", "\"dto-first\"", "\"dto-last\""),
            applications.filter { application -> application.annotation.typeId == overridden }
                .map(::annotationSourceValue),
        )
        assertTrue(applications.none { application ->
            application.annotation.typeId == overridden && application.origin == JimmerDtoAnnotationOrigin.IMMUTABLE
        })
    }

    @Test
    fun `uses declaration placements instead of frontend fallback use site`() {
        val fixture = fixture()
        val immutableTag = LsiSymbolId.type("demo.ImmutableSiteTag")
        val dtoTag = LsiSymbolId.type("demo.DtoSiteTag")
        val baseProp = fixture.schema.types.single().props.single()
        val dtoProp = fixture.graph.props.single() as JimmerDtoBaseProp
        val schema = immutableSchema(
            typeAnnotations = fixture.schema.types.single().annotations,
            propAnnotations = baseProp.annotations + listOf(
                lsiAnnotation(
                    "demo.ImmutableSiteTag",
                    "field",
                    useSiteTarget = LsiAnnotationUseSiteTarget.FIELD,
                ),
                lsiAnnotation(
                    "demo.ImmutableSiteTag",
                    "getter",
                    useSiteTarget = LsiAnnotationUseSiteTarget.GETTER,
                ),
            ),
        )
        val graph = JimmerDtoRenderGraph(
            source = fixture.graph.source,
            rootTypeIds = fixture.graph.rootTypeIds,
            types = fixture.graph.types,
            props = listOf(
                dtoProp.copy(
                    annotations = dtoProp.annotations + dtoAnnotation("demo.DtoSiteTag", null),
                )
            ),
        )
        val declarations = javaAnnotationDeclaration(
            "demo.ImmutableSiteTag",
            listOf("FIELD", "METHOD"),
            listOf("value"),
        ) + javaAnnotationDeclaration(
            "demo.DtoSiteTag",
            listOf("FIELD", "METHOD"),
            emptyList(),
        )
        val contract = JimmerDtoAnnotationContractFreezer(
            LsiWorkspace(declarations = fixture.workspace.declarations + declarations),
            schema,
        ).freeze(graph)
        val applications = contract.propPlansByPropId.getValue(NAME_DTO_PROP).propertyApplications

        assertEquals(
            listOf(
                listOf(JimmerDtoAnnotationPlacement.FIELD, JimmerDtoAnnotationPlacement.GETTER),
                listOf(JimmerDtoAnnotationPlacement.FIELD, JimmerDtoAnnotationPlacement.GETTER),
            ),
            applications.filter { application -> application.annotation.typeId == immutableTag }
                .map(JimmerDtoAnnotationApplication::placements),
        )
        assertEquals(
            listOf(JimmerDtoAnnotationPlacement.FIELD, JimmerDtoAnnotationPlacement.GETTER),
            applications.single { application -> application.annotation.typeId == dtoTag }.placements,
        )
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
    }

    @Test
    fun `freezes chained property annotations from tail and builder setter annotations from head`() {
        val fixture = builderChainFixture(inputBuilder = true)
        val contract = fixture.freeze()
        val plan = contract.propPlansByPropId.getValue(CHAIN_DTO_PROP)
        val propertyTypeIds = plan.propertyApplications.map { application -> application.annotation.typeId }
        val builderTypeIds = plan.builderSetterApplications.map { application -> application.annotation.typeId }

        assertEquals(
            listOf(
                JSON_SERIALIZE,
                JSON_DESERIALIZE,
                JSON_ALIAS,
                JSON_ALIAS,
                TOOLS_JSON_DESERIALIZE,
            ),
            propertyTypeIds,
        )
        assertEquals(
            listOf(JSON_DESERIALIZE, JSON_ALIAS, TOOLS_JSON_DESERIALIZE, JSON_IGNORE),
            builderTypeIds,
        )
        assertEquals(
            STORE_NAME_PROP,
            plan.propertyApplications.single { application -> application.annotation.typeId == JSON_SERIALIZE }
                .sourceSymbolId,
        )
        assertEquals(
            JimmerDtoAnnotationOrigin.DTO,
            plan.builderSetterApplications.single { application -> application.annotation.typeId == JSON_DESERIALIZE }
                .origin,
        )
        assertEquals(
            STORE_PROP,
            plan.builderSetterApplications.single { application -> application.annotation.typeId == JSON_IGNORE }
                .sourceSymbolId,
        )
        assertTrue(JSON_IGNORE !in propertyTypeIds)
        assertTrue(JSON_SERIALIZE !in builderTypeIds)
        assertTrue(JSON_IGNORE in contract.declarationsByTypeId)
        assertTrue(TOOLS_JSON_DESERIALIZE in contract.declarationsByTypeId)
        assertTrue(
            contract.propPlansByPropId.getValue(CHAIN_TAIL_DTO_PROP).builderSetterApplications.isEmpty()
        )
        assertTrue("builder-setter-annotation" in contract.normalizedSnapshot())
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })

        val withoutBuilder = builderChainFixture(inputBuilder = false).freeze()
        assertEquals(
            plan.propertyApplications,
            withoutBuilder.propPlansByPropId.getValue(CHAIN_DTO_PROP).propertyApplications,
        )
        assertTrue(
            withoutBuilder.propPlansByPropId.values.all { propPlan ->
                propPlan.builderSetterApplications.isEmpty()
            }
        )
        assertNotEquals(contract.fingerprint(), withoutBuilder.fingerprint())
    }

    @Test
    fun `skips builder setter annotations for polymorphic input root`() {
        val fixture = builderChainFixture(inputBuilder = true)
        val rootType = fixture.graph.types.single()
        val graph = fixture.graph.copy(
            types = listOf(
                rootType.copy(
                    polymorphism = JimmerDtoPolymorphism(
                        exhaustive = false,
                        branches = listOf(
                            JimmerDtoPolymorphicBranch(
                                kind = JimmerDtoPolymorphicBranchKind.DEFAULT,
                                targetBaseTypeId = null,
                                declaredClassName = null,
                                className = "demo.dto.BookInput.Default",
                                bodyTypeId = rootType.id,
                                mergedTypeId = rootType.id,
                                implicit = true,
                                location = rootType.location,
                            )
                        ),
                    )
                )
            ),
        )
        val contract = JimmerDtoAnnotationContractFreezer(fixture.workspace, fixture.schema).freeze(graph)

        assertTrue(contract.propPlansByPropId.values.all { plan ->
            plan.builderSetterApplications.isEmpty()
        })
        assertTrue(
            contract.propPlansByPropId.getValue(CHAIN_DTO_PROP).propertyApplications.isNotEmpty()
        )
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
    }

    @Test
    fun `canonicalizes dto standard class literal types into lsi types`() {
        val base = fixture()
        val standardTypes = JimmerDtoAnnotation(
            typeId = STANDARD_TYPES,
            arguments = listOf(
                JimmerDtoAnnotationArgument(
                    "string",
                    JimmerDtoAnnotationValue.TypeValue(dtoTypeRef("String")),
                ),
                JimmerDtoAnnotationArgument(
                    "any",
                    JimmerDtoAnnotationValue.TypeValue(dtoTypeRef("Any")),
                ),
                JimmerDtoAnnotationArgument(
                    "list",
                    JimmerDtoAnnotationValue.TypeValue(
                        dtoTypeRef(
                            "List",
                            JimmerDtoTypeArgument(
                                JimmerDtoVariance.INVARIANT,
                                dtoTypeRef("String"),
                            ),
                        )
                    ),
                ),
                JimmerDtoAnnotationArgument(
                    "array",
                    JimmerDtoAnnotationValue.TypeValue(
                        dtoTypeRef(
                            "Array",
                            JimmerDtoTypeArgument(
                                JimmerDtoVariance.INVARIANT,
                                dtoTypeRef("Int"),
                            ),
                        )
                    ),
                ),
            ),
        )
        val originalProp = base.graph.props.single() as JimmerDtoBaseProp
        val graph = JimmerDtoRenderGraph(
            source = base.graph.source,
            rootTypeIds = base.graph.rootTypeIds,
            types = base.graph.types,
            props = listOf(
                originalProp.copy(annotations = originalProp.annotations + standardTypes)
            ),
        )
        val standardDeclarations = javaAnnotationDeclaration(
            qualifiedName = "demo.StandardTypes",
            targetNames = listOf("FIELD"),
            argumentNames = listOf("any", "array", "list", "string"),
        )
        val contract = JimmerDtoAnnotationContractFreezer(
            workspace = LsiWorkspace(declarations = base.workspace.declarations + standardDeclarations),
            immutableSchema = base.schema,
        ).freeze(graph)
        val annotation = contract.propPlansByPropId
            .getValue(NAME_DTO_PROP)
            .propertyApplications
            .single { application -> application.annotation.typeId == STANDARD_TYPES }
            .annotation
        val values = annotation.arguments.associate { argument -> argument.name to argument.value }

        val stringType = assertIs<LsiDeclaredType>(
            assertIs<JimmerDtoAppliedAnnotationValue.TypeValue>(values.getValue("string")).type
        )
        val anyType = assertIs<LsiDeclaredType>(
            assertIs<JimmerDtoAppliedAnnotationValue.TypeValue>(values.getValue("any")).type
        )
        val listType = assertIs<LsiDeclaredType>(
            assertIs<JimmerDtoAppliedAnnotationValue.TypeValue>(values.getValue("list")).type
        )
        val arrayType = assertIs<LsiArrayType>(
            assertIs<JimmerDtoAppliedAnnotationValue.TypeValue>(values.getValue("array")).type
        )

        assertEquals(LsiSymbolId.type("kotlin.String"), stringType.declarationId)
        assertEquals(LsiSymbolId.type("kotlin.Any"), anyType.declarationId)
        assertEquals(LsiSymbolId.type("kotlin.collections.List"), listType.declarationId)
        assertEquals(
            LsiSymbolId.type("kotlin.String"),
            assertIs<LsiDeclaredType>(listType.arguments.single().type).declarationId,
        )
        assertEquals(
            site.addzero.lsi.model.LsiPrimitiveKind.INT,
            assertIs<site.addzero.lsi.model.LsiPrimitiveType>(arrayType.elementType).kind,
        )
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
    }

    @Test
    fun `reports missing declarations wrong kinds invalid arguments and placements deterministically`() {
        val fixture = diagnosticFixture()
        val contract = fixture.freeze()
        val codes = contract.diagnostics.map { diagnostic -> diagnostic.code }

        assertEquals(
            listOf(
                "jimmer.dto.annotation.argument",
                "jimmer.dto.annotation.declaration-kind",
                "jimmer.dto.annotation.declaration-missing",
                "jimmer.dto.annotation.placement",
            ),
            codes,
        )
        assertEquals(
            LsiSymbolId.type("demo.FieldOnly").value,
            contract.diagnostics.single { diagnostic -> diagnostic.code == "jimmer.dto.annotation.placement" }
                .details
                .getValue("annotationType"),
        )
        assertTrue(contract.diagnostics.none { diagnostic ->
            diagnostic.details["annotationType"] == LsiSymbolId.type("demo.TypeOnly").value
        })
        assertTrue(contract.propPlansByPropId.getValue(NAME_DTO_PROP).propertyApplications.isEmpty())
        assertEquals(contract.diagnostics, contract.diagnostics.sortedBy { diagnostic -> diagnostic.code })
        assertEquals(contract.fingerprint(), diagnosticFixture(reversed = true).freeze().fingerprint())
    }

    @Test
    fun `contract field graph contains no frontend or poet types`() {
        val signatures = reachableFieldTypeSignatures(
            JimmerDtoAnnotationContract::class.java,
            JimmerDtoAnnotationDeclaration::class.java,
            JimmerDtoAnnotationApplication::class.java,
            JimmerDtoBuilderSetterAnnotationApplication::class.java,
            JimmerDtoAppliedAnnotation::class.java,
            JimmerDtoAppliedAnnotationValue::class.java,
        )
        val forbidden = signatures.filter { signature ->
            FORBIDDEN_TYPE_PREFIXES.any(signature::contains)
        }

        assertTrue(forbidden.isEmpty(), "DTO annotation contract exposes platform types: $forbidden")
    }

    private fun Fixture.freeze(): JimmerDtoAnnotationContract {
        return JimmerDtoAnnotationContractFreezer(workspace, schema).freeze(graph)
    }

    private fun fixture(
        reversed: Boolean = false,
        kotlinTargets: List<String> = listOf("FIELD", "PROPERTY_GETTER", "PROPERTY"),
        kotlinValueVararg: Boolean = true,
    ): Fixture {
        val baseOnly = lsiAnnotation(
            "demo.BaseOnly",
            explicitArguments = mapOf("value" to LsiAnnotationValue.StringValue("base")),
            defaultArguments = mapOf("ignored" to LsiAnnotationValue.StringValue("default")),
        )
        val typeAnnotations = listOf(
            lsiAnnotation("demo.Shared", "base"),
            lsiAnnotation("other.demo.Shared", "base-other"),
            baseOnly,
            lsiAnnotation("org.babyfish.jimmer.client.ApiIgnore"),
            lsiAnnotation("org.babyfish.jimmer.sql.Entity"),
            lsiAnnotation("org.jspecify.annotations.Nullable"),
            lsiAnnotation("org.babyfish.jimmer.kt.dto.KotlinDto"),
        ).maybeReversed(reversed)
        val propAnnotations = listOf(
            lsiAnnotation("demo.JavaTag", "base"),
            lsiAnnotation("org.babyfish.jimmer.sql.Column"),
            lsiAnnotation("jakarta.validation.constraints.NotNull"),
        ).maybeReversed(reversed)
        val dtoTypeAnnotations = listOf(
            dtoAnnotation("demo.Shared", "\"dto\""),
            dtoAnnotation("other.demo.Shared", "\"dto-other\""),
        ).maybeReversed(reversed)
        val dtoPropAnnotations = listOf(
            dtoAnnotation("demo.JavaTag", "\"dto\""),
            JimmerDtoAnnotation(
                typeId = KOTLIN_TAG,
                arguments = listOf(
                    JimmerDtoAnnotationArgument(
                        name = "value",
                        value = JimmerDtoAnnotationValue.ArrayValue(
                            listOf(
                                JimmerDtoAnnotationValue.LiteralValue("\"alpha\""),
                                JimmerDtoAnnotationValue.LiteralValue("\"beta\""),
                            )
                        ),
                    )
                ),
            ),
        ).maybeReversed(reversed)
        val schema = immutableSchema(typeAnnotations, propAnnotations)
        val graph = renderGraph(dtoTypeAnnotations, dtoPropAnnotations)
        val declarations = buildList {
            addAll(javaAnnotationDeclaration("demo.Shared", listOf("TYPE"), listOf("value")))
            addAll(javaAnnotationDeclaration("other.demo.Shared", listOf("TYPE"), listOf("value")))
            addAll(javaAnnotationDeclaration("demo.BaseOnly", listOf("TYPE"), listOf("ignored", "value")))
            addAll(javaAnnotationDeclaration("org.babyfish.jimmer.client.ApiIgnore", listOf("TYPE"), emptyList()))
            addAll(javaAnnotationDeclaration("demo.JavaTag", listOf("TYPE", "FIELD", "METHOD"), listOf("value")))
            addAll(kotlinAnnotationDeclaration("demo.KotlinTag", kotlinTargets, kotlinValueVararg))
        }.maybeReversed(reversed)
        return Fixture(
            workspace = LsiWorkspace(declarations = declarations),
            schema = schema,
            graph = graph,
        )
    }

    private fun diagnosticFixture(reversed: Boolean = false): Fixture {
        val schema = immutableSchema(emptyList(), emptyList())
        val typeAnnotations = listOf(dtoAnnotation("demo.FieldOnly", null)).maybeReversed(reversed)
        val propAnnotations = listOf(
            dtoAnnotation("demo.Marker", "\"illegal\""),
            dtoAnnotation("demo.TypeOnly", "\"ignored\""),
            dtoAnnotation("demo.NotAnnotation", null),
            dtoAnnotation("missing.Annotation", null),
        ).maybeReversed(reversed)
        val graph = renderGraph(typeAnnotations, propAnnotations)
        val markerDeclaration = javaAnnotationDeclaration("demo.Marker", listOf("FIELD"), emptyList())
        val typeOnlyDeclaration = javaAnnotationDeclaration("demo.TypeOnly", listOf("TYPE"), emptyList())
        val fieldOnlyDeclaration = javaAnnotationDeclaration("demo.FieldOnly", listOf("FIELD"), emptyList())
        val notAnnotation = typeDeclaration(
            qualifiedName = "demo.NotAnnotation",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = emptyList(),
            memberIds = emptyList(),
            language = LsiLanguage.JAVA,
        )
        val declarations = (
            markerDeclaration + typeOnlyDeclaration + fieldOnlyDeclaration + notAnnotation
            ).maybeReversed(reversed)
        return Fixture(
            workspace = LsiWorkspace(declarations = declarations),
            schema = schema,
            graph = graph,
        )
    }

    private fun builderChainFixture(inputBuilder: Boolean): Fixture {
        val base = fixture()
        val templateType = base.schema.types.single()
        val templateProp = templateType.props.single()
        val headProp = templateProp.copy(
            id = STORE_PROP,
            declarationId = STORE_PROP,
            ownerTypeId = BOOK_TYPE,
            declaringTypeId = BOOK_TYPE,
            name = "store",
            type = LsiDeclaredType(STORE_TYPE),
            annotations = listOf(
                lsiAnnotation(JSON_DESERIALIZE.requireTypeQualifiedName()),
                lsiAnnotation(JSON_IGNORE.requireTypeQualifiedName()),
            ),
            overrideChain = listOf(STORE_PROP),
            association = true,
            targetTypeId = STORE_TYPE,
            primaryMapping = JimmerImmutablePrimaryMapping.ASSOCIATION,
            associationKind = JimmerAssociationKind.MANY_TO_ONE,
        )
        val tailProp = templateProp.copy(
            id = STORE_NAME_PROP,
            declarationId = STORE_NAME_PROP,
            ownerTypeId = STORE_TYPE,
            declaringTypeId = STORE_TYPE,
            name = "name",
            annotations = listOf(lsiAnnotation(JSON_SERIALIZE.requireTypeQualifiedName())),
            overrideChain = listOf(STORE_NAME_PROP),
        )
        val schema = JimmerImmutableSchema(
            listOf(
                templateType.copy(
                    annotations = emptyList(),
                    props = listOf(headProp),
                ),
                templateType.copy(
                    id = STORE_TYPE,
                    qualifiedName = STORE_TYPE.requireTypeQualifiedName(),
                    annotations = emptyList(),
                    props = listOf(tailProp),
                ),
            ).sortedBy(JimmerImmutableType::id)
        )
        val templateDtoType = base.graph.types.single()
        val templateDtoProp = base.graph.props.single() as JimmerDtoBaseProp
        val chainProp = templateDtoProp.copy(
            id = CHAIN_DTO_PROP,
            name = "storeName",
            alias = "storeName",
            annotations = listOf(
                dtoAnnotation(JSON_DESERIALIZE.requireTypeQualifiedName(), null),
                dtoAnnotation(JSON_ALIAS.requireTypeQualifiedName(), null),
                dtoAnnotation(JSON_ALIAS.requireTypeQualifiedName(), null),
                dtoAnnotation(TOOLS_JSON_DESERIALIZE.requireTypeQualifiedName(), null),
            ),
            baseProps = listOf(JimmerDtoBasePropBinding("store", STORE_PROP)),
            basePath = "store.name",
            nextPropId = CHAIN_TAIL_DTO_PROP,
            tailPropId = CHAIN_TAIL_DTO_PROP,
            inputModifier = JimmerDtoModifier.FIXED,
        )
        val chainTailProp = templateDtoProp.copy(
            id = CHAIN_TAIL_DTO_PROP,
            name = "name",
            alias = "name",
            annotations = emptyList(),
            baseProps = listOf(JimmerDtoBasePropBinding("name", STORE_NAME_PROP)),
            basePath = "name",
            nextPropId = null,
            tailPropId = CHAIN_TAIL_DTO_PROP,
            inputModifier = JimmerDtoModifier.FIXED,
        )
        val graph = JimmerDtoRenderGraph(
            source = base.graph.source,
            rootTypeIds = base.graph.rootTypeIds,
            types = listOf(
                templateDtoType.copy(
                    modifiers = if (inputBuilder) setOf(JimmerDtoModifier.INPUT) else emptySet(),
                    annotations = emptyList(),
                    propIds = listOf(CHAIN_DTO_PROP),
                    hiddenFlatPropIds = listOf(CHAIN_TAIL_DTO_PROP),
                )
            ),
            props = listOf(chainProp, chainTailProp).sortedBy(JimmerDtoProp::id),
        )
        val jacksonDeclarations = listOf(
            JSON_DESERIALIZE,
            JSON_ALIAS,
            TOOLS_JSON_DESERIALIZE,
            JSON_IGNORE,
            JSON_SERIALIZE,
        ).flatMap { typeId ->
            javaAnnotationDeclaration(
                qualifiedName = typeId.requireTypeQualifiedName(),
                targetNames = listOf("METHOD"),
                argumentNames = emptyList(),
            )
        }
        return Fixture(
            workspace = LsiWorkspace(declarations = base.workspace.declarations + jacksonDeclarations),
            schema = schema,
            graph = graph,
        )
    }

    private fun immutableSchema(
        typeAnnotations: List<LsiAnnotation>,
        propAnnotations: List<LsiAnnotation>,
    ): JimmerImmutableSchema {
        val prop = JimmerImmutableProp(
            id = NAME_PROP,
            declarationId = NAME_PROP,
            ownerTypeId = BOOK_TYPE,
            declaringTypeId = BOOK_TYPE,
            name = "name",
            documentation = null,
            type = LsiDeclaredType(STRING_TYPE),
            annotations = propAnnotations,
            overrideChain = listOf(NAME_PROP),
            inherited = false,
            overridden = false,
            nullable = false,
            list = false,
            association = false,
            embedded = false,
            targetTypeId = null,
            primaryMapping = JimmerImmutablePrimaryMapping.SCALAR,
            primaryAnnotationTypeId = null,
            associationKind = JimmerAssociationKind.NONE,
            formulaKind = JimmerFormulaKind.NONE,
            transientResolver = null,
            view = null,
            genericTarget = false,
            remote = false,
            recursive = false,
            validations = emptyList(),
            converter = null,
        )
        return JimmerImmutableSchema(
            listOf(
                JimmerImmutableType(
                    id = BOOK_TYPE,
                    qualifiedName = "demo.Book",
                    kind = JimmerImmutableTypeKind.ENTITY,
                    documentation = null,
                    annotations = typeAnnotations,
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = listOf(prop),
                    primarySuperTypeId = null,
                    inheritanceRootTypeId = null,
                    inheritanceStrategy = null,
                    joinedTableDissociateAction = null,
                    instantiable = true,
                    discriminatorValue = null,
                    discriminatorPropId = null,
                    acrossMicroServices = false,
                    microServiceName = "",
                )
            )
        )
    }

    private fun renderGraph(
        typeAnnotations: List<JimmerDtoAnnotation>,
        propAnnotations: List<JimmerDtoAnnotation>,
    ): JimmerDtoRenderGraph {
        val type = JimmerDtoType(
            id = ROOT_DTO_TYPE,
            baseTypeId = BOOK_TYPE,
            packageName = "demo.dto",
            name = "BookView",
            modifiers = emptySet(),
            annotations = typeAnnotations,
            superInterfaces = emptyList(),
            documentation = null,
            location = LOCATION,
            focusedRecursion = false,
            propIds = listOf(NAME_DTO_PROP),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val prop = JimmerDtoBaseProp(
            id = NAME_DTO_PROP,
            ownerTypeId = ROOT_DTO_TYPE,
            name = "name",
            alias = "name",
            nullable = false,
            annotations = propAnnotations,
            documentation = null,
            aliasLocation = LOCATION,
            baseLocation = LOCATION,
            baseProps = listOf(JimmerDtoBasePropBinding("name", NAME_PROP)),
            basePath = "name",
            nextPropId = null,
            tailPropId = NAME_DTO_PROP,
            baseNullable = false,
            inputModifier = JimmerDtoModifier.FIXED,
            functionName = null,
            targetTypeId = null,
            enumType = null,
            config = null,
            recursive = false,
            likeOptions = emptySet(),
        )
        return JimmerDtoRenderGraph(
            source = SOURCE,
            rootTypeIds = listOf(ROOT_DTO_TYPE),
            types = listOf(type),
            props = listOf(prop),
        )
    }

    private fun javaAnnotationDeclaration(
        qualifiedName: String,
        targetNames: List<String>?,
        argumentNames: List<String>,
    ): List<LsiDeclaration> {
        val declaration = typeDeclaration(
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = targetNames?.let { names -> listOf(javaTarget(names)) }.orEmpty(),
            annotationMembers = argumentNames.map { argumentName ->
                LsiAnnotationMember(argumentName, LsiDeclaredType(STRING_TYPE))
            },
            language = LsiLanguage.JAVA,
        )
        return listOf(declaration)
    }

    private fun kotlinAnnotationDeclaration(
        qualifiedName: String,
        targetNames: List<String>,
        valueVararg: Boolean,
    ): List<LsiDeclaration> {
        val declaration = typeDeclaration(
            qualifiedName = qualifiedName,
            kind = LsiTypeDeclarationKind.ANNOTATION,
            annotations = listOf(kotlinTarget(targetNames)),
            annotationMembers = listOf(
                LsiAnnotationMember(
                    name = "value",
                    type = LsiArrayType(LsiDeclaredType(STRING_TYPE)),
                    vararg = valueVararg,
                )
            ),
            language = LsiLanguage.KOTLIN,
        )
        return listOf(declaration)
    }

    private fun typeDeclaration(
        qualifiedName: String,
        kind: LsiTypeDeclarationKind,
        annotations: List<LsiAnnotation>,
        memberIds: List<LsiSymbolId> = emptyList(),
        annotationMembers: List<LsiAnnotationMember> = emptyList(),
        language: LsiLanguage,
    ): LsiTypeDeclaration {
        return LsiTypeDeclaration(
            id = LsiSymbolId.type(qualifiedName),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            kind = kind,
            modality = LsiModality.FINAL,
            memberIds = memberIds,
            annotationMembers = annotationMembers,
            annotations = annotations,
            origin = origin(qualifiedName, language),
        )
    }

    private fun javaTarget(targetNames: List<String>): LsiAnnotation {
        return targetAnnotation(
            typeId = JAVA_TARGET,
            argumentName = "value",
            enumTypeId = JAVA_ELEMENT_TYPE,
            targetNames = targetNames,
        )
    }

    private fun kotlinTarget(targetNames: List<String>): LsiAnnotation {
        return targetAnnotation(
            typeId = KOTLIN_TARGET,
            argumentName = "allowedTargets",
            enumTypeId = KOTLIN_ANNOTATION_TARGET,
            targetNames = targetNames,
        )
    }

    private fun targetAnnotation(
        typeId: LsiSymbolId,
        argumentName: String,
        enumTypeId: LsiSymbolId,
        targetNames: List<String>,
    ): LsiAnnotation {
        return LsiAnnotation(
            type = typeId,
            arguments = mapOf(
                argumentName to LsiAnnotationArgument(
                    value = LsiAnnotationValue.ArrayValue(
                        targetNames.map { targetName ->
                            LsiAnnotationValue.EnumValue(enumTypeId, targetName)
                        }
                    ),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                )
            ),
        )
    }

    private fun lsiAnnotation(
        qualifiedName: String,
        value: String? = null,
        explicitArguments: Map<String, LsiAnnotationValue> = emptyMap(),
        defaultArguments: Map<String, LsiAnnotationValue> = emptyMap(),
        useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    ): LsiAnnotation {
        val arguments = linkedMapOf<String, LsiAnnotationArgument>()
        value?.let { explicitValue ->
            arguments["value"] = LsiAnnotationArgument(
                LsiAnnotationValue.StringValue(explicitValue),
                LsiAnnotationArgumentOrigin.EXPLICIT,
            )
        }
        explicitArguments.forEach { (name, argumentValue) ->
            arguments[name] = LsiAnnotationArgument(argumentValue, LsiAnnotationArgumentOrigin.EXPLICIT)
        }
        defaultArguments.forEach { (name, argumentValue) ->
            arguments[name] = LsiAnnotationArgument(argumentValue, LsiAnnotationArgumentOrigin.DEFAULT)
        }
        return LsiAnnotation(LsiSymbolId.type(qualifiedName), arguments, useSiteTarget)
    }

    private fun annotationStringValue(application: JimmerDtoAnnotationApplication): String {
        val argument = application.annotation.arguments.single { argument -> argument.name == "value" }
        return assertIs<JimmerDtoAppliedAnnotationValue.ScalarValue>(argument.value).value
    }

    private fun annotationSourceValue(application: JimmerDtoAnnotationApplication): String {
        val argument = application.annotation.arguments.single { argument -> argument.name == "value" }
        return assertIs<JimmerDtoAppliedAnnotationValue.SourceLiteralValue>(argument.value).code
    }

    private fun dtoAnnotation(
        qualifiedName: String,
        literal: String?,
    ): JimmerDtoAnnotation {
        return JimmerDtoAnnotation(
            typeId = LsiSymbolId.type(qualifiedName),
            arguments = literal?.let { value ->
                listOf(
                    JimmerDtoAnnotationArgument(
                        name = "value",
                        value = JimmerDtoAnnotationValue.LiteralValue(value),
                    )
                )
            }.orEmpty(),
        )
    }

    private fun dtoTypeRef(
        typeName: String,
        vararg arguments: JimmerDtoTypeArgument,
    ): JimmerDtoTypeRef {
        return JimmerDtoTypeRef(
            typeName = typeName,
            arguments = arguments.toList(),
            nullable = false,
            location = LOCATION,
        )
    }

    private fun origin(
        qualifiedName: String,
        language: LsiLanguage,
    ): LsiOrigin {
        return LsiOrigin(
            kind = LsiOriginKind.SOURCE,
            source = LsiSource.of("annotations/${qualifiedName.replace('.', '/')}.source", language),
        )
    }

    private fun <T> List<T>.maybeReversed(reversed: Boolean): List<T> {
        return if (reversed) asReversed() else this
    }

    private data class Fixture(
        val workspace: LsiWorkspace,
        val schema: JimmerImmutableSchema,
        val graph: JimmerDtoRenderGraph,
    )

    companion object {
        private val SOURCE = LsiSource.of("dto/Book.dto", LsiLanguage.UNKNOWN)
        private val LOCATION = LsiLocation(SOURCE, LsiPosition(1, 1))

        private val BOOK_TYPE = LsiSymbolId.type("demo.Book")
        private val STORE_TYPE = LsiSymbolId.type("demo.Store")
        private val STRING_TYPE = LsiSymbolId.type("java.lang.String")
        private val NAME_PROP = LsiSymbolId.property(BOOK_TYPE, "name")
        private val STORE_PROP = LsiSymbolId.property(BOOK_TYPE, "store")
        private val STORE_NAME_PROP = LsiSymbolId.property(STORE_TYPE, "name")

        private val ROOT_DTO_TYPE = JimmerDtoTypeId("dto/Book.dto#root")
        private val NAME_DTO_PROP = JimmerDtoPropId("dto/Book.dto#root/name")
        private val CHAIN_DTO_PROP = JimmerDtoPropId("dto/Book.dto#root/storeName")
        private val CHAIN_TAIL_DTO_PROP = JimmerDtoPropId("dto/Book.dto#root/storeName/tail")

        private val JAVA_TAG = LsiSymbolId.type("demo.JavaTag")
        private val KOTLIN_TAG = LsiSymbolId.type("demo.KotlinTag")
        private val STANDARD_TYPES = LsiSymbolId.type("demo.StandardTypes")
        private val JSON_DESERIALIZE =
            LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonDeserialize")
        private val JSON_ALIAS = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonAlias")
        private val TOOLS_JSON_DESERIALIZE =
            LsiSymbolId.type("tools.jackson.databind.annotation.JsonDeserialize")
        private val JSON_IGNORE = LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonIgnore")
        private val JSON_SERIALIZE =
            LsiSymbolId.type("com.fasterxml.jackson.databind.annotation.JsonSerialize")

        private val JAVA_TARGET = LsiSymbolId.type("java.lang.annotation.Target")
        private val JAVA_ELEMENT_TYPE = LsiSymbolId.type("java.lang.annotation.ElementType")
        private val KOTLIN_TARGET = LsiSymbolId.type("kotlin.annotation.Target")
        private val KOTLIN_ANNOTATION_TARGET = LsiSymbolId.type("kotlin.annotation.AnnotationTarget")

        private val FORBIDDEN_TYPE_PREFIXES = listOf(
            "javax.lang.model.",
            "com.sun.tools.javac.",
            "com.google.devtools.ksp.",
            "com.squareup.javapoet.",
            "com.squareup.kotlinpoet.",
        )

        private fun reachableFieldTypeSignatures(vararg roots: Class<*>): Set<String> {
            val signatures = sortedSetOf<String>()
            val pending = ArrayDeque<Class<*>>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Class<*>, Boolean>())
            roots.forEach(pending::addLast)
            while (pending.isNotEmpty()) {
                val type = pending.removeFirst()
                if (!visited.add(type)) {
                    continue
                }
                type.declaredFields.forEach { field ->
                    signatures += field.genericType.typeName
                    field.genericType.collectClasses(pending)
                }
            }
            return signatures
        }

        private fun Type.collectClasses(destination: ArrayDeque<Class<*>>) {
            when (this) {
                is Class<*> -> {
                    if (shouldTraverse()) {
                        destination.addLast(this)
                    }
                }
                is ParameterizedType -> {
                    rawType.collectClasses(destination)
                    actualTypeArguments.forEach { argument -> argument.collectClasses(destination) }
                }
                is GenericArrayType -> genericComponentType.collectClasses(destination)
                is WildcardType -> {
                    lowerBounds.forEach { bound -> bound.collectClasses(destination) }
                    upperBounds.forEach { bound -> bound.collectClasses(destination) }
                }
            }
        }

        private fun Class<*>.shouldTraverse(): Boolean {
            return !isPrimitive &&
                !isEnum &&
                !name.startsWith("java.") &&
                !name.startsWith("kotlin.")
        }
    }
}
