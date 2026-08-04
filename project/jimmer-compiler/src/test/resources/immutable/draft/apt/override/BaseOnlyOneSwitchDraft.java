package demo;

import java.util.Collections;
import org.babyfish.jimmer.Draft;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.lang.OldChain;
import org.babyfish.jimmer.meta.ImmutablePropCategory;
import org.babyfish.jimmer.meta.ImmutableType;

@GeneratedBy(
        type = BaseOnlyOneSwitch.class
)
public interface BaseOnlyOneSwitchDraft extends BaseOnlyOneSwitch, Draft {
    BaseOnlyOneSwitchDraft.Producer $ = Producer.INSTANCE;

    @OldChain
    BaseOnlyOneSwitchDraft setStatus(int status);

    @GeneratedBy(
            type = BaseOnlyOneSwitch.class
    )
    class Producer {
        static final Producer INSTANCE = new Producer();

        public static final ImmutableType TYPE = ImmutableType
            .newBuilder(
                "0.11.6",
                BaseOnlyOneSwitch.class,
                Collections.emptyList(),
                null
            )
            .add(-1, "status", ImmutablePropCategory.SCALAR, int.class, false)
            .build();

        private Producer() {
        }
    }
}
