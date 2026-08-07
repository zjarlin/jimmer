package demo.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import demo.Client;
import demo.ClientDraft;
import demo.ClientFetcher;
import demo.OrganizationDraft;
import demo.OrganizationFetcher;
import demo.PersonDraft;
import demo.PersonFetcher;
import java.lang.Class;
import java.lang.IllegalArgumentException;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.Objects;
import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.impl.util.DtoPropAccessor;
import org.babyfish.jimmer.internal.FixedInputField;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

@GeneratedBy(
        file = "fixture/src/main/dto/demo/Client.dto"
)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind",
        visible = true,
        defaultImpl = ClientInput.Default.class
)
@JsonSubTypes({
            @JsonSubTypes.Type(ClientInput.Organization.class),
            @JsonSubTypes.Type(ClientInput.Person.class)
        })
public interface ClientInput extends Input<Client> {
    DtoMetadata<Client, ClientInput> METADATA = 
        new DtoMetadata<Client, ClientInput>(
            ClientInput.class,
            ClientFetcher.$
                .type()
                .name()
                .forType(OrganizationFetcher.$
                    .taxCode()
                )
                .forType(PersonFetcher.$
                    .firstName()
                ),
                base -> {
                    Class<?> actualType = ((ImmutableSpi)base).__type().getJavaClass();
                    if (actualType == demo.Organization.class) {
                        return new Organization((demo.Organization)base);
                    }
                    if (actualType == demo.Person.class) {
                        return new Person((demo.Person)base);
                    }
                    return new Default(base);
                }
        );

        long getId();

        @NonNull
        String getKind();

        @NonNull
        String getName();

        @GeneratedPolymorphicDtoBranch(
                value = ClientInput.class,
                order = 0
        )
        @GeneratedBy
        @JsonDeserialize(
                builder = Default.Builder.class
        )
        final class Default implements ClientInput {
            private static final DtoPropAccessor KIND_ACCESSOR = new DtoPropAccessor(
                true,
                new int[] { ClientDraft.Producer.SLOT_TYPE }
            );

            @FixedInputField
            private Long id;

            @FixedInputField
            private String kind;

            @FixedInputField
            private String name;

            public Default() {
            }

            public Default(@NonNull Client base) {
                this.id = base.id();
                this.kind = KIND_ACCESSOR.get(base);
                this.name = base.name();
            }

            public long getId() {
                if (id == null) {
                    throw new IllegalStateException("The property \"id\" is not specified");
                }
                return id;
            }

            public void setId(long id) {
                this.id = id;
            }

            @NonNull
            public String getKind() {
                if (kind == null) {
                    throw new IllegalStateException("The property \"kind\" is not specified");
                }
                return kind;
            }

            public void setKind(@NonNull String kind) {
                this.kind = kind;
            }

            @NonNull
            public String getName() {
                if (name == null) {
                    throw new IllegalStateException("The property \"name\" is not specified");
                }
                return name;
            }

            public void setName(@NonNull String name) {
                this.name = name;
            }

            private void __applyTo(ClientDraft __draft) {
                __draft.setId(this.id);
                __draft.setName(this.name);
            }

            @Override
            public Client toEntity() {
                return toEntityById(null);
            }

            public Client toEntityById(@Nullable Long id) {
                if (Objects.equals(this.getKind(), ImmutableType.get(Client.class).getInheritanceInfo().discriminatorValue("ORG"))) {
                    return OrganizationDraft.$.produce(__draft -> {
                        this.__applyTo(__draft);
                        if (id != null) {
                            __draft.setId(id);
                        }
                    });
                }
                if (Objects.equals(this.getKind(), ImmutableType.get(Client.class).getInheritanceInfo().discriminatorValue("Person"))) {
                    return PersonDraft.$.produce(__draft -> {
                        this.__applyTo(__draft);
                        if (id != null) {
                            __draft.setId(id);
                        }
                    });
                }
                throw new IllegalArgumentException("Illegal discriminator value \"" + this.getKind() + "\" for polymorphic input DTO branch \"demo.dto.ClientInput.Default\"");
            }

