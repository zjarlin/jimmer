package demo

import com.fasterxml.jackson.`annotation`.JsonIgnore
import java.time.LocalDateTime
import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.Throwable
import kotlin.collections.Map
import kotlin.jvm.JvmStatic
import org.babyfish.jimmer.ClientException
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.error.CodeBasedRuntimeException

/**
 * Book errors.
 */
@GeneratedBy(type = BookErrorCode::class)
@ClientException(
    family = "BOOK",
    subTypes = [BookException.OutOfRange::class],
)
public abstract class BookException(
    message: String? = null,
    cause: Throwable? = null,
    /**
     * Created time
     */
    public val timestamp: LocalDateTime,
) : CodeBasedRuntimeException(message, cause) {
    @get:JsonIgnore
    public abstract val bookErrorCode: BookErrorCode

    override val fields: Map<String, Any?>
        get() = mapOf(
            "timestamp" to timestamp
        )

    public companion object {
        @JvmStatic
        public fun outOfRange(
            message: String? = null,
            cause: Throwable? = null,
            timestamp: LocalDateTime,
            min: Int,
            label: String,
            primitiveValues: IntArray,
            boxedValues: Array<Int>,
        ): OutOfRange = OutOfRange(
            message,
            cause,
            timestamp,
            min,
            label,
            primitiveValues,
            boxedValues
        )
    }

    /**
     * Out of range.
     */
    @ClientException(
        family = "BOOK",
        code = "OUT_OF_RANGE",
    )
    public class OutOfRange(
        message: String? = null,
        cause: Throwable? = null,
        timestamp: LocalDateTime,
        public val min: Int,
        public val label: String,
        public val primitiveValues: IntArray,
        public val boxedValues: Array<Int>,
    ) : BookException(message, cause, timestamp) {
        @get:JsonIgnore
        public override val bookErrorCode: BookErrorCode
            get() = BookErrorCode.OUT_OF_RANGE

        override val fields: Map<String, Any?>
            get() = mapOf(
                "timestamp" to timestamp,
                "min" to min,
                "label" to label,
                "primitiveValues" to primitiveValues,
                "boxedValues" to boxedValues
            )
    }
}
