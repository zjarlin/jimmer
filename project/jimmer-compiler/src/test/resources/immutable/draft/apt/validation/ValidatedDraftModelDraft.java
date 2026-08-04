package demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.Serializable;
import java.lang.CloneNotSupportedException;
import java.lang.Cloneable;
import java.lang.IllegalStateException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.lang.System;
import java.util.Collections;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.babyfish.jimmer.CircularReferenceException;
import org.babyfish.jimmer.Draft;
import org.babyfish.jimmer.DraftConsumer;
import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.UnloadedException;
import org.babyfish.jimmer.impl.validation.Validator;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.jackson.ImmutableModuleRequiredException;
import org.babyfish.jimmer.lang.OldChain;
import org.babyfish.jimmer.meta.ImmutablePropCategory;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.PropId;
import org.babyfish.jimmer.runtime.DraftContext;
import org.babyfish.jimmer.runtime.DraftSpi;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.runtime.Internal;
import org.babyfish.jimmer.runtime.Visibility;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@GeneratedBy(
        type = ValidatedDraftModel.class
)
public interface ValidatedDraftModelDraft extends ValidatedDraftModel, Draft {
    ValidatedDraftModelDraft.Producer $ = Producer.INSTANCE;

    @OldChain
    ValidatedDraftModelDraft setRequiredName(String requiredName);

    @OldChain
    ValidatedDraftModelDraft setTitle(String title);

    @GeneratedBy(
            type = ValidatedDraftModel.class
    )
    class Producer {
        static final Producer INSTANCE = new Producer();

        public static final int SLOT_REQUIRED_NAME = 0;

        public static final int SLOT_TITLE = 1;

        public static final ImmutableType TYPE = ImmutableType
            .newBuilder(
                "0.11.6",
                ValidatedDraftModel.class,
                Collections.emptyList(),
                (ctx, base) -> new DraftImpl(ctx, (ValidatedDraftModel)base)
            )
            .add(SLOT_REQUIRED_NAME, "requiredName", ImmutablePropCategory.SCALAR, String.class, false)
            .add(SLOT_TITLE, "title", ImmutablePropCategory.SCALAR, String.class, false)
            .build();

        private Producer() {
        }

        public ValidatedDraftModel produce(DraftConsumer<ValidatedDraftModelDraft> block) {
            return (ValidatedDraftModel)Internal.produce(TYPE, null, block);
        }

        public ValidatedDraftModel produce(ValidatedDraftModel base,
                DraftConsumer<ValidatedDraftModelDraft> block) {
            return (ValidatedDraftModel)Internal.produce(TYPE, base, block);
        }

        public ValidatedDraftModel produce(boolean resolveImmediately,
                DraftConsumer<ValidatedDraftModelDraft> block) {
            return (ValidatedDraftModel)Internal.produce(TYPE, null, resolveImmediately, block);
        }

        public ValidatedDraftModel produce(ValidatedDraftModel base, boolean resolveImmediately,
                DraftConsumer<ValidatedDraftModelDraft> block) {
            return (ValidatedDraftModel)Internal.produce(TYPE, base, resolveImmediately, block);
        }