            @Override
            public int hashCode() {
                int hash = Objects.hashCode(this.id);
                hash = hash * 31 + Objects.hashCode(this.kind);
                hash = hash * 31 + Objects.hashCode(this.name);
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || this.getClass() != o.getClass()) {
                    return false;
                }
                Default other = (Default) o;
                if (!Objects.equals(this.id, other.id)) {
                    return false;
                }
                if (!Objects.equals(this.kind, other.kind)) {
                    return false;
                }
                if (!Objects.equals(this.name, other.name)) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("ClientInput.Default").append('(');
                builder.append("").append("id=").append(id);
                builder.append(", ").append("kind=").append(kind);
                builder.append(", ").append("name=").append(name);
                builder.append(')');
                return builder.toString();
            }

            @JsonPOJOBuilder(
                    withPrefix = ""
            )
            public static class Builder {
                private Long id;

                private String kind;

                private String name;

                public Builder id(long id) {
                    this.id = Objects.requireNonNull(id, "The property \"id\" cannot be null");
                    return this;
                }

                public Builder kind(String kind) {
                    this.kind = Objects.requireNonNull(kind, "The property \"kind\" cannot be null");
                    return this;
                }

                public Builder name(String name) {
                    this.name = Objects.requireNonNull(name, "The property \"name\" cannot be null");
                    return this;
                }

