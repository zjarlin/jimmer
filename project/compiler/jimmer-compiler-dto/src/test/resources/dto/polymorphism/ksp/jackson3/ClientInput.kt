package demo.dto

import com.fasterxml.jackson.`annotation`.JsonSubTypes
import com.fasterxml.jackson.`annotation`.JsonTypeInfo
import com.fasterxml.jackson.`annotation`.JsonTypeName
import demo.Client
import demo.ClientDraft
import demo.OrganizationDraft
import demo.PersonDraft
import demo.`by`
import java.lang.IllegalArgumentException
import java.util.Objects
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.jvm.JvmField
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.`impl`.util.DtoPropAccessor
import org.babyfish.jimmer.`internal`.FixedInputField
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.`internal`.GeneratedPolymorphicDtoBranch
import org.babyfish.jimmer.kt.new
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.babyfish.jimmer.sql.fetcher.DtoMetadata
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import tools.jackson.databind.`annotation`.JsonDeserialize
import tools.jackson.databind.`annotation`.JsonPOJOBuilder

@GeneratedBy(file = "fixture/src/main/dto/demo/Client.dto", prompt = "The current DTO type is mutable. If you need to make it immutable, please remove the ksp argument `jimmer.dto.mutable`")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = ClientInput.Default::class,
)
@JsonSubTypes(value = [JsonSubTypes.Type(value = ClientInput.Organization::class),
JsonSubTypes.Type(value = ClientInput.Person::class)])
public interface ClientInput : Input<Client> {
    public val id: Long

    public val kind: String

    public val name: String

    @GeneratedBy
    public companion object {
        @JvmField
        public val METADATA: DtoMetadata<Client, ClientInput> = 
            DtoMetadata<Client, ClientInput>(
                ClientInput::class.java,
                newFetcher(Client::class).by {
                    type()
                    name()
                    forType(demo.Organization::class) {
                        taxCode()
                    }
                    forType(demo.Person::class) {
                        firstName()
                    }
                },
                { base ->
                    val actualType = (base as ImmutableSpi).__type().javaClass
                    when (actualType) {
                        demo.Organization::class.java -> Organization(base as demo.Organization)
                        demo.Person::class.java -> Person(base as demo.Person)
                        else -> Default(base)
                    }
                }
            )
    }

    @GeneratedPolymorphicDtoBranch(
        value = ClientInput::class,
        order = 0,
    )
    @GeneratedBy
    @JsonDeserialize(builder = Default.Builder::class)
    public class Default(
        @FixedInputField
        override var id: Long,
        @FixedInputField
        override var kind: String,
        @FixedInputField
        override var name: String,
    ) : ClientInput {
        public constructor(base: Client) : this(
            base.id, 
            KIND_ACCESSOR.get<String>(base), 
            base.name)

        override fun toEntity(): Client {
            if (kind == ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("ORG")) {
                return new(demo.Organization::class).by {
                    toEntityImpl(this)
                }
            }
            if (kind == ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("Person")) {
                return new(demo.Person::class).by {
                    toEntityImpl(this)
                }
            }
            throw IllegalArgumentException("Illegal discriminator value \"" + kind + "\" for polymorphic input DTO branch \"demo.dto.ClientInput.Default\"")
        }

        public fun toEntity(block: ClientDraft.() -> Unit): Client {
            if (kind == ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("ORG")) {
                return new(demo.Organization::class).by {
                    toEntityImpl(this)
                    block(this)
                }
            }
            if (kind == ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("Person")) {
                return new(demo.Person::class).by {
                    toEntityImpl(this)
                    block(this)
                }
            }
            throw IllegalArgumentException("Illegal discriminator value \"" + kind + "\" for polymorphic input DTO branch \"demo.dto.ClientInput.Default\"")
        }

        internal fun __applyTo(_draft: ClientDraft) {
            _draft.id = id
            _draft.name = name
        }

        /**
         * Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco
         */
        private fun toEntityImpl(_draft: ClientDraft) {
            this.__applyTo(_draft)
        }

        public fun copy(
            id: Long = this.id,
            kind: String = this.kind,
            name: String = this.name,
        ): Default = Default(id, kind, name)

        public override fun hashCode(): Int {
            var _hash = Objects.hashCode(this.id)
            _hash = _hash * 31 + Objects.hashCode(this.kind)
            _hash = _hash * 31 + Objects.hashCode(this.name)
            return _hash
        }

        public override fun equals(other: Any?): Boolean {
            if (other == null || this::class != other::class) {
                return false
            }
            val _other = other as Default
            if (!Objects.equals(this.id, _other.id)) {
                return false
            }
            if (!Objects.equals(this.kind, _other.kind)) {
                return false
            }
            if (!Objects.equals(this.name, _other.name)) {
                return false
            }
            return true
        }

        public override fun toString(): String {
            val builder = StringBuilder()
            builder.append("ClientInput.Default").append('(')
            builder.append("").append("id=").append(id)
            builder.append(", ").append("kind=").append(kind)
            builder.append(", ").append("name=").append(name)
            builder.append(')')
            return builder.toString()
        }

        @GeneratedBy
        public companion object {
            private val KIND_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                        true,
                        intArrayOf(ClientDraft.`$`.SLOT_TYPE)
                    )
        }

