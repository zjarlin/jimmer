package site.addzero.lsi.jimmer.tuple.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructorConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTuplePropertyMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleSetterConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleTypeRefMetadata
import site.addzero.lsi.poet.renderJavaSource
import site.addzero.lsi.poet.renderKotlinSource

class TypedTupleMetadataGeneratorTest {

    @Test
    fun generates_expected_kotlin_snapshot_for_constructor_tuple() {
        val generator = TypedTupleMetadataGenerator()
        val metadata = simpleMetadata(
            construction = TypedTupleConstructorConstructionMetadata(
                argumentPropertyIndices = listOf(0, 1),
            ),
        )

        val fileSpec = generator.generate(metadata)

        assertEquals("test.BookTupleMapper", fileSpec.qualifiedName)
        assertEquals(
            """
            package test
            
            import java.util.Arrays
            import java.util.Collections
            import java.util.List
            import kotlin.Any
            import kotlin.Array
            import kotlin.Long
            import kotlin.String
            import org.babyfish.jimmer.sql.ast.Selection
            import org.babyfish.jimmer.sql.runtime.TupleMapper
            
            public class BookTupleMapper : TupleMapper<BookTuple> {
                private lateinit var selections: Array<Selection<*>?>
            
                private constructor(selections: Array<Selection<*>?>) {
                    this.selections = selections
                }
            
                public override fun getSelections(): List<Selection<*>> {
                    val selectionItems: Array<Selection<*>> = selections as Array<Selection<*>>
                    return Collections.unmodifiableList(Arrays.asList(*selectionItems))
                }
            
                public override fun createTuple(args: Array<Any?>): BookTuple = BookTuple(args[0] as Long, args[1] as String)
            
                public companion object {
                    public fun id(selection: Selection<Long>): NameBuilder {
                        val selections: Array<Selection<*>?> = arrayOfNulls<Selection<*>>(2)
                        selections[0] = selection
                        return NameBuilder(selections)
                    }
                }
            
                public class NameBuilder {
                    private lateinit var selections: Array<Selection<*>?>
            
                    internal constructor(selections: Array<Selection<*>?>) {
                        this.selections = selections
                    }
            
                    public fun name(selection: Selection<String>): BookTupleMapper {
                        selections[1] = selection
                        return BookTupleMapper(selections)
                    }
                }
            }
            
            """.trimIndent(),
            fileSpec.renderKotlinSource(),
        )
    }

    @Test
    fun generates_expected_java_snapshot_for_setter_tuple() {
        val generator = TypedTupleMetadataGenerator()
        val metadata = simpleMetadata(
            sourceSimpleName = "JavaBookTuple",
            sourceQualifiedName = "test.JavaBookTuple",
            sourceClassName = LsiClassName.bestGuess("test.JavaBookTuple"),
            generatedSimpleName = "JavaBookTupleMapper",
            generatedQualifiedName = "test.JavaBookTupleMapper",
            generatedClassName = LsiClassName.bestGuess("test.JavaBookTupleMapper"),
            construction = TypedTupleSetterConstructionMetadata(
                setterNames = listOf("setId", "setName"),
            ),
        )

        assertEquals(
            """
            package test;
            
            import java.lang.Long;
            import java.lang.Object;
            import java.lang.Override;
            import java.lang.String;
            import java.util.Arrays;
            import java.util.Collections;
            import java.util.List;
            import org.babyfish.jimmer.sql.ast.Selection;
            import org.babyfish.jimmer.sql.runtime.TupleMapper;
            
            public class JavaBookTupleMapper implements TupleMapper<JavaBookTuple> {
                private Selection<?>[] selections;
            
                private JavaBookTupleMapper(Selection<?>[] selections) {
                    this.selections = selections;
                }
            
                @Override
                public List<Selection<?>> getSelections() {
                    Selection<?>[] selectionItems = (Selection<?>[]) selections;
                    return Collections.unmodifiableList(Arrays.asList(selectionItems));
                }
            
                @Override
                public JavaBookTuple createTuple(Object[] args) {
                    JavaBookTuple __tuple = new JavaBookTuple();
                    __tuple.setId((Long) args[0]);
                    __tuple.setName((String) args[1]);
                    return __tuple;
                }
            
                public static NameBuilder id(Selection<Long> selection) {
                    Selection<?>[] selections = new Selection<?>[2];
                    selections[0] = selection;
                    return new NameBuilder(selections);
                }
            
                public static class NameBuilder {
                    private Selection<?>[] selections;
            
                    NameBuilder(Selection<?>[] selections) {
                        this.selections = selections;
                    }
            
                    public JavaBookTupleMapper name(Selection<String> selection) {
                        selections[1] = selection;
                        return new JavaBookTupleMapper(selections);
                    }
                }
            }
            
            """.trimIndent(),
            generator.generate(metadata).renderJavaSource(),
        )
    }

    private fun simpleMetadata(
        sourceSimpleName: String = "BookTuple",
        sourceQualifiedName: String = "test.BookTuple",
        sourceClassName: LsiClassName = LsiClassName.bestGuess("test.BookTuple"),
        generatedSimpleName: String = "BookTupleMapper",
        generatedQualifiedName: String = "test.BookTupleMapper",
        generatedClassName: LsiClassName = LsiClassName.bestGuess("test.BookTupleMapper"),
        construction: TypedTupleConstructionMetadata,
    ): TypedTupleMetadata =
        TypedTupleMetadata(
            id = sourceQualifiedName,
            sourceSimpleName = sourceSimpleName,
            sourceQualifiedName = sourceQualifiedName,
            sourceClassName = sourceClassName,
            packageName = "test",
            generatedSimpleName = generatedSimpleName,
            generatedQualifiedName = generatedQualifiedName,
            generatedClassName = generatedClassName,
            construction = construction,
            properties = listOf(
                TypedTuplePropertyMetadata(
                    id = "$sourceQualifiedName::id",
                    ownerTypeId = sourceQualifiedName,
                    name = "id",
                    type = typeRef("kotlin.Long"),
                ),
                TypedTuplePropertyMetadata(
                    id = "$sourceQualifiedName::name",
                    ownerTypeId = sourceQualifiedName,
                    name = "name",
                    type = typeRef("kotlin.String"),
                ),
            ),
        )

    private fun typeRef(
        qualifiedName: String,
    ): TypedTupleTypeRefMetadata =
        TypedTupleTypeRefMetadata(
            qualifiedName = qualifiedName,
            simpleName = qualifiedName.substringAfterLast('.'),
            presentableText = qualifiedName,
            nullable = false,
            primitive = false,
            array = false,
            typeArguments = emptyList(),
            componentType = null,
        )
}
