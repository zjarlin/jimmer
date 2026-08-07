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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.babyfish.jimmer.runtime.NonSharedList;
import org.babyfish.jimmer.runtime.Visibility;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@GeneratedBy(
        type = ReferenceModel.class
)
public interface ReferenceModelDraft extends ReferenceModel, Draft {
    ReferenceModelDraft.Producer $ = Producer.INSTANCE;

    AddressDraft address();

    AddressDraft address(boolean autoCreate);

    @OldChain
    ReferenceModelDraft setAddress(Address address);

    @OldChain
    ReferenceModelDraft applyAddress(DraftConsumer<AddressDraft> block);

    @OldChain
    ReferenceModelDraft applyAddress(Address base, DraftConsumer<AddressDraft> block);

    ContactDraft contact();

    ContactDraft contact(boolean autoCreate);

    @OldChain
    ReferenceModelDraft setContact(Contact contact);

    @OldChain
    ReferenceModelDraft applyContact(DraftConsumer<ContactDraft> block);

    @OldChain
    ReferenceModelDraft applyContact(Contact base, DraftConsumer<ContactDraft> block);

    List<ContactDraft> previousContacts(boolean autoCreate);

    @OldChain
    ReferenceModelDraft setPreviousContacts(List<Contact> previousContacts);

    @OldChain
    ReferenceModelDraft addIntoPreviousContacts(DraftConsumer<ContactDraft> block);

    @OldChain
    ReferenceModelDraft addIntoPreviousContacts(Contact base, DraftConsumer<ContactDraft> block);

    List<String> aliases(boolean autoCreate);

    @OldChain
    ReferenceModelDraft setAliases(List<String> aliases);

    @GeneratedBy(
            type = ReferenceModel.class
    )
    class Producer {
        static final Producer INSTANCE = new Producer();

        public static final int SLOT_ADDRESS = 0;

        public static final int SLOT_CONTACT = 1;

        public static final int SLOT_PREVIOUS_CONTACTS = 2;

        public static final int SLOT_ALIASES = 3;

        public static final ImmutableType TYPE = ImmutableType
            .newBuilder(
                "0.11.6",
                ReferenceModel.class,
                Collections.emptyList(),
                (ctx, base) -> new DraftImpl(ctx, (ReferenceModel)base)
            )
            .add(SLOT_ADDRESS, "address", ImmutablePropCategory.REFERENCE, Address.class, false)
            .add(SLOT_CONTACT, "contact", ImmutablePropCategory.REFERENCE, Contact.class, false)
            .add(SLOT_PREVIOUS_CONTACTS, "previousContacts", ImmutablePropCategory.REFERENCE_LIST, Contact.class, false)
            .add(SLOT_ALIASES, "aliases", ImmutablePropCategory.SCALAR_LIST, String.class, false)
            .build();

        private Producer() {
        }

        public ReferenceModel produce(DraftConsumer<ReferenceModelDraft> block) {
            return (ReferenceModel)Internal.produce(TYPE, null, block);
        }

        public ReferenceModel produce(ReferenceModel base,
                DraftConsumer<ReferenceModelDraft> block) {
            return (ReferenceModel)Internal.produce(TYPE, base, block);
        }

        public ReferenceModel produce(boolean resolveImmediately,
                DraftConsumer<ReferenceModelDraft> block) {
            return (ReferenceModel)Internal.produce(TYPE, null, resolveImmediately, block);
        }

        public ReferenceModel produce(ReferenceModel base, boolean resolveImmediately,
                DraftConsumer<ReferenceModelDraft> block) {
            return (ReferenceModel)Internal.produce(TYPE, base, resolveImmediately, block);
        }

        /**
         * Class, not interface, for free-marker
         */
        @GeneratedBy(
                type = ReferenceModel.class
        )
        @JsonPropertyOrder({"dummyPropForJacksonError__", "address", "contact", "previousContacts", "aliases"})
        public abstract static class Implementor implements ReferenceModel, ImmutableSpi {
            @Override
            public final Object __get(PropId prop) {
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		return __get(prop.asName());
                    case SLOT_ADDRESS:
                    		return address();
                    case SLOT_CONTACT:
                    		return contact();
                    case SLOT_PREVIOUS_CONTACTS:
                    		return previousContacts();
                    case SLOT_ALIASES:
                    		return aliases();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\"");
                }
            }

