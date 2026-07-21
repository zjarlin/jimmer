package demo;

import java.lang.Integer;
import java.lang.String;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TypedProp;

@GeneratedBy(
        type = Location.class
)
public interface LocationProps {
    TypedProp.Scalar<Location, String> CITY = 
        TypedProp.scalar(ImmutableType.get(Location.class).getProp("city"));

    TypedProp.Scalar<Location, Integer> ZIP_CODE = 
        TypedProp.scalar(ImmutableType.get(Location.class).getProp("zipCode"));
}