        /**
         * Class, not interface, for free-marker
         */
        @GeneratedBy(
                type = ValidatedDraftModel.class
        )
        @JsonPropertyOrder({"dummyPropForJacksonError__", "requiredName", "title"})
        public abstract static class Implementor implements ValidatedDraftModel, ImmutableSpi {
            @Override
            public final Object __get(PropId prop) {
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		return __get(prop.asName());
                    case SLOT_REQUIRED_NAME:
                    		return requiredName();
                    case SLOT_TITLE:
                    		return title();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            @Override
            public final Object __get(String prop) {
                switch (prop) {
                    case "requiredName":
                    		return requiredName();
                    case "title":
                    		return title();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            public final String getRequiredName() {
                return requiredName();
            }

            @NotBlank(
                    message = "title must not be blank"
            )
            @Size(
                    min = 2,
                    max = 8,
                    message = "title size"
            )
            @Pattern(
                    regexp = "[A-Z][a-z]+",
                    message = "title pattern"
            )
            @IdCard(
                    message = "invalid id card"
            )
            public final String getTitle() {
                return title();
            }

            @Override
            public final ImmutableType __type() {
                return TYPE;
            }

            public final int getDummyPropForJacksonError__() {
                throw new ImmutableModuleRequiredException();
            }
        }

        @GeneratedBy(
                type = ValidatedDraftModel.class
        )
        private static class Impl extends Implementor implements Cloneable, Serializable {
            private Visibility __visibility;

            String __requiredNameValue;

            String __titleValue;

            @Override
            @JsonIgnore
            public String requiredName() {
                if (__requiredNameValue == null) {
                    throw new UnloadedException(ValidatedDraftModel.class, "requiredName");
                }
                return __requiredNameValue;
            }

            @Override
            @JsonIgnore
            public String title() {
                if (__titleValue == null) {
                    throw new UnloadedException(ValidatedDraftModel.class, "title");
                }
                return __titleValue;
            }

            @Override
            public Impl clone() {
                try {
                    Impl copy = (Impl) super.clone();
                    Visibility originalVisibility = this.__visibility;
                    if (originalVisibility != null) {
                        Visibility newVisibility = Visibility.of(2);
                        for (int propId = 0; propId < 2; propId++) {
                            newVisibility.show(propId, originalVisibility.visible(propId));
                        }
                        copy.__visibility = newVisibility;
                    } else {
                        copy.__visibility = null;
                    }
                    return copy;
                } catch(CloneNotSupportedException ex) {
                    throw new AssertionError(ex);
                }
            }

            @Override
            public boolean __isLoaded(PropId prop) {
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		return __isLoaded(prop.asName());
                    case SLOT_REQUIRED_NAME:
                    		return __requiredNameValue != null;
                    case SLOT_TITLE:
                    		return __titleValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            @Override
            public boolean __isLoaded(String prop) {
                switch (prop) {
                    case "requiredName":
                    		return __requiredNameValue != null;
                    case "title":
                    		return __titleValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            @Override
            public boolean __isVisible(PropId prop) {
                if (__visibility == null) {
                    return true;
                }
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		return __isVisible(prop.asName());
                    case SLOT_REQUIRED_NAME:
                    		return __visibility.visible(SLOT_REQUIRED_NAME);
                    case SLOT_TITLE:
                    		return __visibility.visible(SLOT_TITLE);
                    default: return true;
                }
            }

            @Override
            public boolean __isVisible(String prop) {
                if (__visibility == null) {
                    return true;
                }
                switch (prop) {
                    case "requiredName":
                    		return __visibility.visible(SLOT_REQUIRED_NAME);
                    case "title":
                    		return __visibility.visible(SLOT_TITLE);
                    default: return true;
                }
            }

            @Override
            public int hashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__requiredNameValue != null) {
                    hash = 31 * hash + __requiredNameValue.hashCode();
                }
                if (__titleValue != null) {
                    hash = 31 * hash + __titleValue.hashCode();
                }
                return hash;
            }

            private int __shallowHashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__requiredNameValue != null) {
                    hash = 31 * hash + System.identityHashCode(__requiredNameValue);
                }
                if (__titleValue != null) {
                    hash = 31 * hash + System.identityHashCode(__titleValue);
                }
                return hash;
            }

            @Override
            public int __hashCode(boolean shallow) {
                return shallow ? __shallowHashCode() : hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null || !(obj instanceof Implementor)) {
                    return false;
                }
                Implementor __other = (Implementor)obj;
                if (__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false;
                }
                boolean __requiredNameLoaded = __requiredNameValue != null;
                if (__requiredNameLoaded != __other.__isLoaded(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false;
                }
                if (__requiredNameLoaded && !Objects.equals(__requiredNameValue, __other.requiredName())) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_TITLE)) != __other.__isVisible(PropId.byIndex(SLOT_TITLE))) {
                    return false;
                }
                boolean __titleLoaded = __titleValue != null;
                if (__titleLoaded != __other.__isLoaded(PropId.byIndex(SLOT_TITLE))) {
                    return false;
                }
                if (__titleLoaded && !Objects.equals(__titleValue, __other.title())) {
                    return false;
                }
                return true;
            }

