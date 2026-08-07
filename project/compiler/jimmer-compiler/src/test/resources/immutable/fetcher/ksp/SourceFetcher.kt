@file:Suppress("warnings")
@file:GeneratedBy(type = demo.Book::class)

package demo

import kotlin.Boolean
import kotlin.Suppress
import kotlin.Unit
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.kt.DslScope
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.fetcher.IdOnlyFetchType
import org.babyfish.jimmer.sql.fetcher.ReferenceFetchType
import org.babyfish.jimmer.sql.fetcher.`impl`.FetcherImpl
import org.babyfish.jimmer.sql.kt.fetcher.FetcherCreator
import org.babyfish.jimmer.sql.kt.fetcher.KListFieldDsl
import org.babyfish.jimmer.sql.kt.fetcher.KRecursiveListFieldDsl
import org.babyfish.jimmer.sql.kt.fetcher.KRecursiveReferenceFieldDsl
import org.babyfish.jimmer.sql.kt.fetcher.KReferenceFieldDsl
import org.babyfish.jimmer.sql.kt.fetcher.`impl`.JavaFieldConfigUtils

@GeneratedBy(type = Book::class)
public fun FetcherCreator<Book>.`by`(block: BookFetcherDsl.() -> Unit): Fetcher<Book> {
    val dsl = BookFetcherDsl(emptyBookFetcher)
    dsl.block()
    return dsl.internallyGetFetcher()
}

@GeneratedBy(type = Book::class)
public fun FetcherCreator<Book>.`by`(base: Fetcher<Book>?, block: BookFetcherDsl.() -> Unit): Fetcher<Book> {
    val dsl = BookFetcherDsl(base ?: emptyBookFetcher)
    dsl.block()
    return dsl.internallyGetFetcher()
}

