package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.poet.renderJavaSource

class ImmutableAptGeneratorRenderTest {

    @Test
    fun generates_expected_java_shape_for_embeddable_prop_expression() {
        val fileSpec = EmbeddedPropExpressionGenerator(addressMetadata()).generate()

        assertEquals("test.model.AddressPropExpression", fileSpec.qualifiedName)

        val source = fileSpec.renderJavaSource()

        assertTrue(source.contains("public class AddressPropExpression extends AbstractTypedEmbeddedPropExpression<Address>"), source)
        assertTrue(source.contains("public AddressPropExpression(PropExpression.Embedded<Address> raw)"), source)
        assertTrue(source.contains("public AddressPropExpression(AddressPropExpression base, BaseTableOwner baseTableOwner)"), source)
        assertTrue(source.contains("public PropExpression.Str city()"), source)
        assertTrue(source.contains("return __get(AddressProps.CITY.unwrap());"), source)
        assertTrue(source.contains("public GeoPropExpression geo()"), source)
        assertTrue(source.contains("return new GeoPropExpression(__get(AddressProps.GEO.unwrap()));"), source)
        assertTrue(source.contains("public AddressPropExpression __baseTableOwner(BaseTableOwner baseTableOwner)"), source)
    }

    @Test
    fun generates_expected_java_shape_for_entity_table_artifacts() {
        val fileSpecs = TableGenerator(bookMetadata()).generate()

        assertEquals(
            listOf("test.model.BookTable", "test.model.BookTableEx"),
            fileSpecs.map { it.qualifiedName },
        )

        val tableSource = fileSpecs[0].renderJavaSource()
        val tableExSource = fileSpecs[1].renderJavaSource()

        assertTrue(tableSource.contains("public class BookTable extends AbstractTypedTable<Book> implements BookProps"), tableSource)
        assertTrue(tableSource.contains("public static final BookTable ${'$'} = new BookTable();"), tableSource)
        assertTrue(tableSource.contains("public BookTable()"), tableSource)
        assertTrue(tableSource.contains("super(Book.class);"), tableSource)
        assertTrue(tableSource.contains("public PropExpression.Str name()"), tableSource)
        assertTrue(tableSource.contains("public AddressPropExpression address()"), tableSource)
        assertTrue(tableSource.contains("public AuthorTable author()"), tableSource)
        assertTrue(tableSource.contains("public AuthorTable author(JoinType joinType)"), tableSource)
        assertTrue(tableSource.contains("public PropExpression.Num<Long> authorId()"), tableSource)
        assertTrue(tableSource.contains("return __getAssociatedId(BookProps.AUTHOR.unwrap());"), tableSource)
        assertTrue(tableSource.contains("public CustomerTable.Remote customer()"), tableSource)
        assertTrue(tableSource.contains("public Predicate stores(Function<StoreTableEx, Predicate> block)"), tableSource)
        assertTrue(tableSource.contains("public static class Remote extends AbstractTypedTable<Book>"), tableSource)
        assertTrue(tableSource.contains("public PropExpression.Num<Long> id()"), tableSource)
        assertTrue(tableSource.contains("throw new UnsupportedOperationException();"), tableSource)

        assertTrue(tableExSource.contains("public class BookTableEx extends BookTable implements TableExProxy<Book, BookTable>"), tableExSource)
        assertTrue(
            tableExSource.contains("public static final BookTableEx ${'$'} = new BookTableEx(BookTable.${'$'}, (String)null);") ||
                tableExSource.contains("public static final BookTableEx ${'$'} = new BookTableEx(BookTable.${'$'}, (String) null);"),
            tableExSource,
        )
        assertTrue(tableExSource.contains("public AuthorTableEx author()"), tableExSource)
        assertTrue(tableExSource.contains("public AuthorTableEx author(JoinType joinType)"), tableExSource)
        assertTrue(tableExSource.contains("public <TT extends Table<?>> TT weakJoin("), tableExSource)
        assertTrue(tableExSource.contains("public <TT extends BaseTable> TT weakJoin("), tableExSource)
        assertTrue(tableExSource.contains("JWeakJoinLambdaFactory.get(weakJoinLambda)"), tableExSource)
        assertTrue(tableExSource.contains("return new BookTableEx(this, reason);"), tableExSource)
    }

    @Test
    fun generates_expected_java_shape_for_fetcher_core_artifact() {
        val fileSpec = FetcherGenerator(
            sourcePackageName = ImmutableGeneratorTestFixtures.SOURCE_PACKAGE_NAME,
            sourceFileName = ImmutableGeneratorTestFixtures.SOURCE_FILE_NAME,
            type = ImmutableGeneratorTestFixtures.bookFetcherReferenceMetadata(),
        ).generate(mode = ImmutableGenerationMode.JAVA_SHARED).single()

        assertEquals("test.model.BookFetcher", fileSpec.qualifiedName)

        val source = fileSpec.renderJavaSource()
        val compactSource = source.replace(Regex("\\s+"), "")
        fun assertContains(snippet: String) {
            assertTrue(compactSource.contains(snippet.replace(Regex("\\s+"), "")), source)
        }

        assertContains("public class BookFetcher extends AbstractTypedFetcher<Book, BookFetcher>")
        assertContains("public static final BookFetcher ${'$'} = new BookFetcher(null);")
        assertContains("public static BookFetcher ${'$'}from(Fetcher<Book> base)")
        assertContains("private BookFetcher(FetcherImpl<Book> base)")
        assertContains("private BookFetcher(BookFetcher prev, ImmutableProp prop, boolean negative, IdOnlyFetchType idOnlyFetchType)")
        assertContains("private BookFetcher(BookFetcher prev, ImmutableProp prop, FieldConfig<?, ? extends Table<?>> fieldConfig)")
        assertContains("public BookFetcher store()")
        assertContains("public BookFetcher store(Fetcher<Store> childFetcher, Consumer<? extends ReferenceFieldConfig<Store, StoreTable>> fieldConfig)")
        assertContains("public BookFetcher recursiveStore()")
        assertContains("public BookFetcher recursiveStore(Consumer<? extends RecursiveReferenceFieldConfig<Store, StoreTable>> fieldConfig)")
        assertContains("protected BookFetcher createFetcher(ImmutableProp prop, boolean negative, IdOnlyFetchType idOnlyFetchType)")
        assertContains("protected BookFetcher createFetcher(ImmutableProp prop, FieldConfig<?, ? extends Table<?>> fieldConfig)")
    }

    private fun addressMetadata(): ImmutablePropsTypeMetadata =
        ImmutableGeneratorTestFixtures.addressPropsMetadata()

    private fun bookMetadata(): ImmutablePropsTypeMetadata =
        ImmutableGeneratorTestFixtures.bookTablePropsMetadata()
}