                public Default build() {
                    Default _input = new Default();
                    if (id == null) {
                        throw Input.unknownNonNullProperty(Default.class, "id");
                    }
                    _input.setId(id);
                    if (kind == null) {
                        throw Input.unknownNonNullProperty(Default.class, "kind");
                    }
                    _input.setKind(kind);
                    if (name == null) {
                        throw Input.unknownNonNullProperty(Default.class, "name");
                    }
                    _input.setName(name);
                    return _input;
                }
            }
        }

        @GeneratedPolymorphicDtoBranch(
                value = ClientInput.class,
                order = 1
        )
        @GeneratedBy
        @JsonDeserialize(
                builder = Organization.Builder.class
        )
        @JsonTypeName("ORG")
        final class Organization implements ClientInput {
            private static final DtoPropAccessor KIND_ACCESSOR = new DtoPropAccessor(
                true,
                new int[] { OrganizationDraft.Producer.SLOT_TYPE }
            );

            @FixedInputField
            private Long id;

            @FixedInputField
            private String kind;

            @FixedInputField
            private String name;

            @FixedInputField
            private String taxCode;

            public Organization() {
            }

            public Organization(demo. @NonNull Organization base) {
                this.id = base.id();
                this.kind = KIND_ACCESSOR.get(base);
                this.name = base.name();
                this.taxCode = base.taxCode();
            }

            public long getId() {
                if (id == null) {
                    throw new IllegalStateException("The property \"id\" is not specified");
                }
                return id;
            }

            public void setId(long id) {
                this.id = id;
            }

            @NonNull
            public String getKind() {
                if (kind == null) {
                    throw new IllegalStateException("The property \"kind\" is not specified");
                }
                return kind;
            }

            public void setKind(@NonNull String kind) {
                this.kind = kind;
            }

            @NonNull
            public String getName() {
                if (name == null) {
                    throw new IllegalStateException("The property \"name\" is not specified");
                }
                return name;
            }

            public void setName(@NonNull String name) {
                this.name = name;
            }

            @NonNull
            public String getTaxCode() {
                if (taxCode == null) {
                    throw new IllegalStateException("The property \"taxCode\" is not specified");
                }
                return taxCode;
            }

            public void setTaxCode(@NonNull String taxCode) {
                this.taxCode = taxCode;
            }

            private void __applyTo(OrganizationDraft __draft) {
                __draft.setId(this.id);
                __draft.setName(this.name);
                __draft.setTaxCode(this.taxCode);
            }

            @Override
            public demo.Organization toEntity() {
                return toEntityById(null);
            }

            public demo.Organization toEntityById(@Nullable Long id) {
                if (!Objects.equals(this.getKind(), ImmutableType.get(Client.class).getInheritanceInfo().discriminatorValue("ORG"))) {
                    throw new IllegalArgumentException("Discriminator value \"" + this.getKind() + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Organization\" whose entity type is \"demo.Organization\"");
                }
                return OrganizationDraft.$.produce(__draft -> {
                    this.__applyTo(__draft);
                    if (id != null) {
                        __draft.setId(id);
                    }
                });
            }

            @Override
            public int hashCode() {
                int hash = Objects.hashCode(this.id);
                hash = hash * 31 + Objects.hashCode(this.kind);
                hash = hash * 31 + Objects.hashCode(this.name);
                hash = hash * 31 + Objects.hashCode(this.taxCode);
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || this.getClass() != o.getClass()) {
                    return false;
                }
                Organization other = (Organization) o;
                if (!Objects.equals(this.id, other.id)) {
                    return false;
                }
                if (!Objects.equals(this.kind, other.kind)) {
                    return false;
                }
                if (!Objects.equals(this.name, other.name)) {
                    return false;
                }
                if (!Objects.equals(this.taxCode, other.taxCode)) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("ClientInput.Organization").append('(');
                builder.append("").append("id=").append(id);
                builder.append(", ").append("kind=").append(kind);
                builder.append(", ").append("name=").append(name);
                builder.append(", ").append("taxCode=").append(taxCode);
                builder.append(')');
                return builder.toString();
            }

            @JsonPOJOBuilder(
                    withPrefix = ""
            )
            public static class Builder {
                private Long id;

                private String kind;

                private String name;

                private String taxCode;

                public Builder id(long id) {
                    this.id = Objects.requireNonNull(id, "The property \"id\" cannot be null");
                    return this;
                }

                public Builder kind(String kind) {
                    this.kind = Objects.requireNonNull(kind, "The property \"kind\" cannot be null");
                    return this;
                }

                public Builder name(String name) {
                    this.name = Objects.requireNonNull(name, "The property \"name\" cannot be null");
                    return this;
                }

                public Builder taxCode(String taxCode) {
                    this.taxCode = Objects.requireNonNull(taxCode, "The property \"taxCode\" cannot be null");
                    return this;
                }

                public Organization build() {
                    Organization _input = new Organization();
                    if (id == null) {
                        throw Input.unknownNonNullProperty(Organization.class, "id");
                    }
                    _input.setId(id);
                    if (kind == null) {
                        throw Input.unknownNonNullProperty(Organization.class, "kind");
                    }
                    _input.setKind(kind);
                    if (name == null) {
                        throw Input.unknownNonNullProperty(Organization.class, "name");
                    }
                    _input.setName(name);
                    if (taxCode == null) {
                        throw Input.unknownNonNullProperty(Organization.class, "taxCode");
                    }
                    _input.setTaxCode(taxCode);
                    return _input;
                }
            }
        }

        @GeneratedPolymorphicDtoBranch(
                value = ClientInput.class,
                order = 2
        )
        @GeneratedBy
        @JsonDeserialize(
                builder = Person.Builder.class
        )
        @JsonTypeName("Person")
        final class Person implements ClientInput {
            private static final DtoPropAccessor KIND_ACCESSOR = new DtoPropAccessor(
                true,
                new int[] { PersonDraft.Producer.SLOT_TYPE }
            );

            @FixedInputField
            private Long id;

            @FixedInputField
            private String kind;

            @FixedInputField
            private String name;

            @FixedInputField
            private String firstName;

            public Person() {
            }

            public Person(demo. @NonNull Person base) {
                this.id = base.id();
                this.kind = KIND_ACCESSOR.get(base);
                this.name = base.name();
                this.firstName = base.firstName();
            }

            public long getId() {
                if (id == null) {
                    throw new IllegalStateException("The property \"id\" is not specified");
                }
                return id;
            }

            public void setId(long id) {
                this.id = id;
            }

            @NonNull
            public String getKind() {
                if (kind == null) {
                    throw new IllegalStateException("The property \"kind\" is not specified");
                }
                return kind;
            }

            public void setKind(@NonNull String kind) {
                this.kind = kind;
            }

            @NonNull
            public String getName() {
                if (name == null) {
                    throw new IllegalStateException("The property \"name\" is not specified");
                }
                return name;
            }

            public void setName(@NonNull String name) {
                this.name = name;
            }

            @NonNull
            public String getFirstName() {
                if (firstName == null) {
                    throw new IllegalStateException("The property \"firstName\" is not specified");
                }
                return firstName;
            }

            public void setFirstName(@NonNull String firstName) {
                this.firstName = firstName;
            }

            private void __applyTo(PersonDraft __draft) {
                __draft.setId(this.id);
                __draft.setName(this.name);
                __draft.setFirstName(this.firstName);
            }

            @Override
            public demo.Person toEntity() {
                return toEntityById(null);
            }

            public demo.Person toEntityById(@Nullable Long id) {
                if (!Objects.equals(this.getKind(), ImmutableType.get(Client.class).getInheritanceInfo().discriminatorValue("Person"))) {
                    throw new IllegalArgumentException("Discriminator value \"" + this.getKind() + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Person\" whose entity type is \"demo.Person\"");
                }
                return PersonDraft.$.produce(__draft -> {
                    this.__applyTo(__draft);
                    if (id != null) {
                        __draft.setId(id);
                    }
                });
            }

            @Override
            public int hashCode() {
                int hash = Objects.hashCode(this.id);
                hash = hash * 31 + Objects.hashCode(this.kind);
                hash = hash * 31 + Objects.hashCode(this.name);
                hash = hash * 31 + Objects.hashCode(this.firstName);
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || this.getClass() != o.getClass()) {
                    return false;
                }
                Person other = (Person) o;
                if (!Objects.equals(this.id, other.id)) {
                    return false;
                }
                if (!Objects.equals(this.kind, other.kind)) {
                    return false;
                }
                if (!Objects.equals(this.name, other.name)) {
                    return false;
                }
                if (!Objects.equals(this.firstName, other.firstName)) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("ClientInput.Person").append('(');
                builder.append("").append("id=").append(id);
                builder.append(", ").append("kind=").append(kind);
                builder.append(", ").append("name=").append(name);
                builder.append(", ").append("firstName=").append(firstName);
                builder.append(')');
                return builder.toString();
            }

            @JsonPOJOBuilder(
                    withPrefix = ""
            )
            public static class Builder {
                private Long id;

                private String kind;

                private String name;

                private String firstName;

                public Builder id(long id) {
                    this.id = Objects.requireNonNull(id, "The property \"id\" cannot be null");
                    return this;
                }

                public Builder kind(String kind) {
                    this.kind = Objects.requireNonNull(kind, "The property \"kind\" cannot be null");
                    return this;
                }

                public Builder name(String name) {
                    this.name = Objects.requireNonNull(name, "The property \"name\" cannot be null");
                    return this;
                }

                public Builder firstName(String firstName) {
                    this.firstName = Objects.requireNonNull(firstName, "The property \"firstName\" cannot be null");
                    return this;
                }

                public Person build() {
                    Person _input = new Person();
                    if (id == null) {
                        throw Input.unknownNonNullProperty(Person.class, "id");
                    }
                    _input.setId(id);
                    if (kind == null) {
                        throw Input.unknownNonNullProperty(Person.class, "kind");
                    }
                    _input.setKind(kind);
                    if (name == null) {
                        throw Input.unknownNonNullProperty(Person.class, "name");
                    }
                    _input.setName(name);
                    if (firstName == null) {
                        throw Input.unknownNonNullProperty(Person.class, "firstName");
                    }
                    _input.setFirstName(firstName);
                    return _input;
                }
            }
        }
    }
