package org.babyfish.jimmer.dto.compiler;

import site.addzero.lsi.dto.LsiDtoBaseProp;
import site.addzero.lsi.dto.LsiDtoBaseType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DtoTypeMapper {

    private DtoTypeMapper() {}

    public static <S extends LsiDtoBaseType, SP extends LsiDtoBaseProp, T extends LsiDtoBaseType, TP extends LsiDtoBaseProp>
    List<DtoType<T, TP>> mapTypes(
            Collection<DtoType<S, SP>> sourceTypes,
            Function<? super S, ? extends T> typeMapper,
            Function<? super SP, ? extends TP> propMapper
    ) {
        Mapper<S, SP, T, TP> mapper = new Mapper<>(typeMapper, propMapper);
        List<DtoType<T, TP>> targetTypes = new ArrayList<>(sourceTypes.size());
        for (DtoType<S, SP> sourceType : sourceTypes) {
            targetTypes.add(mapper.mapType(sourceType));
        }
        return targetTypes;
    }

    private static class Mapper<S extends LsiDtoBaseType, SP extends LsiDtoBaseProp, T extends LsiDtoBaseType, TP extends LsiDtoBaseProp> {

        private final Function<? super S, ? extends T> typeMapper;

        private final Function<? super SP, ? extends TP> propMapper;

        private final IdentityHashMap<DtoType<S, SP>, DtoType<T, TP>> typeCache = new IdentityHashMap<>();

        private final IdentityHashMap<SP, TP> propCache = new IdentityHashMap<>();

        private Mapper(
                Function<? super S, ? extends T> typeMapper,
                Function<? super SP, ? extends TP> propMapper
        ) {
            this.typeMapper = typeMapper;
            this.propMapper = propMapper;
        }

        private DtoType<T, TP> mapType(DtoType<S, SP> sourceType) {
            DtoType<T, TP> targetType = typeCache.get(sourceType);
            if (targetType != null) {
                return targetType;
            }
            targetType = new DtoType<>(
                    mapBaseType(sourceType.getBaseType()),
                    sourceType.getPackageName(),
                    sourceType.getModifiers(),
                    sourceType.getAnnotations(),
                    sourceType.getSuperInterfaces(),
                    sourceType.getName(),
                    sourceType.getDtoFile(),
                    sourceType.getDoc()
            );
            typeCache.put(sourceType, targetType);
            List<AbstractProp> props = new ArrayList<>(sourceType.getProps().size());
            for (AbstractProp prop : sourceType.getProps()) {
                props.add(mapProp(prop, targetType));
            }
            targetType.setProps(Collections.unmodifiableList(props));
            return targetType;
        }

        @SuppressWarnings("unchecked")
        private AbstractProp mapProp(AbstractProp sourceProp, DtoType<T, TP> ownerType) {
            if (sourceProp instanceof UserProp) {
                return sourceProp;
            }
            return mapDtoProp((DtoProp<S, SP>) sourceProp, ownerType);
        }

        private DtoProp<T, TP> mapDtoProp(DtoProp<S, SP> sourceProp, DtoType<T, TP> ownerType) {
            DtoPropImplementor implementor = (DtoPropImplementor) sourceProp;
            Map<String, TP> basePropMap = new LinkedHashMap<>();
            for (Map.Entry<String, SP> entry : sourceProp.getBasePropMap().entrySet()) {
                basePropMap.put(entry.getKey(), mapBaseProp(entry.getValue()));
            }
            DtoType<T, TP> targetType = null;
            if (sourceProp.getTargetType() != null) {
                targetType = sourceProp.isRecursive() ? ownerType : mapType(sourceProp.getTargetType());
            }
            return new DtoPropImpl<>(
                    basePropMap,
                    implementor.getBaseLine(),
                    implementor.getBaseColumn(),
                    sourceProp.getAlias(),
                    sourceProp.getAliasLine(),
                    sourceProp.getAliasColumn(),
                    mapConfig(sourceProp.getConfig()),
                    sourceProp.getAnnotations(),
                    sourceProp.getDoc(),
                    targetType,
                    sourceProp.getEnumType(),
                    implementor.getMandatory(),
                    implementor.getInputModifier(),
                    implementor.getFuncName(),
                    sourceProp.isRecursive(),
                    sourceProp.getLikeOptions()
            );
        }

        private T mapBaseType(S sourceType) {
            return typeMapper.apply(sourceType);
        }

        private TP mapBaseProp(SP sourceProp) {
            TP targetProp = propCache.get(sourceProp);
            if (targetProp != null) {
                return targetProp;
            }
            targetProp = propMapper.apply(sourceProp);
            propCache.put(sourceProp, targetProp);
            return targetProp;
        }

        private PropConfig<TP> mapConfig(PropConfig<SP> sourceConfig) {
            if (sourceConfig == null) {
                return null;
            }
            return new PropConfigImpl<>(
                    mapPredicate(sourceConfig.getPredicate()),
                    mapOrderItems(sourceConfig.getOrderItems()),
                    sourceConfig.getFilterClassName(),
                    sourceConfig.getRecursionClassName(),
                    sourceConfig.getFetchType(),
                    sourceConfig.getLimit(),
                    sourceConfig.getOffset(),
                    sourceConfig.getBatch(),
                    sourceConfig.getDepth()
            );
        }

        private List<PropConfig.OrderItem<TP>> mapOrderItems(List<PropConfig.OrderItem<SP>> sourceItems) {
            if (sourceItems.isEmpty()) {
                return Collections.emptyList();
            }
            List<PropConfig.OrderItem<TP>> targetItems = new ArrayList<>(sourceItems.size());
            for (PropConfig.OrderItem<SP> sourceItem : sourceItems) {
                targetItems.add(new OrderItemImpl<>(mapPath(sourceItem.getPath()), sourceItem.isDesc()));
            }
            return Collections.unmodifiableList(targetItems);
        }

        @SuppressWarnings("unchecked")
        private PropConfig.Predicate mapPredicate(PropConfig.Predicate sourcePredicate) {
            if (sourcePredicate == null) {
                return null;
            }
            if (sourcePredicate instanceof PropConfig.Predicate.And) {
                List<PropConfig.Predicate> predicates = new ArrayList<>();
                for (PropConfig.Predicate predicate : ((PropConfig.Predicate.And) sourcePredicate).getPredicates()) {
                    predicates.add(mapPredicate(predicate));
                }
                return new AndPredicateImpl(predicates);
            }
            if (sourcePredicate instanceof PropConfig.Predicate.Or) {
                List<PropConfig.Predicate> predicates = new ArrayList<>();
                for (PropConfig.Predicate predicate : ((PropConfig.Predicate.Or) sourcePredicate).getPredicates()) {
                    predicates.add(mapPredicate(predicate));
                }
                return new OrPredicateImpl(predicates);
            }
            if (sourcePredicate instanceof PropConfig.Predicate.Nullity<?>) {
                PropConfig.Predicate.Nullity<SP> predicate = (PropConfig.Predicate.Nullity<SP>) sourcePredicate;
                return new NullityPredicate<>(mapPath(predicate.getPath()), predicate.isNegative());
            }
            if (sourcePredicate instanceof PropConfig.Predicate.Cmp<?>) {
                PropConfig.Predicate.Cmp<SP> predicate = (PropConfig.Predicate.Cmp<SP>) sourcePredicate;
                return new CmpPredicate<>(mapPath(predicate.getPath()), predicate.getOperator(), predicate.getValue());
            }
            throw new IllegalArgumentException("Unsupported predicate type: " + sourcePredicate.getClass().getName());
        }

        private List<PropConfig.PathNode<TP>> mapPath(List<PropConfig.PathNode<SP>> sourcePath) {
            if (sourcePath.isEmpty()) {
                return Collections.emptyList();
            }
            List<PropConfig.PathNode<TP>> targetPath = new ArrayList<>(sourcePath.size());
            for (PropConfig.PathNode<SP> sourcePathNode : sourcePath) {
                targetPath.add(new PathNodeImpl<>(mapBaseProp(sourcePathNode.getProp()), sourcePathNode.isAssociatedId()));
            }
            return sourcePath.size() == 1 ?
                    Collections.singletonList(targetPath.get(0)) :
                    Collections.unmodifiableList(targetPath);
        }
    }

    private abstract static class CompositePredicate implements PropConfig.Predicate {

        private final List<PropConfig.Predicate> predicates;

        private CompositePredicate(List<PropConfig.Predicate> predicates) {
            this.predicates = predicates.size() == 1 ?
                    Collections.singletonList(predicates.get(0)) :
                    Collections.unmodifiableList(predicates);
        }

        public List<PropConfig.Predicate> getPredicates() {
            return predicates;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            boolean addSeparator = false;
            builder.append('(');
            for (PropConfig.Predicate predicate : predicates) {
                if (addSeparator) {
                    builder.append(separator());
                } else {
                    addSeparator = true;
                }
                builder.append(predicate);
            }
            builder.append(')');
            return builder.toString();
        }

        protected abstract String separator();
    }

    private static class AndPredicateImpl extends CompositePredicate implements PropConfig.Predicate.And {

        private AndPredicateImpl(List<PropConfig.Predicate> predicates) {
            super(predicates);
        }

        @Override
        public List<PropConfig.Predicate> getPredicates() {
            return super.getPredicates();
        }

        @Override
        protected String separator() {
            return " and ";
        }
    }

    private static class OrPredicateImpl extends CompositePredicate implements PropConfig.Predicate.Or {

        private OrPredicateImpl(List<PropConfig.Predicate> predicates) {
            super(predicates);
        }

        @Override
        public List<PropConfig.Predicate> getPredicates() {
            return super.getPredicates();
        }

        @Override
        protected String separator() {
            return " or ";
        }
    }

    private static class OrderItemImpl<P extends LsiDtoBaseProp> implements PropConfig.OrderItem<P> {

        private final List<PropConfig.PathNode<P>> path;

        private final boolean desc;

        private OrderItemImpl(List<PropConfig.PathNode<P>> path, boolean desc) {
            this.path = path;
            this.desc = desc;
        }

        @Override
        public List<PropConfig.PathNode<P>> getPath() {
            return path;
        }

        @Override
        public boolean isDesc() {
            return desc;
        }
    }

    private static class NullityPredicate<P extends LsiDtoBaseProp> implements PropConfig.Predicate.Nullity<P> {

        private final List<PropConfig.PathNode<P>> path;

        private final boolean negative;

        private NullityPredicate(List<PropConfig.PathNode<P>> path, boolean negative) {
            this.path = path;
            this.negative = negative;
        }

        @Override
        public List<PropConfig.PathNode<P>> getPath() {
            return path;
        }

        @Override
        public boolean isNegative() {
            return negative;
        }
    }

    private static class CmpPredicate<P extends LsiDtoBaseProp> implements PropConfig.Predicate.Cmp<P> {

        private final List<PropConfig.PathNode<P>> path;

        private final String operator;

        private final Object value;

        private CmpPredicate(List<PropConfig.PathNode<P>> path, String operator, Object value) {
            this.path = path;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public List<PropConfig.PathNode<P>> getPath() {
            return path;
        }

        @Override
        public String getOperator() {
            return operator;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    private static class PropConfigImpl<P extends LsiDtoBaseProp> implements PropConfig<P> {

        private final Predicate predicate;

        private final List<OrderItem<P>> orderItems;

        private final String filterClassName;

        private final String recursionClassName;

        private final String fetchType;

        private final int limit;

        private final int offset;

        private final int batch;

        private final int depth;

        private PropConfigImpl(
                Predicate predicate,
                List<OrderItem<P>> orderItems,
                String filterClassName,
                String recursionClassName,
                String fetchType,
                int limit,
                int offset,
                int batch,
                int depth
        ) {
            this.predicate = predicate;
            this.orderItems = orderItems;
            this.filterClassName = filterClassName;
            this.recursionClassName = recursionClassName;
            this.fetchType = fetchType;
            this.limit = limit;
            this.offset = offset;
            this.batch = batch;
            this.depth = depth;
        }

        @Override
        public Predicate getPredicate() {
            return predicate;
        }

        @Override
        public List<OrderItem<P>> getOrderItems() {
            return orderItems;
        }

        @Override
        public String getFilterClassName() {
            return filterClassName;
        }

        @Override
        public String getRecursionClassName() {
            return recursionClassName;
        }

        @Override
        public String getFetchType() {
            return fetchType;
        }

        @Override
        public int getLimit() {
            return limit;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public int getBatch() {
            return batch;
        }

        @Override
        public int getDepth() {
            return depth;
        }
    }

    private static class PathNodeImpl<P extends LsiDtoBaseProp> implements PropConfig.PathNode<P> {

        private final P prop;

        private final boolean associatedId;

        private PathNodeImpl(P prop, boolean associatedId) {
            this.prop = prop;
            this.associatedId = associatedId;
        }

        @Override
        public P getProp() {
            return prop;
        }

        @Override
        public boolean isAssociatedId() {
            return associatedId;
        }
    }
}
