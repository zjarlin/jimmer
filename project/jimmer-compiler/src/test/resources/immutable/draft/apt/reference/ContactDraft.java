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
import org.babyfish.jimmer.CircularReferenceException;
import org.babyfish.jimmer.Draft;
import org.babyfish.jimmer.DraftConsumer;
import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.UnloadedException;
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
        type = Contact.class
)
public interface ContactDraft extends Contact, Draft {
    ContactDraft.Producer $ = Producer.INSTANCE;

    @OldChain
    ContactDraft setLabel(String label);

    @GeneratedBy(
            type = Contact.class
    )
    class Producer {
        static final Producer INSTANCE = new Producer();

        public static final int SLOT_LABEL = 0;

        public static final ImmutableType TYPE = ImmutableType
            .newBuilder(
                "0.11.2",
                Contact.class,
                Collections.emptyList(),
                (ctx, base) -> new DraftImpl(ctx, (Contact)base)
            )
            .add(SLOT_LABEL, "label", ImmutablePropCategory.SCALAR, String.class, false)
            .build();

        private Producer() {
        }

        public Contact produce(DraftConsumer<ContactDraft> block) {
            return (Contact)Internal.produce(TYPE, null, block);
        }

        public Contact produce(Contact base, DraftConsumer<ContactDraft> block) {
            return (Contact)Internal.produce(TYPE, base, block);
        }

        public Contact produce(boolean resolveImmediately, DraftConsumer<ContactDraft> block) {
            return (Contact)Internal.produce(TYPE, null, resolveImmediately, block);
        }

        public Contact produce(Contact base, boolean resolveImmediately,
                DraftConsumer<ContactDraft> block) {
            return (Contact)Internal.produce(TYPE, base, resolveImmediately, block);
        }

        /**
         * Class, not interface, for free-marker
         */
        @GeneratedBy(
                type = Contact.class
        )
        @JsonPropertyOrder({"dummyPropForJacksonError__", "label"})
        public abstract static class Implementor implements Contact, ImmutableSpi {
            @Override
            public final Object __get(PropId prop) {
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		return __get(prop.asName());
                    case SLOT_LABEL:
                    		return label();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\"");
                }
            }

            @Override
            public final Object __get(String prop) {
                switch (prop) {
                    case "label":
                    		return label();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\"");
                }
            }

            public final String getLabel() {
                return label();
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
                type = Contact.class
        )
        private static class Impl extends Implementor implements Cloneable, Serializable {
            private Visibility __visibility;

            String __labelValue;

            @Override
            @JsonIgnore
            public String label() {
                if (__labelValue == null) {
                    throw new UnloadedException(Contact.class, "label");
                }
                return __labelValue;
            }

            @Override
            public Impl clone() {
                try {
                    Impl copy = (Impl) super.clone();
                    Visibility originalVisibility = this.__visibility;
                    if (originalVisibility != null) {
                        Visibility newVisibility = Visibility.of(1);
                        for (int propId = 0; propId < 1; propId++) {
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
                    case SLOT_LABEL:
                    		return __labelValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\"");
                }
            }

            @Override
            public boolean __isLoaded(String prop) {
                switch (prop) {
                    case "label":
                    		return __labelValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\"");
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
                    case SLOT_LABEL:
                    		return __visibility.visible(SLOT_LABEL);
                    default: return true;
                }
            }

            @Override
            public boolean __isVisible(String prop) {
                if (__visibility == null) {
                    return true;
                }
                switch (prop) {
                    case "label":
                    		return __visibility.visible(SLOT_LABEL);
                    default: return true;
                }
            }

            @Override
            public int hashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__labelValue != null) {
                    hash = 31 * hash + __labelValue.hashCode();
                }
                return hash;
            }

            private int __shallowHashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__labelValue != null) {
                    hash = 31 * hash + System.identityHashCode(__labelValue);
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
                if (__isVisible(PropId.byIndex(SLOT_LABEL)) != __other.__isVisible(PropId.byIndex(SLOT_LABEL))) {
                    return false;
                }
                boolean __labelLoaded = __labelValue != null;
                if (__labelLoaded != __other.__isLoaded(PropId.byIndex(SLOT_LABEL))) {
                    return false;
                }
                if (__labelLoaded && !Objects.equals(__labelValue, __other.label())) {
                    return false;
                }
                return true;
            }

            private boolean __shallowEquals(Object obj) {
                if (obj == null || !(obj instanceof Implementor)) {
                    return false;
                }
                Implementor __other = (Implementor)obj;
                if (__isVisible(PropId.byIndex(SLOT_LABEL)) != __other.__isVisible(PropId.byIndex(SLOT_LABEL))) {
                    return false;
                }
                boolean __labelLoaded = __labelValue != null;
                if (__labelLoaded != __other.__isLoaded(PropId.byIndex(SLOT_LABEL))) {
                    return false;
                }
                if (__labelLoaded && __labelValue != __other.label()) {
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
                type = Contact.class
        )
        private static class DraftImpl extends Implementor implements DraftSpi, ContactDraft {
            private DraftContext __ctx;

            private Impl __base;

            private Impl __modified;

            private boolean __resolving;

            private Contact __resolved;

            DraftImpl(DraftContext ctx, Contact base) {
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
            public String label() {
                return (__modified!= null ? __modified : __base).label();
            }

            @Override
            public ContactDraft setLabel(String label) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (label == null) {
                    throw new IllegalArgumentException(
                        "'label' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__labelValue = label;
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
                    case SLOT_LABEL:
                    		setLabel((String)value);break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.Contact\": \"" + prop + "\"");
                }
            }

            @SuppressWarnings("all")
            @Override
            public void __set(String prop, Object value) {
                switch (prop) {
                    case "label":
                    		setLabel((String)value);break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\"");
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
                    __modified().__visibility = __visibility = Visibility.of(1);
                }
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		__show(prop.asName(), visible);
                    return;
                    case SLOT_LABEL:
                    		__visibility.show(SLOT_LABEL, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property id for \"demo.Contact\": \"" + 
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
                    __modified().__visibility = __visibility = Visibility.of(1);
                }
                switch (prop) {
                    case "label":
                    		__visibility.show(SLOT_LABEL, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property name for \"demo.Contact\": \"" + 
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
                    case SLOT_LABEL:
                    		__modified().__labelValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.Contact\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
                }
            }

            @Override
            public void __unload(String prop) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                switch (prop) {
                    case "label":
                    		__modified().__labelValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.Contact\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
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
            type = Contact.class
    )
    class Builder {
        private final Producer.DraftImpl __draft;

        public Builder() {
            this(null);
        }

        public Builder(@Nullable Contact base) {
            __draft = new Producer.DraftImpl(null, base);
        }

        public Builder label(@NonNull String label) {
            if (label != null) {
                __draft.setLabel(label);
            }
            return this;
        }

        public Contact build() {
            return (Contact)__draft.__modified();
        }
    }
}