        @GeneratedBy
        @JsonPOJOBuilder(withPrefix = "")
        public class Builder {
            private var id: Long? = null

            private var kind: String? = null

            private var name: String? = null

            public fun id(id: Long): Builder {
                this.id = id
                return this
            }

            public fun kind(kind: String): Builder {
                this.kind = kind
                return this
            }

            public fun name(name: String): Builder {
                this.name = name
                return this
            }

            public fun build(): Default = Default(
                id ?: throw Input.unknownNonNullProperty(Default::class.java, "id"),
                kind ?: throw Input.unknownNonNullProperty(Default::class.java, "kind"),
                name ?: throw Input.unknownNonNullProperty(Default::class.java, "name"),
            )
        }
    }

    @GeneratedPolymorphicDtoBranch(
        value = ClientInput::class,
        order = 1,
    )
    @GeneratedBy
    @JsonTypeName("ORG")
    @JsonDeserialize(builder = Organization.Builder::class)
    public class Organization(
        @FixedInputField
        override var id: Long,
        @FixedInputField
        override var kind: String,
        @FixedInputField
        override var name: String,
        @FixedInputField
        public var taxCode: String,
    ) : ClientInput {
        public constructor(base: demo.Organization) : this(
            base.id, 
            KIND_ACCESSOR.get<String>(base), 
            base.name, 
            base.taxCode)

        override fun toEntity(): demo.Organization = new(demo.Organization::class).by(null, false, this@Organization::toEntityImpl)

        public fun toEntity(block: OrganizationDraft.() -> Unit): demo.Organization = new(demo.Organization::class).by {
            toEntityImpl(this)
            block(this)
        }

        internal fun __applyTo(_draft: OrganizationDraft) {
            if (kind != ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("ORG")) {
                throw IllegalArgumentException("Discriminator value \"" + kind + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Organization\" whose entity type is \"demo.Organization\"")
            }
            _draft.id = id
            _draft.name = name
            _draft.taxCode = taxCode
        }

        /**
         * Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco
         */
        private fun toEntityImpl(_draft: OrganizationDraft) {
            this.__applyTo(_draft)
        }

        public fun copy(
            id: Long = this.id,
            kind: String = this.kind,
            name: String = this.name,
            taxCode: String = this.taxCode,
        ): Organization = Organization(id, kind, name, taxCode)

        public override fun hashCode(): Int {
            var _hash = Objects.hashCode(this.id)
            _hash = _hash * 31 + Objects.hashCode(this.kind)
            _hash = _hash * 31 + Objects.hashCode(this.name)
            _hash = _hash * 31 + Objects.hashCode(this.taxCode)
            return _hash
        }

        public override fun equals(other: Any?): Boolean {
            if (other == null || this::class != other::class) {
                return false
            }
            val _other = other as Organization
            if (!Objects.equals(this.id, _other.id)) {
                return false
            }
            if (!Objects.equals(this.kind, _other.kind)) {
                return false
            }
            if (!Objects.equals(this.name, _other.name)) {
                return false
            }
            if (!Objects.equals(this.taxCode, _other.taxCode)) {
                return false
            }
            return true
        }

        public override fun toString(): String {
            val builder = StringBuilder()
            builder.append("ClientInput.Organization").append('(')
            builder.append("").append("id=").append(id)
            builder.append(", ").append("kind=").append(kind)
            builder.append(", ").append("name=").append(name)
            builder.append(", ").append("taxCode=").append(taxCode)
            builder.append(')')
            return builder.toString()
        }

        @GeneratedBy
        public companion object {
            private val KIND_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                        true,
                        intArrayOf(OrganizationDraft.`$`.SLOT_TYPE)
                    )
        }

        @GeneratedBy
        @JsonPOJOBuilder(withPrefix = "")
        public class Builder {
            private var id: Long? = null

            private var kind: String? = null

            private var name: String? = null

            private var taxCode: String? = null

            public fun id(id: Long): Builder {
                this.id = id
                return this
            }

            public fun kind(kind: String): Builder {
                this.kind = kind
                return this
            }

            public fun name(name: String): Builder {
                this.name = name
                return this
            }

            public fun taxCode(taxCode: String): Builder {
                this.taxCode = taxCode
                return this
            }

            public fun build(): Organization = Organization(
                id ?: throw Input.unknownNonNullProperty(Organization::class.java, "id"),
                kind ?: throw Input.unknownNonNullProperty(Organization::class.java, "kind"),
                name ?: throw Input.unknownNonNullProperty(Organization::class.java, "name"),
                taxCode ?: throw Input.unknownNonNullProperty(Organization::class.java, "taxCode"),
            )
        }
    }

    @GeneratedPolymorphicDtoBranch(
        value = ClientInput::class,
        order = 2,
    )
    @GeneratedBy
    @JsonTypeName("Person")
    @JsonDeserialize(builder = Person.Builder::class)
    public class Person(
        @FixedInputField
        override var id: Long,
        @FixedInputField
        override var kind: String,
        @FixedInputField
        override var name: String,
        @FixedInputField
        public var firstName: String,
    ) : ClientInput {
        public constructor(base: demo.Person) : this(
            base.id, 
            KIND_ACCESSOR.get<String>(base), 
            base.name, 
            base.firstName)

        override fun toEntity(): demo.Person = new(demo.Person::class).by(null, false, this@Person::toEntityImpl)

        public fun toEntity(block: PersonDraft.() -> Unit): demo.Person = new(demo.Person::class).by {
            toEntityImpl(this)
            block(this)
        }

        internal fun __applyTo(_draft: PersonDraft) {
            if (kind != ImmutableType.get(Client::class.java).inheritanceInfo!!.discriminatorValue("Person")) {
                throw IllegalArgumentException("Discriminator value \"" + kind + "\" does not match polymorphic input DTO branch \"demo.dto.ClientInput.Person\" whose entity type is \"demo.Person\"")
            }
            _draft.id = id
            _draft.name = name
            _draft.firstName = firstName
        }

        /**
         * Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco
         */
        private fun toEntityImpl(_draft: PersonDraft) {
            this.__applyTo(_draft)
        }

        public fun copy(
            id: Long = this.id,
            kind: String = this.kind,
            name: String = this.name,
            firstName: String = this.firstName,
        ): Person = Person(id, kind, name, firstName)

        public override fun hashCode(): Int {
            var _hash = Objects.hashCode(this.id)
            _hash = _hash * 31 + Objects.hashCode(this.kind)
            _hash = _hash * 31 + Objects.hashCode(this.name)
            _hash = _hash * 31 + Objects.hashCode(this.firstName)
            return _hash
        }

        public override fun equals(other: Any?): Boolean {
            if (other == null || this::class != other::class) {
                return false
            }
            val _other = other as Person
            if (!Objects.equals(this.id, _other.id)) {
                return false
            }
            if (!Objects.equals(this.kind, _other.kind)) {
                return false
            }
            if (!Objects.equals(this.name, _other.name)) {
                return false
            }
            if (!Objects.equals(this.firstName, _other.firstName)) {
                return false
            }
            return true
        }

        public override fun toString(): String {
            val builder = StringBuilder()
            builder.append("ClientInput.Person").append('(')
            builder.append("").append("id=").append(id)
            builder.append(", ").append("kind=").append(kind)
            builder.append(", ").append("name=").append(name)
            builder.append(", ").append("firstName=").append(firstName)
            builder.append(')')
            return builder.toString()
        }

        @GeneratedBy
        public companion object {
            private val KIND_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                        true,
                        intArrayOf(PersonDraft.`$`.SLOT_TYPE)
                    )
        }

        @GeneratedBy
        @JsonPOJOBuilder(withPrefix = "")
        public class Builder {
            private var id: Long? = null

            private var kind: String? = null

            private var name: String? = null

            private var firstName: String? = null

            public fun id(id: Long): Builder {
                this.id = id
                return this
            }

            public fun kind(kind: String): Builder {
                this.kind = kind
                return this
            }

            public fun name(name: String): Builder {
                this.name = name
                return this
            }

            public fun firstName(firstName: String): Builder {
                this.firstName = firstName
                return this
            }

            public fun build(): Person = Person(
                id ?: throw Input.unknownNonNullProperty(Person::class.java, "id"),
                kind ?: throw Input.unknownNonNullProperty(Person::class.java, "kind"),
                name ?: throw Input.unknownNonNullProperty(Person::class.java, "name"),
                firstName ?: throw Input.unknownNonNullProperty(Person::class.java, "firstName"),
            )
        }
    }
}

@GeneratedBy(type = Client::class)
public fun Iterable<ClientInput>.toEntities(): List<Client> = map(ClientInput::toEntity)