@DslScope
@GeneratedBy(type = Book::class)
public class BookFetcherDsl(
    fetcher: Fetcher<Book> = emptyBookFetcher,
) {
    private var _fetcher: Fetcher<Book> = fetcher

    public fun internallyGetFetcher(): Fetcher<Book> = _fetcher

    public fun allScalarFields() {
        _fetcher = _fetcher.allScalarFields()
    }

    public fun allTableFields() {
        _fetcher = _fetcher.allTableFields()
    }

    public fun title(enabled: Boolean = true) {
        _fetcher = if (enabled) {
            _fetcher.add("title")
        } else {
            _fetcher.remove("title")
        }
    }

    public fun parent(enabled: Boolean = true) {
        _fetcher = if (enabled) {
            _fetcher.add("parent")
        } else {
            _fetcher.remove("parent")
        }
    }

    public fun parent(idOnlyFetchType: IdOnlyFetchType) {
        _fetcher = _fetcher.add("parent", idOnlyFetchType)
    }

    public fun parent(childFetcher: Fetcher<Book>) {
        _fetcher = _fetcher.add(
            "parent",
            childFetcher
        )
    }

    public fun parent(childFetcher: Fetcher<Book>, cfgBlock: (KReferenceFieldDsl<Book>.() -> Unit)?) {
        _fetcher = _fetcher.add(
            "parent",
            childFetcher,
            JavaFieldConfigUtils.reference(cfgBlock)
        )
    }

    public fun parent(childBlock: BookFetcherDsl.() -> Unit) {
        _fetcher = _fetcher.add(
            "parent",
            BookFetcherDsl().apply { childBlock() }.internallyGetFetcher()
        )
    }

    public fun parent(cfgBlock: (KReferenceFieldDsl<Book>.() -> Unit)?, childBlock: BookFetcherDsl.() -> Unit) {
        _fetcher = _fetcher.add(
            "parent",
            BookFetcherDsl().apply { childBlock() }.internallyGetFetcher(),
            JavaFieldConfigUtils.reference(cfgBlock)
        )
    }

    public fun parent(enabled: Boolean, childFetcher: Fetcher<Book>) {
        if (!enabled) {
            _fetcher = _fetcher.remove("parent")
        } else {
            parent(childFetcher)
        }
    }

    public fun parent(
        enabled: Boolean,
        childFetcher: Fetcher<Book>,
        cfgBlock: (KReferenceFieldDsl<Book>.() -> Unit)?,
    ) {
        if (!enabled) {
            _fetcher = _fetcher.remove("parent")
        } else {
            parent(childFetcher, cfgBlock)
        }
    }

    public fun parent(enabled: Boolean, childBlock: BookFetcherDsl.() -> Unit) {
        if (!enabled) {
            _fetcher = _fetcher.remove("parent")
        } else {
            parent(childBlock)
        }
    }

    public fun parent(
        enabled: Boolean,
        cfgBlock: (KReferenceFieldDsl<Book>.() -> Unit)?,
        childBlock: BookFetcherDsl.() -> Unit,
    ) {
        if (!enabled) {
            _fetcher = _fetcher.remove("parent")
        } else {
            parent(cfgBlock, childBlock)
        }
    }

    public fun parent(fetchType: ReferenceFetchType, childFetcher: Fetcher<Book>) {
        _fetcher = _fetcher.add(
            "parent",
            childFetcher,
            JavaFieldConfigUtils.reference<Book>(fetchType)
        )
    }

    public fun parent(fetchType: ReferenceFetchType, childBlock: BookFetcherDsl.() -> Unit) {
        _fetcher = _fetcher.add(
            "parent",
            BookFetcherDsl().apply { childBlock() }.internallyGetFetcher(),
            JavaFieldConfigUtils.reference<Book>(fetchType)
        )
    }

    public fun `parent*`() {
        _fetcher = _fetcher.addRecursion(
            "parent",
            null
        )
    }

    public fun `parent*`(cfgBlock: (KRecursiveReferenceFieldDsl<Book>.() -> Unit)?) {
        _fetcher = _fetcher.addRecursion(
            "parent",
            JavaFieldConfigUtils.recursiveReference(cfgBlock)
        )
    }

    public fun parentId(enabled: Boolean = true) {
        _fetcher = if (enabled) {
            _fetcher.add("parentId")
        } else {
            _fetcher.remove("parentId")
        }
    }

    public fun parentId(idOnlyFetchType: IdOnlyFetchType) {
        _fetcher = _fetcher.add("parentId", idOnlyFetchType)
    }

    public fun children(enabled: Boolean = true) {
        _fetcher = if (enabled) {
            _fetcher.add("children")
        } else {
            _fetcher.remove("children")
        }
    }

    public fun children(childFetcher: Fetcher<Book>) {
        _fetcher = _fetcher.add(
            "children",
            childFetcher
        )
    }

    public fun children(childFetcher: Fetcher<Book>, cfgBlock: (KListFieldDsl<Book>.() -> Unit)?) {
        _fetcher = _fetcher.add(
            "children",
            childFetcher,
            JavaFieldConfigUtils.list(cfgBlock)
        )
    }

    public fun children(childBlock: BookFetcherDsl.() -> Unit) {
        _fetcher = _fetcher.add(
            "children",
            BookFetcherDsl().apply { childBlock() }.internallyGetFetcher()
        )
    }

    public fun children(cfgBlock: (KListFieldDsl<Book>.() -> Unit)?, childBlock: BookFetcherDsl.() -> Unit) {
        _fetcher = _fetcher.add(
            "children",
            BookFetcherDsl().apply { childBlock() }.internallyGetFetcher(),
            JavaFieldConfigUtils.list(cfgBlock)
        )
    }

    public fun children(enabled: Boolean, childFetcher: Fetcher<Book>) {
        if (!enabled) {
            _fetcher = _fetcher.remove("children")
        } else {
            children(childFetcher)
        }
    }

    public fun children(
        enabled: Boolean,
        childFetcher: Fetcher<Book>,
        cfgBlock: (KListFieldDsl<Book>.() -> Unit)?,
    ) {
        if (!enabled) {
            _fetcher = _fetcher.remove("children")
        } else {
            children(childFetcher, cfgBlock)
        }
    }

    public fun children(enabled: Boolean, childBlock: BookFetcherDsl.() -> Unit) {
        if (!enabled) {
            _fetcher = _fetcher.remove("children")
        } else {
            children(childBlock)
        }
    }

    public fun children(
        enabled: Boolean,
        cfgBlock: (KListFieldDsl<Book>.() -> Unit)?,
        childBlock: BookFetcherDsl.() -> Unit,
    ) {
        if (!enabled) {
            _fetcher = _fetcher.remove("children")
        } else {
            children(cfgBlock, childBlock)
        }
    }

    public fun `children*`() {
        _fetcher = _fetcher.addRecursion(
            "children",
            null
        )
    }

    public fun `children*`(cfgBlock: (KRecursiveListFieldDsl<Book>.() -> Unit)?) {
        _fetcher = _fetcher.addRecursion(
            "children",
            JavaFieldConfigUtils.recursiveList(cfgBlock)
        )
    }

    public fun transientLabel(enabled: Boolean = true) {
        _fetcher = if (enabled) {
            _fetcher.add("transientLabel")
        } else {
            _fetcher.remove("transientLabel")
        }
    }
}

private val emptyBookFetcher: Fetcher<Book> = FetcherImpl(Book::class.java)
