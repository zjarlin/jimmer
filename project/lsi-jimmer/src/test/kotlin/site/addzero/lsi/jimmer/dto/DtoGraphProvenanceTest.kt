package site.addzero.lsi.jimmer.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

class DtoGraphProvenanceTest {

    @Test
    fun `collects every structural and annotation source in stable order`() {
        val sources = SOURCE_NAMES.associateWith { name ->
            LsiSource.of("demo/src/main/dto/$name.dto")
        }
        val documentSource = sources.getValue("Document")
        val rootTypeId = DtoTypeId("${documentSource.path}#root")
        val bodyTypeId = DtoTypeId("${documentSource.path}#body")
        val mergedTypeId = DtoTypeId("${documentSource.path}#merged")
        val basePropId = DtoPropId("${documentSource.path}#root/prop:base")
        val foldPropId = DtoPropId("${documentSource.path}#root/prop:fold")
        val userPropId = DtoPropId("${documentSource.path}#root/prop:user")
        val nestedAnnotation = DtoAnnotation(
            typeId = LsiSymbolId.type("demo.Nested"),
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "type",
                    value = DtoAnnotationValue.TypeValue(
                        typeRef("demo.AnnotationType", sources.getValue("NestedAnnotationType"))
                    ),
                )
            ),
        )
        val rootType = DtoType(
            id = rootTypeId,
            baseTypeId = LsiSymbolId.type("demo.Entity"),
            packageName = "demo.dto",
            name = "RootView",
            modifiers = emptySet(),
            annotations = listOf(
                DtoAnnotation(
                    typeId = LsiSymbolId.type("demo.Marker"),
                    arguments = listOf(
                        DtoAnnotationArgument(
                            name = "values",
                            value = DtoAnnotationValue.ArrayValue(
                                listOf(
                                    DtoAnnotationValue.AnnotationValue(nestedAnnotation),
                                    DtoAnnotationValue.TypeValue(
                                        typeRef("demo.ArrayType", sources.getValue("ArrayAnnotationType"))
                                    ),
                                )
                            ),
                        )
                    ),
                )
            ),
            superInterfaces = listOf(
                DtoTypeRef(
                    typeName = "demo.Contract",
                    arguments = listOf(
                        DtoTypeArgument(
                            variance = DtoVariance.OUT,
                            type = typeRef("demo.Payload", sources.getValue("InterfaceArgument")),
                        )
                    ),
                    nullable = false,
                    location = location(sources.getValue("Interface")),
                )
            ),
            documentation = null,
            location = location(sources.getValue("RootType")),
            focusedRecursion = false,
            propIds = listOf(basePropId, foldPropId, userPropId),
            hiddenFlatPropIds = emptyList(),
            polymorphism = DtoPolymorphism(
                exhaustive = true,
                branches = listOf(
                    DtoPolymorphicBranch(
                        kind = DtoPolymorphicBranchKind.DEFAULT,
                        targetBaseTypeId = null,
                        declaredClassName = null,
                        className = "demo.dto.DefaultView",
                        bodyTypeId = bodyTypeId,
                        mergedTypeId = mergedTypeId,
                        implicit = false,
                        location = location(sources.getValue("Branch")),
                    )
                ),
            ),
        )
        val baseProp = DtoBaseProp(
            id = basePropId,
            ownerTypeId = rootTypeId,
            name = "base",
            alias = "base",
            nullable = false,
            annotations = listOf(
                DtoAnnotation(
                    typeId = LsiSymbolId.type("demo.PropMarker"),
                    arguments = listOf(
                        DtoAnnotationArgument(
                            name = "type",
                            value = DtoAnnotationValue.TypeValue(
                                typeRef("demo.PropType", sources.getValue("PropAnnotationType"))
                            ),
                        )
                    ),
                )
            ),
            documentation = null,
            aliasLocation = location(sources.getValue("BaseAlias")),
            baseLocation = location(sources.getValue("BaseDeclaration")),
            baseProps = listOf(
                DtoBasePropBinding(
                    name = "base",
                    propId = LsiSymbolId.property(LsiSymbolId.type("demo.Entity"), "base"),
                )
            ),
            basePath = "base",
            nextPropId = null,
            tailPropId = basePropId,
            baseNullable = false,
            inputModifier = DtoModifier.STATIC,
            functionName = null,
            targetTypeId = bodyTypeId,
            enumType = null,
            config = DtoPropConfig(
                predicate = null,
                orderItems = emptyList(),
                filter = DtoConfigTypeRef(
                    LsiSymbolId.type("demo.Filter"),
                    location(sources.getValue("Filter")),
                ),
                recursion = DtoConfigTypeRef(
                    LsiSymbolId.type("demo.Recursion"),
                    location(sources.getValue("Recursion")),
                ),
                fetchType = DtoFetchType.AUTO,
                limit = null,
                batch = null,
                depth = null,
            ),
            recursive = false,
            likeOptions = emptySet(),
        )
        val foldProp = DtoFoldProp(
            id = foldPropId,
            ownerTypeId = rootTypeId,
            name = "fold",
            alias = "fold",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = location(sources.getValue("FoldAlias")),
            nullGuardPropId = basePropId,
            targetTypeId = bodyTypeId,
        )
        val userProp = DtoUserProp(
            id = userPropId,
            ownerTypeId = rootTypeId,
            name = "user",
            alias = "user",
            nullable = false,
            annotations = emptyList(),
            documentation = null,
            aliasLocation = location(sources.getValue("UserAlias")),
            type = typeRef("demo.UserValue", sources.getValue("UserType")),
            defaultValueText = null,
        )
        val graph = DtoGraph(
            source = documentSource,
            rootTypeIds = listOf(rootTypeId),
            types = listOf(
                simpleType(bodyTypeId, sources.getValue("BodyType")),
                simpleType(mergedTypeId, sources.getValue("MergedType")),
                rootType,
            ).sortedBy(DtoType::id),
            props = listOf(baseProp, foldProp, userProp).sortedBy(DtoProp::id),
        )

        assertEquals(sources.values.toSortedSet(), graph.dependencySources())
        assertEquals(
            setOf(
                LsiSymbolId.type("demo.Entity"),
                LsiSymbolId.type("demo.Filter"),
                LsiSymbolId.type("demo.Marker"),
                LsiSymbolId.type("demo.Nested"),
                LsiSymbolId.type("demo.PropMarker"),
                LsiSymbolId.type("demo.Recursion"),
                LsiSymbolId.property(LsiSymbolId.type("demo.Entity"), "base"),
            ),
            graph.dependencySymbols(),
        )
    }

    private fun simpleType(id: DtoTypeId, source: LsiSource): DtoType {
        return DtoType(
            id = id,
            baseTypeId = null,
            packageName = "demo.dto",
            name = id.value.substringAfterLast('#'),
            modifiers = emptySet(),
            annotations = emptyList(),
            superInterfaces = emptyList(),
            documentation = null,
            location = location(source),
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
    }

    private fun typeRef(typeName: String, source: LsiSource): DtoTypeRef {
        return DtoTypeRef(typeName, emptyList(), false, location(source))
    }

    private fun location(source: LsiSource): LsiLocation {
        return LsiLocation(source, LsiPosition(1, 1))
    }

    private companion object {
        val SOURCE_NAMES = listOf(
            "ArrayAnnotationType",
            "BaseAlias",
            "BaseDeclaration",
            "BodyType",
            "Branch",
            "Document",
            "Filter",
            "FoldAlias",
            "Interface",
            "InterfaceArgument",
            "MergedType",
            "NestedAnnotationType",
            "PropAnnotationType",
            "Recursion",
            "RootType",
            "UserAlias",
            "UserType",
        )
    }
}
