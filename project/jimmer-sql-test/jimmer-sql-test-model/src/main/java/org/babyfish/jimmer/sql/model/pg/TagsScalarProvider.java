package org.babyfish.jimmer.sql.model.pg;

import org.babyfish.jimmer.sql.runtime.ScalarProvider;
import org.babyfish.jimmer.json.codec.JsonType;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec;

public class TagsScalarProvider implements ScalarProvider<List<String>, String> {
    @Override
    public List<String> toScalar(@NonNull String sqlValue) throws Exception {
        return defaultCodec().decode(sqlValue, JsonType.listOf(String.class));
    }

    @Override
    public String toSql(@NonNull List<String> scalarValue) throws Exception {
        return defaultCodec().encode(scalarValue);
    }

    @Override
    public boolean isJsonScalar() {
        return true;
    }
}
