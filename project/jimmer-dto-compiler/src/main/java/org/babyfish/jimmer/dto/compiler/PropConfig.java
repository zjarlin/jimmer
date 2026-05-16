package org.babyfish.jimmer.dto.compiler;

import site.addzero.lsi.dto.LsiDtoBaseProp;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PropConfig<P extends LsiDtoBaseProp> {

    @Nullable
    Predicate getPredicate();

    List<OrderItem<P>> getOrderItems();

    @Nullable
    String getFilterClassName();

    @Nullable
    String getRecursionClassName();

    String getFetchType();

    int getLimit();

    int getOffset();

    int getBatch();

    int getDepth();

    interface Predicate {
        interface And extends Predicate {
            List<Predicate> getPredicates();
        }

        interface Or extends Predicate {
            List<Predicate> getPredicates();
        }

        interface Cmp<P extends LsiDtoBaseProp> extends Predicate {
            List<PathNode<P>> getPath();
            String getOperator();
            Object getValue();
        }

        interface Nullity<P extends LsiDtoBaseProp> extends Predicate {
            List<PathNode<P>> getPath();
            boolean isNegative();
        }
    }

    interface OrderItem<P extends LsiDtoBaseProp> {
        List<PathNode<P>> getPath();
        boolean isDesc();
    }
    
    interface PathNode<P extends LsiDtoBaseProp> {
        P getProp();
        boolean isAssociatedId();
    }
}
