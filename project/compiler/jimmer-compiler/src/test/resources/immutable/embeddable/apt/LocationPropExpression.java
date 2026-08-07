package demo;

import java.lang.Integer;
import java.lang.Override;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.embedded.AbstractTypedEmbeddedPropExpression;
import org.babyfish.jimmer.sql.ast.impl.base.BaseTableOwner;

@GeneratedBy(
        type = Location.class
)
public class LocationPropExpression extends AbstractTypedEmbeddedPropExpression<Location> {
    public LocationPropExpression(PropExpression.Embedded<Location> raw) {
        super(raw);
    }

    public LocationPropExpression(LocationPropExpression base, BaseTableOwner baseTableOwner) {
        super(base, baseTableOwner);
    }

    /**
     * 城市名称。
     */
    public PropExpression.Str city() {
        return __get(LocationProps.CITY.unwrap());
    }

    /**
     * 邮政编码。
     */
    public PropExpression.Num<Integer> zipCode() {
        return __get(LocationProps.ZIP_CODE.unwrap());
    }

    @Override
    public LocationPropExpression __baseTableOwner(BaseTableOwner baseTableOwner) {
        return new LocationPropExpression(this, baseTableOwner);
    }
}