            @Override
            public final Object __get(String prop) {
                switch (prop) {
                    case "address":
                    		return address();
                    case "contact":
                    		return contact();
                    case "previousContacts":
                    		return previousContacts();
                    case "aliases":
                    		return aliases();
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\"");
                }
            }

            public final Address getAddress() {
                return address();
            }

            public final Contact getContact() {
                return contact();
            }

            public final List<Contact> getPreviousContacts() {
                return previousContacts();
            }

            public final List<String> getAliases() {
                return aliases();
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
                type = ReferenceModel.class
        )
        private static class Impl extends Implementor implements Cloneable, Serializable {
            private Visibility __visibility;

            Address __addressValue;

            Contact __contactValue;

            NonSharedList<Contact> __previousContactsValue;

            NonSharedList<String> __aliasesValue;

            @Override
            @JsonIgnore
            public Address address() {
                if (__addressValue == null) {
                    throw new UnloadedException(ReferenceModel.class, "address");
                }
                return __addressValue;
            }

            @Override
            @JsonIgnore
            public Contact contact() {
                if (__contactValue == null) {
                    throw new UnloadedException(ReferenceModel.class, "contact");
                }
                return __contactValue;
            }

            @Override
            @JsonIgnore
            public List<Contact> previousContacts() {
                if (__previousContactsValue == null) {
                    throw new UnloadedException(ReferenceModel.class, "previousContacts");
                }
                return __previousContactsValue;
            }

            @Override
            @JsonIgnore
            public List<String> aliases() {
                if (__aliasesValue == null) {
                    throw new UnloadedException(ReferenceModel.class, "aliases");
                }
                return __aliasesValue;
            }

            @Override
            public Impl clone() {
                try {
                    Impl copy = (Impl) super.clone();
                    Visibility originalVisibility = this.__visibility;
                    if (originalVisibility != null) {
                        Visibility newVisibility = Visibility.of(4);
                        for (int propId = 0; propId < 4; propId++) {
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
                    case SLOT_ADDRESS:
                    		return __addressValue != null;
                    case SLOT_CONTACT:
                    		return __contactValue != null;
                    case SLOT_PREVIOUS_CONTACTS:
                    		return __previousContactsValue != null;
                    case SLOT_ALIASES:
                    		return __aliasesValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\"");
                }
            }

            @Override
            public boolean __isLoaded(String prop) {
                switch (prop) {
                    case "address":
                    		return __addressValue != null;
                    case "contact":
                    		return __contactValue != null;
                    case "previousContacts":
                    		return __previousContactsValue != null;
                    case "aliases":
                    		return __aliasesValue != null;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\"");
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
                    case SLOT_ADDRESS:
                    		return __visibility.visible(SLOT_ADDRESS);
                    case SLOT_CONTACT:
                    		return __visibility.visible(SLOT_CONTACT);
                    case SLOT_PREVIOUS_CONTACTS:
                    		return __visibility.visible(SLOT_PREVIOUS_CONTACTS);
                    case SLOT_ALIASES:
                    		return __visibility.visible(SLOT_ALIASES);
                    default: return true;
                }
            }

            @Override
            public boolean __isVisible(String prop) {
                if (__visibility == null) {
                    return true;
                }
                switch (prop) {
                    case "address":
                    		return __visibility.visible(SLOT_ADDRESS);
                    case "contact":
                    		return __visibility.visible(SLOT_CONTACT);
                    case "previousContacts":
                    		return __visibility.visible(SLOT_PREVIOUS_CONTACTS);
                    case "aliases":
                    		return __visibility.visible(SLOT_ALIASES);
                    default: return true;
                }
            }

            @Override
            public int hashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__addressValue != null) {
                    hash = 31 * hash + __addressValue.hashCode();
                }
                if (__contactValue != null) {
                    hash = 31 * hash + __contactValue.hashCode();
                }
                if (__previousContactsValue != null) {
                    hash = 31 * hash + __previousContactsValue.hashCode();
                }
                if (__aliasesValue != null) {
                    hash = 31 * hash + __aliasesValue.hashCode();
                }
                return hash;
            }

            private int __shallowHashCode() {
                int hash = __visibility != null ? __visibility.hashCode() : 0;
                if (__addressValue != null) {
                    hash = 31 * hash + System.identityHashCode(__addressValue);
                }
                if (__contactValue != null) {
                    hash = 31 * hash + System.identityHashCode(__contactValue);
                }
                if (__previousContactsValue != null) {
                    hash = 31 * hash + System.identityHashCode(__previousContactsValue);
                }
                if (__aliasesValue != null) {
                    hash = 31 * hash + System.identityHashCode(__aliasesValue);
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
                if (__isVisible(PropId.byIndex(SLOT_ADDRESS)) != __other.__isVisible(PropId.byIndex(SLOT_ADDRESS))) {
                    return false;
                }
                boolean __addressLoaded = __addressValue != null;
                if (__addressLoaded != __other.__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                    return false;
                }
                if (__addressLoaded && !Objects.equals(__addressValue, __other.address())) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_CONTACT)) != __other.__isVisible(PropId.byIndex(SLOT_CONTACT))) {
                    return false;
                }
                boolean __contactLoaded = __contactValue != null;
                if (__contactLoaded != __other.__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                    return false;
                }
                if (__contactLoaded && !Objects.equals(__contactValue, __other.contact())) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)) != __other.__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false;
                }
                boolean __previousContactsLoaded = __previousContactsValue != null;
                if (__previousContactsLoaded != __other.__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false;
                }
                if (__previousContactsLoaded && !Objects.equals(__previousContactsValue, __other.previousContacts())) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_ALIASES)) != __other.__isVisible(PropId.byIndex(SLOT_ALIASES))) {
                    return false;
                }
                boolean __aliasesLoaded = __aliasesValue != null;
                if (__aliasesLoaded != __other.__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                    return false;
                }
                if (__aliasesLoaded && !Objects.equals(__aliasesValue, __other.aliases())) {
                    return false;
                }
                return true;
            }

            private boolean __shallowEquals(Object obj) {
                if (obj == null || !(obj instanceof Implementor)) {
                    return false;
                }
                Implementor __other = (Implementor)obj;
                if (__isVisible(PropId.byIndex(SLOT_ADDRESS)) != __other.__isVisible(PropId.byIndex(SLOT_ADDRESS))) {
                    return false;
                }
                boolean __addressLoaded = __addressValue != null;
                if (__addressLoaded != __other.__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                    return false;
                }
                if (__addressLoaded && __addressValue != __other.address()) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_CONTACT)) != __other.__isVisible(PropId.byIndex(SLOT_CONTACT))) {
                    return false;
                }
                boolean __contactLoaded = __contactValue != null;
                if (__contactLoaded != __other.__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                    return false;
                }
                if (__contactLoaded && __contactValue != __other.contact()) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)) != __other.__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false;
                }
                boolean __previousContactsLoaded = __previousContactsValue != null;
                if (__previousContactsLoaded != __other.__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false;
                }
                if (__previousContactsLoaded && __previousContactsValue != __other.previousContacts()) {
                    return false;
                }
                if (__isVisible(PropId.byIndex(SLOT_ALIASES)) != __other.__isVisible(PropId.byIndex(SLOT_ALIASES))) {
                    return false;
                }
                boolean __aliasesLoaded = __aliasesValue != null;
                if (__aliasesLoaded != __other.__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                    return false;
                }
                if (__aliasesLoaded && __aliasesValue != __other.aliases()) {
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
                type = ReferenceModel.class
        )
        private static class DraftImpl extends Implementor implements DraftSpi, ReferenceModelDraft {
            private DraftContext __ctx;

            private Impl __base;

            private Impl __modified;

            private boolean __resolving;

            private ReferenceModel __resolved;

            DraftImpl(DraftContext ctx, ReferenceModel base) {
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
            public AddressDraft address() {
                return __ctx.toDraftObject((__modified!= null ? __modified : __base).address());
            }

            @Override
            public AddressDraft address(boolean autoCreate) {
                if (autoCreate && !__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                    setAddress(AddressDraft.$.produce(null, null));
                }
                return __ctx.toDraftObject((__modified!= null ? __modified : __base).address());
            }

            @Override
            public ReferenceModelDraft setAddress(Address address) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (address == null) {
                    throw new IllegalArgumentException(
                        "'address' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__addressValue = address;
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft applyAddress(DraftConsumer<AddressDraft> block) {
                applyAddress(null, block);
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft applyAddress(Address base,
                    DraftConsumer<AddressDraft> block) {
                setAddress(AddressDraft.$.produce(base, block));
                return this;
            }

            @Override
            @JsonIgnore
            public ContactDraft contact() {
                return __ctx.toDraftObject((__modified!= null ? __modified : __base).contact());
            }

            @Override
            public ContactDraft contact(boolean autoCreate) {
                if (autoCreate && !__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                    setContact(ContactDraft.$.produce(null, null));
                }
                return __ctx.toDraftObject((__modified!= null ? __modified : __base).contact());
            }

            @Override
            public ReferenceModelDraft setContact(Contact contact) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (contact == null) {
                    throw new IllegalArgumentException(
                        "'contact' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__contactValue = contact;
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft applyContact(DraftConsumer<ContactDraft> block) {
                applyContact(null, block);
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft applyContact(Contact base,
                    DraftConsumer<ContactDraft> block) {
                setContact(ContactDraft.$.produce(base, block));
                return this;
            }

            @Override
            @JsonIgnore
            public List<Contact> previousContacts() {
                return __ctx.toDraftList((__modified!= null ? __modified : __base).previousContacts(), Contact.class, true);
            }

            @Override
            public List<ContactDraft> previousContacts(boolean autoCreate) {
                if (autoCreate && !__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    setPreviousContacts(new ArrayList<>());
                }
                return __ctx.toDraftList((__modified!= null ? __modified : __base).previousContacts(), Contact.class, true);
            }

            @Override
            public ReferenceModelDraft setPreviousContacts(List<Contact> previousContacts) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (previousContacts == null) {
                    throw new IllegalArgumentException(
                        "'previousContacts' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__previousContactsValue = NonSharedList.of(__tmpModified.__previousContactsValue, previousContacts);
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft addIntoPreviousContacts(DraftConsumer<ContactDraft> block) {
                addIntoPreviousContacts(null, block);
                return this;
            }

            @OldChain
            @Override
            public ReferenceModelDraft addIntoPreviousContacts(Contact base,
                    DraftConsumer<ContactDraft> block) {
                previousContacts(true).add((ContactDraft)ContactDraft.$.produce(base, block));
                return this;
            }

            @Override
            @JsonIgnore
            public List<String> aliases() {
                return __ctx.toDraftList((__modified!= null ? __modified : __base).aliases(), String.class, false);
            }

            @Override
            public List<String> aliases(boolean autoCreate) {
                if (autoCreate && !__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                    setAliases(new ArrayList<>());
                }
                return __ctx.toDraftList((__modified!= null ? __modified : __base).aliases(), String.class, false);
            }

            @Override
            public ReferenceModelDraft setAliases(List<String> aliases) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                if (aliases == null) {
                    throw new IllegalArgumentException(
                        "'aliases' cannot be null, please specify non-null value or use nullable annotation to decorate this property"
                    );
                }
                Impl __tmpModified = __modified();
                __tmpModified.__aliasesValue = NonSharedList.of(__tmpModified.__aliasesValue, aliases);
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
                    case SLOT_ADDRESS:
                    		setAddress((Address)value);break;
                    case SLOT_CONTACT:
                    		setContact((Contact)value);break;
                    case SLOT_PREVIOUS_CONTACTS:
                    		setPreviousContacts((List<Contact>)value);break;
                    case SLOT_ALIASES:
                    		setAliases((List<String>)value);break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.ReferenceModel\": \"" + prop + "\"");
                }
            }

            @SuppressWarnings("all")
            @Override
            public void __set(String prop, Object value) {
                switch (prop) {
                    case "address":
                    		setAddress((Address)value);break;
                    case "contact":
                    		setContact((Contact)value);break;
                    case "previousContacts":
                    		setPreviousContacts((List<Contact>)value);break;
                    case "aliases":
                    		setAliases((List<String>)value);break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\"");
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
                    __modified().__visibility = __visibility = Visibility.of(4);
                }
                int __propIndex = prop.asIndex();
                switch (__propIndex) {
                    case -1:
                    		__show(prop.asName(), visible);
                    return;
                    case SLOT_ADDRESS:
                    		__visibility.show(SLOT_ADDRESS, visible);break;
                    case SLOT_CONTACT:
                    		__visibility.show(SLOT_CONTACT, visible);break;
                    case SLOT_PREVIOUS_CONTACTS:
                    		__visibility.show(SLOT_PREVIOUS_CONTACTS, visible);break;
                    case SLOT_ALIASES:
                    		__visibility.show(SLOT_ALIASES, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property id for \"demo.ReferenceModel\": \"" + 
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
                    __modified().__visibility = __visibility = Visibility.of(4);
                }
                switch (prop) {
                    case "address":
                    		__visibility.show(SLOT_ADDRESS, visible);break;
                    case "contact":
                    		__visibility.show(SLOT_CONTACT, visible);break;
                    case "previousContacts":
                    		__visibility.show(SLOT_PREVIOUS_CONTACTS, visible);break;
                    case "aliases":
                    		__visibility.show(SLOT_ALIASES, visible);break;
                    default: throw new IllegalArgumentException(
                                "Illegal property name for \"demo.ReferenceModel\": \"" + 
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
                    case SLOT_ADDRESS:
                    		__modified().__addressValue = null;break;
                    case SLOT_CONTACT:
                    		__modified().__contactValue = null;break;
                    case SLOT_PREVIOUS_CONTACTS:
                    		__modified().__previousContactsValue = null;break;
                    case SLOT_ALIASES:
                    		__modified().__aliasesValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property id for \"demo.ReferenceModel\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
                }
            }

            @Override
            public void __unload(String prop) {
                if (__resolved != null) {
                    throw new IllegalStateException("The current draft has been resolved so it cannot be modified");
                }
                switch (prop) {
                    case "address":
                    		__modified().__addressValue = null;break;
                    case "contact":
                    		__modified().__contactValue = null;break;
                    case "previousContacts":
                    		__modified().__previousContactsValue = null;break;
                    case "aliases":
                    		__modified().__aliasesValue = null;break;
                    default: throw new IllegalArgumentException("Illegal property name for \"demo.ReferenceModel\": \"" + prop + "\", it does not exist or its loaded state is not controllable");
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
                    if (__tmpModified == null) {
                        if (base.__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                            Address oldValue = base.address();
                            Address newValue = __ctx.resolveObject(oldValue);
                            if (oldValue != newValue) {
                                setAddress(newValue);
                            }
                        }
                        if (base.__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                            Contact oldValue = base.contact();
                            Contact newValue = __ctx.resolveObject(oldValue);
                            if (oldValue != newValue) {
                                setContact(newValue);
                            }
                        }
                        if (base.__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                            List<Contact> oldValue = base.previousContacts();
                            List<Contact> newValue = __ctx.resolveList(oldValue);
                            if (oldValue != newValue) {
                                setPreviousContacts(newValue);
                            }
                        }
                        if (base.__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                            List<String> oldValue = base.aliases();
                            List<String> newValue = __ctx.resolveList(oldValue);
                            if (oldValue != newValue) {
                                setAliases(newValue);
                            }
                        }
                        __tmpModified = __modified;
                    } else {
                        __tmpModified.__addressValue = __ctx.resolveObject(__tmpModified.__addressValue);
                        __tmpModified.__contactValue = __ctx.resolveObject(__tmpModified.__contactValue);
                        __tmpModified.__previousContactsValue = NonSharedList.of(__tmpModified.__previousContactsValue, __ctx.resolveList(__tmpModified.__previousContactsValue));
                        __tmpModified.__aliasesValue = NonSharedList.of(__tmpModified.__aliasesValue, __ctx.resolveList(__tmpModified.__aliasesValue));
                    }
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
            type = ReferenceModel.class
    )
    class Builder {
        private final Producer.DraftImpl __draft;

        public Builder() {
            this(null);
        }

        public Builder(@Nullable ReferenceModel base) {
            __draft = new Producer.DraftImpl(null, base);
        }

        public Builder address(@NonNull Address address) {
            if (address != null) {
                __draft.setAddress(address);
            }
            return this;
        }

        public Builder contact(@NonNull Contact contact) {
            if (contact != null) {
                __draft.setContact(contact);
            }
            return this;
        }

        public Builder previousContacts(@NonNull List<Contact> previousContacts) {
            if (previousContacts != null) {
                __draft.setPreviousContacts(previousContacts);
            }
            return this;
        }

        public Builder aliases(@NonNull List<String> aliases) {
            if (aliases != null) {
                __draft.setAliases(aliases);
            }
            return this;
        }

        public ReferenceModel build() {
            return (ReferenceModel)__draft.__modified();
        }
    }
}
