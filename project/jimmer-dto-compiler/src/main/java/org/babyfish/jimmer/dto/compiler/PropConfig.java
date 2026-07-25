package org.babyfish.jimmer.dto.compiler;

import org.babyfish.jimmer.dto.compiler.spi.BaseProp;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PropConfig<P extends BaseProp> {

    @Nullable
    Predicate getPredicate();

    List<OrderItem<P>> getOrderItems();

    @Nullable
    ConfigTypeRef getFilterType();

    @Nullable
    ConfigTypeRef getRecursionType();

    String getFetchType();

    @Nullable
    Limit getLimit();

    @Nullable
    Integer getBatch();

    @Nullable
    Integer getDepth();

    interface Limit {
        int getValue();
        int getOffset();
    }

    interface Predicate {
        interface And extends Predicate {
            List<Predicate> getPredicates();
        }

        interface Or extends Predicate {
            List<Predicate> getPredicates();
        }

        interface Cmp<P extends BaseProp> extends Predicate {
            List<PathNode<P>> getPath();
            String getOperator();
            Object getValue();
        }

        interface Nullity<P extends BaseProp> extends Predicate {
            List<PathNode<P>> getPath();
            boolean isNegative();
        }
    }

    interface OrderItem<P extends BaseProp> {
        List<PathNode<P>> getPath();
        boolean isDesc();
    }
    
    interface PathNode<P extends BaseProp> {
        P getProp();
        boolean isAssociatedId();
    }
}
