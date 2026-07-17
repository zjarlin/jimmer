package demo;

import demo.alpha.Book;
import demo.alpha.BookDraft;
import org.babyfish.jimmer.DraftConsumer;
import org.babyfish.jimmer.internal.GeneratedBy;

@GeneratedBy
public interface Immutables {
    static Book createBook(DraftConsumer<BookDraft> block) {
        return BookDraft.$.produce(block);
    }

    static Book createBook(Book base, DraftConsumer<BookDraft> block) {
        return BookDraft.$.produce(base, block);
    }

    static demo.beta.Book createBook_2(DraftConsumer<demo.beta.BookDraft> block) {
        return demo.beta.BookDraft.$.produce(block);
    }

    static demo.beta.Book createBook_2(demo.beta.Book base,
            DraftConsumer<demo.beta.BookDraft> block) {
        return demo.beta.BookDraft.$.produce(base, block);
    }
}
