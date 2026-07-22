package demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.Throwable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.babyfish.jimmer.ClientException;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Book errors.
 */
@GeneratedBy(
        type = BookErrorCode.class
)
@ClientException(
        family = "BOOK",
        subTypes = {BookException.OutOfRange.class}
)
public abstract class BookException extends CodeBasedRuntimeException {
    @NonNull
    final LocalDateTime timestamp;

    public BookException(String message, Throwable cause, @NonNull LocalDateTime timestamp) {
        super(message, cause);
        this.timestamp = timestamp;
    }

    /**
     * Created time
     */
    @NonNull
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @JsonIgnore
    public abstract BookErrorCode getBookErrorCode();

    public static OutOfRange outOfRange(@NonNull LocalDateTime timestamp, @NonNull int min,
            @NonNull String label, @NonNull int[] primitiveValues, @NonNull Integer[] boxedValues) {
        return new OutOfRange(
            null,
            null,
            timestamp,
            min,
            label,
            primitiveValues,
            boxedValues
        );
    }

    public static OutOfRange outOfRange(@NonNull String message, @NonNull LocalDateTime timestamp,
            @NonNull int min, @NonNull String label, @NonNull int[] primitiveValues,
            @NonNull Integer[] boxedValues) {
        return new OutOfRange(
            message,
            null,
            timestamp,
            min,
            label,
            primitiveValues,
            boxedValues
        );
    }

    public static OutOfRange outOfRange(@NonNull String message, @Nullable Throwable cause,
            @NonNull LocalDateTime timestamp, @NonNull int min, @NonNull String label,
            @NonNull int[] primitiveValues, @NonNull Integer[] boxedValues) {
        return new OutOfRange(
            message,
            cause,
            timestamp,
            min,
            label,
            primitiveValues,
            boxedValues
        );
    }

    /**
     * Out of range.
     */
    @ClientException(
            family = "BOOK",
            code = "OUT_OF_RANGE"
    )
    public static class OutOfRange extends BookException {
        @NonNull
        final int min;

        @NonNull
        final String label;

        @NonNull
        final int[] primitiveValues;

        @NonNull
        final Integer[] boxedValues;

        public OutOfRange(String message, Throwable cause, @NonNull LocalDateTime timestamp,
                @NonNull int min, @NonNull String label, @NonNull int[] primitiveValues,
                @NonNull Integer[] boxedValues) {
            super(message, cause, timestamp);
            this.min = min;
            this.label = label;
            this.primitiveValues = primitiveValues;
            this.boxedValues = boxedValues;
        }

        @NonNull
        public int getMin() {
            return min;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        @NonNull
        public int[] getPrimitiveValues() {
            return primitiveValues;
        }

        @NonNull
        public Integer[] getBoxedValues() {
            return boxedValues;
        }

        @JsonIgnore
        @Override
        public BookErrorCode getBookErrorCode() {
            return BookErrorCode.OUT_OF_RANGE;
        }

        @Override
        public Map<String, Object> getFields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("timestamp", timestamp);
            fields.put("min", min);
            fields.put("label", label);
            fields.put("primitiveValues", primitiveValues);
            fields.put("boxedValues", boxedValues);
            return fields;
        }
    }
}
