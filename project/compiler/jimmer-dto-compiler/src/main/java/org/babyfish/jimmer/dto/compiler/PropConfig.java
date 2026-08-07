package org.babyfish.jimmer.dto.compiler;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PropConfig<P> {

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

        interface Cmp<P> extends Predicate {
            List<PathNode<P>> getPath();
            String getOperator();
            Object getValue();
        }

        interface Nullity<P> extends Predicate {
            List<PathNode<P>> getPath();
            boolean isNegative();
        }
    }

    interface OrderItem<P> {
        List<PathNode<P>> getPath();
        boolean isDesc();
    }
    
    interface PathNode<P> {
        P getProp();
        String getPropName();
        boolean isAssociatedId();
    }
}