            private boolean __shallowEquals(Object obj) {
                if (obj == null || !(obj instanceof Implementor)) {
                    return false;
                }
                Implementor __other = (Implementor)obj;
                if (__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false;
                }
                boolean __requiredNameLoaded = __requiredNameValue != null;
                if (__requiredNameLoaded != __other.__isLoaded(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false;
                }
                if (__requiredNameLoaded && __requiredNameValue != __other.requiredName()) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_TITLE)) != __other.__isVisible(PropId.byIndex(SLOT_TITLE))) {
                    return false;
                }
                boolean __titleLoaded = __titleValue != null;
                if (__titleLoaded != __other.__isLoaded(PropId.byIndex(SLOT_TITLE))) {
                    return false;
                }
                if (__titleLoaded && __titleValue != __other.title()) {
                    return false;
                }
                return true;
            }

            @Override
            public boolean __equals(Object obj, boolean shallow) {
                return shallow ? __shallowEquals(obj) : equals(obj);
            }

            @Override
            public String toString() {
                return ImmutableObjects.toString(this);
            }
        }

        @GeneratedBy(
                type = ValidatedDraftModel.class
        )
        private static class DraftImpl extends Implementor implements DraftSpi, ValidatedDraftModelDraft {
            private static final java.util.regex.Pattern __TITLE_PATTER = java.util.regex.Pattern.compile("[A-Z][a-z]+", 0);

            private static final Validator<String> __TITLE_ID_CARD_VALIDATOR_1180256938 = 
                new Validator<>(IdCard.class, "invalid id card", ValidatedDraftModel.class, PropId.byIndex(SLOT_TITLE));

            private DraftContext __ctx;

            private Impl __base;

            private Impl __modified;

            private boolean __resolving;

            private ValidatedDraftModel __resolved;

            DraftImpl(DraftContext ctx, ValidatedDraftModel base) {
                __ctx = ctx;
                if (base != null) {
                    __base = (Impl)base;
                }
                else {
                    __modified = new Impl();
                }
            }

            @Override
            public boolean __isLoaded(PropId prop) {
                return (__modified!= null ? __modified : __base).__isLoaded(prop);
            }

            @Override
            public boolean __isLoaded(String prop) {
                return (__modified!= null ? __modified : __base).__isLoaded(prop);
            }

            @Override
            public boolean __isVisible(PropId prop) {
                return (__modified!= null ? __modified : __base).__isVisible(prop);
            }

            @Override
            public boolean __isVisible(String prop) {
                return (__modified!= null ? __modified : __base).__isVisible(prop);
            }

            @Override
            public int hashCode() {
                return (__modified!= null ? __modified : __base).hashCode();
            }

            @Override
            public int __hashCode(boolean shallow) {
                return (__modified!= null ? __modified : __base).__hashCode(shallow);
            }

            @Override
            public boolean equals(Object obj) {
                return (__modified!= null ? __modified : __base).equals(obj);
            }

            @Override
            public boolean __equals(Object obj, boolean shallow) {
                return (__modified!= null ? __modified : __base).__equals(obj, shallow);
            }

            @Override
            public String toString() {
                return ImmutableObjects.toString(this);
            }

            @Override
            @JsonIgnore
            public String requiredName() {
                return (__modified!= null ? __modified : __base).requiredName();
            }

            @Override
            public ValidatedDraftModelDraft setRequiredName(String requiredName) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (requiredName == null) {
                    throw new IllegalArgumentException(
                        "'requiredName' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__requiredNameValue = requiredName;
                return this;
            }

            @Override
            @JsonIgnore
            public String title() {
                return (__modified!= null ? __modified : __base).title();
            }

            @Override
            public ValidatedDraftModelDraft setTitle(String title) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (title == null) {
                    throw new IllegalArgumentException(
                        "'title' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                if (title.trim().isEmpty()) {
                }
                if (title.length() < 2) {
                }
                if (title.length() > 8) {
                }
                if (!__TITLE_PATTER.matcher(title).matches()) {
                }
                __TITLE_ID_CARD_VALIDATOR_1180256938.validate(title);
                Impl __tmpModified = __modified();
                __tmpModified.__titleValue = title;
                return this;
            }

            @SuppressWarnings("all")
            @Override
            public void __set(PropId prop, Object value) {
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		__set(prop.asName(), value);
                    return;
                    case SLOT_REQUIRED_NAME:
                    		setRequiredName((String)value);break;
                    case SLOT_TITLE:
                    		setTitle((String)value);break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            @SuppressWarnings("all")
            @Override
            public void __set(String prop, Object value) {
                switch (prop) {
                    case "requiredName":
                    		setRequiredName((String)value);break;
                    case "title":
                    		setTitle((String)value);break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\"");
                }
            }

            @Override
            public void __show(PropId prop, boolean visible) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                Visibility __visibility = (__modified!= null ? __modified : __base).__visibility;
                if (__visibility == null) {
                    if (visible) {
                        return;
                    }
                    __modified().__visibility = __visibility = Visibility.of(2);
                }
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		__show(prop.asName(), visible);
                    return;
                    case SLOT_REQUIRED_NAME:
                    		__visibility.show(SLOT_REQUIRED_NAME, visible);break;
                    case SLOT_TITLE:
                    		__visibility.show(SLOT_TITLE, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property id for \"demo.ValidatedDraftModel\": \"" + 
                                prop + 
                                "\",it does not exists"
                            );
                }
            }

            @Override
            public void __show(String prop, boolean visible) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                Visibility __visibility = (__modified!= null ? __modified : __base).__visibility;
                if (__visibility == null) {
                    if (visible) {
                        return;
                    }
                    __modified().__visibility = __visibility = Visibility.of(2);
                }
                switch (prop) {
                    case "requiredName":
                    		__visibility.show(SLOT_REQUIRED_NAME, visible);break;
                    case "title":
                    		__visibility.show(SLOT_TITLE, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property name for \"demo.ValidatedDraftModel\": \"" + 
                                prop + 
                                "\",it does not exists"
                            );
                }
            }

            @Override
            public void __unload(PropId prop) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		__unload(prop.asName());
                    return;
                    case SLOT_REQUIRED_NAME:
                    		__modified().__requiredNameValue = null;break;
                    case SLOT_TITLE:
                    		__modified().__titleValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.ValidatedDraftModel\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
                }
            }

            @Override
            public void __unload(String prop) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                switch (prop) {
                    case "requiredName":
                    		__modified().__requiredNameValue = null;break;
                    case "title":
                    		__modified().__titleValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ValidatedDraftModel\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
                }
            }

            @Override
            public DraftContext __draftContext() {
                return __ctx;
            }

            @Override
            public Object __resolve() {
                if (__resolved != null) {
                    return __resolved;
                }
                if (__resolving) {
                    throw new CircularReferenceException();
                }
                __resolving = true;
                try {
                    Implementor base = __base;
                    Impl __tmpModified = __modified;
                    if (__base != null && __tmpModified == null) {
                        this.__resolved = base;
                        return base;
                    }
                    this.__resolved = __tmpModified;
                    return __tmpModified;
                }
                finally {
                    __resolving = false;
                }
            }

            @Override
            public boolean __isResolved() {
                return __resolved != null;
            }

            Impl __modified() {
                Impl __tmpModified = __modified;
                if (__tmpModified == null) {
                    __tmpModified = __base.clone();
                    __modified = __tmpModified;
                }
                return __tmpModified;
            }
        }
    }

    @GeneratedBy(
            type = ValidatedDraftModel.class
    )
    class Builder {
        private final Producer.DraftImpl __draft;

        public Builder() {
            this(null);
        }

        public Builder(@Nullable ValidatedDraftModel base) {
            __draft = new Producer.DraftImpl(null, base);
        }

        public Builder requiredName(@NonNull String requiredName) {
            if (requiredName != null) {
                __draft.setRequiredName(requiredName);
            }
            return this;
        }

        @NotBlank(
                message = "title must not be blank"
        )
        @Size(
                min = 2,
                max = 8,
                message = "title size"
        )
        @Pattern(
                regexp = "[A-Z][a-z]+",
                message = "title pattern"
        )
        @IdCard(
                message = "invalid id card"
        )
        public Builder title(@NonNull String title) {
            if (title != null) {
                __draft.setTitle(title);
            }
            return this;
        }

        public ValidatedDraftModel build() {
            return (ValidatedDraftModel)__draft.__modified();
        }
    }
}
