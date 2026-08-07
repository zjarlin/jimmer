package org.babyfish.jimmer.dto.compiler;

import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class PropConfigBuilder<T, P> {

    private final CompilerContext<T, P> ctx;

    private final P baseProp;

    private final String funcName;

    private final boolean recursive;

    private PropConfig.Predicate predicate;

    private List<PropConfig.OrderItem<P>> orderItems = Collections.emptyList();

    private ConfigTypeRef filterType;

    private ConfigTypeRef recursionType;

    private String fetchType = "AUTO";

    private PropConfig.Limit limit;

    private Integer batch;

    private Integer depth;

    private boolean modified;

    PropConfigBuilder(
            CompilerContext<T, P> ctx,
            P baseProp,
            String funcName,
            boolean recursive
    ) {
        this.ctx = ctx;
        this.baseProp = baseProp;
        this.funcName = funcName;
        this.recursive = recursive;
    }

    public void setPredicate(DtoParser.WhereContext where) {
        if (filterType != null) {
            throw ctx.exception(
                    where.start.getLine(),
                    where.start.getCharPositionInLine(),
                    "Cannot specify \"!where\" when \"!filter\" exists"
            );
        }
        if (!ctx.isBasePropAssociation(baseProp, true)) {
            throw ctx.exception(
                    where.start.getLine(),
                    where.start.getCharPositionInLine(),
                    "Cannot be specify \"!where\" when the property is not association"
            );
        }
        if (ctx.isBasePropReference(baseProp) && !ctx.isBasePropNullable(baseProp)) {
            throw ctx.exception(
                    where.start.getLine(),
                    where.start.getCharPositionInLine(),
                    "Cannot be specify \"!where\" when the property is non-null reference"
            );
        }
        this.predicate = createPredicate(where.predicate());
        this.modified = true;
    }

    void setOrderItems(DtoParser.OrderByContext orderBy) {
        if (filterType != null) {
            throw ctx.exception(
                    orderBy.start.getLine(),
                    orderBy.start.getCharPositionInLine(),
                    "Cannot specify \"!orderBy\" when \"!filter\" exists"
            );
        }
        if (!ctx.isBasePropAssociation(baseProp, true) || !ctx.isBasePropList(baseProp)) {
            throw ctx.exception(
                    orderBy.start.getLine(),
                    orderBy.start.getCharPositionInLine(),
                    "Cannot be specify \"!orderBy\" when the property is not associated list"
            );
        }
        List<DtoParser.OrderByItemContext> orderItems = orderBy.items;
        List<PropConfig.OrderItem<P>> items = new ArrayList<>(orderItems.size());
        for (DtoParser.OrderByItemContext item : orderItems) {
            Token modeToken = item.orderMode;
            String mode = modeToken != null ? modeToken.getText() : null;
            if (mode != null && !"asc".equals(mode) && !"desc".equals(mode)) {
                throw ctx.exception(
                        modeToken.getLine(),
                        modeToken.getCharPositionInLine(),
                        "The order mode is neither \"asc\" nor \"desc\""
                );
            }
            items.add(
                    new OrderItemImpl<>(
                            createPropPath(item.propPath()),
                            "desc".equals(mode)
                    )
            );
        }
        this.orderItems = Collections.unmodifiableList(items);
        this.modified = true;
    }

    void setFilterType(DtoParser.FilterContext filter) {
        if (predicate != null) {
            throw ctx.exception(
                    filter.start.getLine(),
                    filter.start.getCharPositionInLine(),
                    "Cannot specify \"!filter\" when \"!where\" exists"
            );
        }
        if (!orderItems.isEmpty()) {
            throw ctx.exception(
                    filter.start.getLine(),
                    filter.start.getCharPositionInLine(),
                    "Cannot specify \"!filter\" when \"!orderBy\" exists"
            );
        }
        if (!ctx.isBasePropAssociation(baseProp, true) || !ctx.isBasePropList(baseProp)) {
            throw ctx.exception(
                    filter.start.getLine(),
                    filter.start.getCharPositionInLine(),
                    "Cannot be specify \"!filter\" when the property is not associated list"
            );
        }
        String qualifiedName = filter
                .qualifiedName()
                .parts
                .stream()
                .map(Token::getText)
                .collect(Collectors.joining("."));
        Token start = filter.qualifiedName().start;
        this.filterType = new ConfigTypeRef(
                ctx.resolve(
                        qualifiedName,
                        start.getLine(),
                        start.getCharPositionInLine()
                ),
                start.getLine(),
                start.getCharPositionInLine() + 1
        );
        this.modified = true;
    }

    void setRecursionType(DtoParser.RecursionContext recursion) {
        if (depth != null) {
            throw ctx.exception(
                    recursion.start.getLine(),
                    recursion.start.getCharPositionInLine(),
                    "Cannot specify \"!recursion\" when \"!depth\" exists"
            );
        }
        if (!recursive) {
            throw ctx.exception(
                    recursion.start.getLine(),
                    recursion.start.getCharPositionInLine(),
                    "\"!recursion\" can only be applied for recursive property"
            );
        }
        String qualifiedName = recursion
                .qualifiedName()
                .parts
                .stream()
                .map(Token::getText)
                .collect(Collectors.joining("."));
        Token start = recursion.qualifiedName().start;
        this.recursionType = new ConfigTypeRef(
                ctx.resolve(
                        qualifiedName,
                        start.getLine(),
                        start.getCharPositionInLine()
                ),
                start.getLine(),
                start.getCharPositionInLine() + 1
        );
        this.modified = true;
    }

    void setFetchType(DtoParser.FetchTypeContext fetchType) {
        if (!ctx.isBasePropAssociation(baseProp, true) || ctx.isBasePropList(baseProp)) {
            throw ctx.exception(
                    fetchType.start.getLine(),
                    fetchType.start.getCharPositionInLine(),
                    "Cannot be specify \"!fetchType\" when the property is not associated reference"
            );
        }
        this.fetchType = fetchType.fetchMode.getText();
        switch (this.fetchType) {
            case "SELECT":
            case "JOIN_IF_NO_CACHE":
            case "JOIN_ALWAYS":
                break;
            default:
                throw ctx.exception(
                        fetchType.fetchMode.getLine(),
                        fetchType.fetchMode.getCharPositionInLine(),
                        "The fetch mode can only be \"SELECT\", \"JOIN_IF_NO_CACHE\" or \"JOIN_ALWAYS\""
                );
        }
        this.modified = true;
    }

    void setLimit(DtoParser.LimitContext limit) {
        if (!ctx.isBasePropAssociation(baseProp, true) || !ctx.isBasePropList(baseProp)) {
            throw ctx.exception(
                    limit.start.getLine(),
                    limit.start.getCharPositionInLine(),
                    "Cannot be specify \"!limit\" when the property is not associated list"
            );
        }
        int limitValue = Integer.parseInt(limit.limitArg.getText());
        if (limitValue < 1) {
            throw ctx.exception(
                    limit.limitArg.getLine(),
                    limit.limitArg.getCharPositionInLine(),
                    "The limit cannot be less than 1"
            );
        }
        int offsetValue = 0;
        if (limit.offsetArg != null) {
            offsetValue = Integer.parseInt(limit.offsetArg.getText());
            if (offsetValue < 0) {
                throw ctx.exception(
                        limit.offsetArg.getLine(),
                        limit.offsetArg.getCharPositionInLine(),
                        "The offset cannot be less than 0"
                );
            }
        }

        this.limit = new LimitImpl(limitValue, offsetValue);
        this.modified = true;
    }

    void setBatch(DtoParser.BatchContext batch) {
        int value = Integer.parseInt(batch.IntegerLiteral().getText());
        if (value < 1) {
            throw ctx.exception(
                    batch.start.getLine(),
                    batch.start.getCharPositionInLine(),
                    "The batch cannot be less than 1"
            );
        }
        this.batch = value;
        this.modified = true;
    }

    void setDepth(DtoParser.RecursionDepthContext depth) {
        if (recursionType != null) {
            throw ctx.exception(
                    depth.start.getLine(),
                    depth.start.getCharPositionInLine(),
                    "Cannot specify \"!depth\" when \"!recursion\" exists"
            );
        }
        if (!recursive) {
            throw ctx.exception(
                    depth.start.getLine(),
                    depth.start.getCharPositionInLine(),
                    "\"!depth\" can only be applied for recursive property"
            );
        }
        int value = Integer.parseInt(depth.IntegerLiteral().getText());
        if (value < 0) {
            throw ctx.exception(
                    depth.start.getLine(),
                    depth.start.getCharPositionInLine(),
                    "The offset cannot be less than 0"
            );
        }
        this.depth = value;
        this.modified = true;
    }

    PropConfig<P> build() {
        if (!modified) {
            return null;
        }
        return new PropConfigImpl<>(
                predicate,
                orderItems,
                filterType,
                recursionType,
                fetchType,
                limit,
                batch,
                depth
        );
    }

    private PropConfig.Predicate createPredicate(DtoParser.PredicateContext predicate) {
        List<PropConfig.Predicate> predicates =
                new ArrayList<>(predicate.subPredicates.size());
        for (DtoParser.AndPredicateContext p : predicate.subPredicates) {
            predicates.add(createPredicate(p));
        }
        return OrPredicateImpl.of(predicates);
    }

    private PropConfig.Predicate createPredicate(DtoParser.AndPredicateContext predicate) {
        List<PropConfig.Predicate> predicates =
                new ArrayList<>(predicate.subPredicates.size());
        for (DtoParser.AtomPredicateContext p : predicate.subPredicates) {
            predicates.add(createPredicate(p));
        }
        return AndPredicateImpl.of(predicates);
    }

    private PropConfig.Predicate createPredicate(DtoParser.AtomPredicateContext predicate) {
        if (predicate.cmpPredicate() != null) {
            return createPredicate(predicate.cmpPredicate());
        }
        if (predicate.nullityPredicate() != null) {
            return createPredicate(predicate.nullityPredicate());
        }
        return createPredicate(predicate.predicate());
    }

    private PropConfig.Predicate createPredicate(DtoParser.NullityPredicateContext predicate) {
        return new NullityPredicate<>(createPropPath(predicate.propPath()), predicate.not != null);
    }

    private PropConfig.Predicate createPredicate(DtoParser.CmpPredicateContext predicate) {
        if (predicate.op.getType() == DtoLexer.Identifier) {
            String opText = predicate.op.getText();
            switch (opText) {
                case "like":
                case "ilike":
                    break;
                default:
                    throw ctx.exception(
                            predicate.op.getLine(),
                            predicate.op.getCharPositionInLine(),
                            "The infix operator must \"like\" or \"ilike\""
                    );
            }
        }
        List<PropConfig.PathNode<P>> path = createPropPath(predicate.propPath());
        PropConfig.PathNode<P> lastProp = path.get(path.size() - 1);
        SimplePropType simplePropType = ctx.getSimpleType(lastProp);
        if (simplePropType == SimplePropType.NONE) {
            List<Token> parts = predicate.propPath().parts;
            Token lastPart = parts.get(parts.size() - 1);
            throw ctx.exception(
                    lastPart.getLine(),
                    lastPart.getCharPositionInLine(),
                    "The \"!where\" in DTO must be simple predicate " +
                            "so that the last property \"" +
                            lastProp +
                            "\" must be boolean, number, string"
            );
        }
        String op = predicate.op.getText();
        if (simplePropType != SimplePropType.STRING && (op.equals("like") || op.equals("ilike"))) {
            throw ctx.exception(
                    predicate.op.getLine(),
                    predicate.op.getCharPositionInLine(),
                    "The operator \"" +
                            op +
                            "\" is not allowed here because the left operand is not string"
            );
        }
        Object value = createPropValue(predicate.right, simplePropType);
        return new CmpPredicate<>(path, predicate.op.getText(), value);
    }

    @SuppressWarnings("unchecked")
    private List<PropConfig.PathNode<P>> createPropPath(DtoParser.PropPathContext propPath) {
        T baseType = ctx.getTargetType(this.baseProp);
        int size = propPath.parts.size();
        List<PropConfig.PathNode<P>> pathNodes = new ArrayList<>(size + 1);
        for (int i = 0; i < size; i++) {
            Token part = propPath.parts.get(i);
            P prop = ctx.getProps(baseType).get(part.getText());
            if (prop == null) {
                if (part.getText().endsWith("Id")) {
                    String referenceName = part.getText().substring(0, part.getText().length() - 2);
                    P referenceProp = ctx.getProps(baseType).get(referenceName);
                    if (referenceProp != null &&
                            ctx.isBasePropReference(referenceProp) &&
                            ctx.isBasePropAssociation(referenceProp, true)) {
                        String idPropName = ctx.getBasePropName(
                                ctx.getIdProp(ctx.getTargetType(referenceProp))
                        );
                        pathNodes.add(
                                new AssociatedIdPathNodeImpl<>(
                                        referenceProp,
                                        ctx.getBasePropName(referenceProp),
                                        ctx.getBasePropDisplayName(referenceProp),
                                        idPropName
                                )
                        );
                        T targetType = ctx.getTargetType(referenceProp);
                        prop = ctx.getIdProp(targetType);
                        baseType = ctx.getTargetType(prop);
                        continue;
                    }
                }
                throw ctx.exception(
                        part.getLine(),
                        part.getCharPositionInLine(),
                        "There is no property \"" +
                                part.getText() +
                                "\" in type \"" +
                                ctx.getBaseTypeQualifiedName(baseType) +
                                "\""
                );
            }
            P referenceProp = ctx.getIdViewBaseProp(prop);
            if (referenceProp != null) {
                if (!ctx.isBasePropList(referenceProp)) {
                    String idPropName = ctx.getBasePropName(
                            ctx.getIdProp(ctx.getTargetType(referenceProp))
                    );
                    pathNodes.add(
                            new AssociatedIdPathNodeImpl<>(
                                    referenceProp,
                                    ctx.getBasePropName(referenceProp),
                                    ctx.getBasePropDisplayName(referenceProp),
                                    idPropName
                            )
                    );
                    T targetType = ctx.getTargetType(referenceProp);
                    P idProp = ctx.getIdProp(targetType);
                    baseType = ctx.getTargetType(idProp);
                    continue;
                }
            }
            if (ctx.isBasePropAssociation(prop, true)) {
                if (ctx.isBasePropReference(prop)) {
                    if (i + 1 < size) {
                        String idPropName = ctx.getBasePropName(ctx.getIdProp(ctx.getTargetType(prop)));
                        if (propPath.parts.get(i + 1).getText().equals(idPropName)) {
                            throw ctx.exception(
                                    part.getLine(),
                                    part.getCharPositionInLine(),
                                    "Please replace \"" +
                                            ctx.getBasePropName(prop) +
                                            "." +
                                            idPropName +
                                            "\" to \"" +
                                            ctx.getBasePropName(prop) +
                                            "Id\""
                            );
                        }
                    } else {
                        throw ctx.exception(
                                part.getLine(),
                                part.getCharPositionInLine(),
                                "Please replace \"" +
                                        ctx.getBasePropName(prop) +
                                        "\" to \"" +
                                        ctx.getBasePropName(prop) +
                                        "Id\""
                        );
                    }
                } else {
                    throw ctx.exception(
                            part.getLine(),
                        part.getCharPositionInLine(),
                        "There property \"" +
                                    ctx.getBasePropDisplayName(prop) +
                                    "\" cannot be supported because join is forbidden by fetcher field predicate"
                    );
                }
            } else if (i + 1 < size && !ctx.isBasePropEmbedded(prop)) {
                throw ctx.exception(
                        part.getLine(),
                        part.getCharPositionInLine(),
                        "There property \"" +
                                ctx.getBasePropDisplayName(prop) +
                                "\" is not last property but it is not embedded object"
                );
            }
            pathNodes.add(
                    new SimplePathNodeImpl<>(
                            prop,
                            ctx.getBasePropName(prop),
                            ctx.getBasePropDisplayName(prop)
                    )
            );
            baseType = ctx.getTargetType(prop);
        }
        return pathNodes;
    }

    private Object createPropValue(DtoParser.PropValueContext value, SimplePropType simplePropType) {
        if (value.stringToken != null) {
            if (simplePropType != SimplePropType.STRING) {
                throw ctx.exception(
                        value.start.getLine(),
                        value.start.getCharPositionInLine(),
                        "Illegal string literal, the left operand is not string"
                );
            }
            String text = value.stringToken.getText();
            return text.substring(1, text.length() - 1);
        }
        if (value.booleanToken != null) {
            if (simplePropType != SimplePropType.BOOLEAN) {
                throw ctx.exception(
                        value.start.getLine(),
                        value.start.getCharPositionInLine(),
                        "Illegal string literal, the left operand is not boolean"
                );
            }
            return "true".equals(value.booleanToken.getText());
        }
        if (value.characterToken != null) {
            if (simplePropType != SimplePropType.STRING) {
                throw ctx.exception(
                        value.start.getLine(),
                        value.start.getCharPositionInLine(),
                        "Illegal char literal, the left operand is not string"
                );
            }
            String text = value.characterToken.getText();
            return text.substring(1, text.length() - 1);
        }
        if (value.integerToken != null) {
            switch (simplePropType) {
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                    long l = Long.parseLong(value.integerToken.getText());
                    if (value.negative != null) {
                        l = -l;
                    }
                    return l;
                case BIG_INTEGER:
                    BigInteger bi = new BigInteger(value.integerToken.getText());
                    if (value.negative != null) {
                        bi = bi.negate();
                    }
                    return bi;
                default:
                    if (simplePropType != SimplePropType.STRING) {
                        throw ctx.exception(
                                value.start.getLine(),
                                value.start.getCharPositionInLine(),
                                "Illegal integer literal, the left operand is not integer"
                        );
                    }
            }
        }
        switch (simplePropType) {
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
                BigDecimal bc =  new BigDecimal(value.floatingPointToken.getText());
                if (value.negative != null) {
                    bc = bc.negate();
                }
                return bc;
            default:
                throw ctx.exception(
                        value.start.getLine(),
                        value.start.getCharPositionInLine(),
                        "Illegal float/decimal literal, the left operand is neither float nor decimal"
                );
        }
    }

    private abstract static class CompositePredicate implements PropConfig.Predicate.Or {

        private final List<PropConfig.Predicate> predicates;

        CompositePredicate(List<PropConfig.Predicate> predicates) {
            this.predicates = Collections.unmodifiableList(predicates);
        }

        @Override
        public List<PropConfig.Predicate> getPredicates() {
            return predicates;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            boolean addSeparator = false;
            String separator = separator();
            builder.append('(');
            for (PropConfig.Predicate predicate : predicates) {
                if (addSeparator) {
                    builder.append(separator);
                } else {
                    addSeparator = true;
                }
                builder.append(predicate);
            }
            builder.append(')');
            return builder.toString();
        }

        abstract String separator();
    }

    private static class AndPredicateImpl extends CompositePredicate implements PropConfig.Predicate.And {

        private AndPredicateImpl(List<PropConfig.Predicate> predicates) {
            super(predicates);
        }

        static PropConfig.Predicate of(List<PropConfig.Predicate> predicates) {
            if (predicates.size() == 1) {
                return predicates.get(0);
            }
            return new AndPredicateImpl(predicates);
        }

        @Override
        String separator() {
            return " and ";
        }
    }

    private static class OrPredicateImpl extends CompositePredicate implements PropConfig.Predicate.Or {

        private OrPredicateImpl(List<PropConfig.Predicate> predicates) {
            super(predicates);
        }

        static PropConfig.Predicate of(List<PropConfig.Predicate> predicates) {
            if (predicates.size() == 1) {
                return predicates.get(0);
            }
            return new OrPredicateImpl(predicates);
        }

        @Override
        String separator() {
            return " or ";
        }
    }

    private static abstract class PathHolder<P> {

        final List<PropConfig.PathNode<P>> path;

        PathHolder(List<PropConfig.PathNode<P>> path) {
            this.path = path.size() == 1 ?
                    Collections.singletonList(path.get(0)) :
                    Collections.unmodifiableList(path);
        }

        public List<PropConfig.PathNode<P>> getPath() {
            return path;
        }

        String path() {
            StringBuilder builder = new StringBuilder();
            boolean addComma = false;
            for (PropConfig.PathNode<P> pathNode : path) {
                if (addComma) {
                    builder.append('.');
                } else {
                    addComma = true;
                }
                builder.append(pathNode.getPropName());
                if (pathNode.isAssociatedId()) {
                    builder.append("Id");
                }
            }
            return builder.toString();
        }
    }

    private static class OrderItemImpl<P> extends PathHolder<P> implements PropConfig.OrderItem<P> {

        private final boolean desc;

        private OrderItemImpl(List<PropConfig.PathNode<P>> path, boolean desc) {
            super(path);
            this.desc = desc;
        }

        @Override
        public boolean isDesc() {
            return desc;
        }

        @Override
        public String toString() {
            return path() + (desc ? " desc" : " asc");
        }
    }

    private static class NullityPredicate<P>
            extends PathHolder<P>
            implements PropConfig.Predicate.Nullity<P> {

        private final boolean negative;

        NullityPredicate(List<PropConfig.PathNode<P>> path, boolean negative) {
            super(path);
            this.negative = negative;
        }

        @Override
        public boolean isNegative() {
            return negative;
        }

        @Override
        public String toString() {
            return path() + (negative ? " is not null" : " is null");
        }
    }

    private static class CmpPredicate<P> extends PathHolder<P> implements PropConfig.Predicate.Cmp<P> {

        private final String operator;

        private final Object value;

        CmpPredicate(List<PropConfig.PathNode<P>> path, String operator, Object value) {
            super(path);
            this.operator = "!=".equals(operator) ? "<>" : operator;
            this.value = value;
        }

        @Override
        public String getOperator() {
            return operator;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return path() + " " +operator + " " +
                    (value instanceof String ? "\"" + value + "\"" : value);
        }
    }

    private static class LimitImpl implements PropConfig.Limit {

        private final int value;

        private final int offset;

        private LimitImpl(int value, int offset) {
            this.value = value;
            this.offset = offset;
        }

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public int getOffset() {
            return offset;
        }
    }

    private static class PropConfigImpl<P> implements PropConfig<P> {

        private final PropConfig.Predicate predicate;

        private final List<PropConfig.OrderItem<P>> orderItems;

        private final ConfigTypeRef filterType;

        private final ConfigTypeRef recursionType;

        private final String fetchType;

        private final PropConfig.Limit limit;

        private final Integer batch;

        private final Integer depth;

        private PropConfigImpl(
                Predicate predicate,
                List<OrderItem<P>> orderItems,
                ConfigTypeRef filterType,
                ConfigTypeRef recursionType,
                String fetchType,
                PropConfig.Limit limit,
                Integer batch,
                Integer depth
        ) {
            this.predicate = predicate;
            this.orderItems = orderItems;
            this.filterType = filterType;
            this.recursionType = recursionType;
            this.fetchType = fetchType;
            this.limit = limit;
            this.batch = batch;
            this.depth = depth;
        }

        @Nullable
        @Override
        public PropConfig.Predicate getPredicate() {
            return predicate;
        }

        @Override
        public List<OrderItem<P>> getOrderItems() {
            return orderItems;
        }

        @Nullable
        @Override
        public ConfigTypeRef getFilterType() {
            return filterType;
        }

        @Nullable
        @Override
        public ConfigTypeRef getRecursionType() {
            return recursionType;
        }

        @Nullable
        @Override
        public String getFetchType() {
            return fetchType;
        }

        @Override
        @Nullable
        public PropConfig.Limit getLimit() {
            return limit;
        }

        @Nullable
        @Override
        public Integer getBatch() {
            return batch;
        }

        @Nullable
        @Override
        public Integer getDepth() {
            return depth;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (predicate != null) {
                builder.append("!where(").append(predicate).append(") ");
            }
            if (!orderItems.isEmpty()) {
                builder.append("!orderBy(");
                boolean addComma = false;
                for (OrderItem<P> item : orderItems) {
                    if (addComma) {
                        builder.append(", ");
                    } else {
                        addComma = true;
                    }
                    builder.append(item);
                }
                builder.append(") ");
            }
            if (filterType != null) {
                builder.append("!filter(").append(filterType.getQualifiedName()).append(") ");
            }
            if (recursionType != null) {
                builder.append("!recursion(").append(recursionType.getQualifiedName()).append(") ");
            }
            if (!"AUTO".equals(fetchType)) {
                builder.append("!fetchType(").append(fetchType).append(") ");
            }
            if (limit != null) {
                if (limit.getOffset() != 0) {
                    builder.append("!limit(")
                            .append(limit.getValue())
                            .append(", ")
                            .append(limit.getOffset())
                            .append(") ");
                } else {
                    builder.append("!limit(").append(limit.getValue()).append(") ");
                }
            }
            if (batch != null) {
                builder.append("!batch(").append(batch).append(") ");
            }
            if (depth != null) {
                builder.append("!depth(").append(depth).append(") ");
            }
            return builder.toString();
        }
    }

    private static class SimplePathNodeImpl<P> implements PropConfig.PathNode<P> {

        private final P prop;

        private final String propName;

        private final String propDisplayName;

        SimplePathNodeImpl(P prop, String propName, String propDisplayName) {
            this.prop = prop;
            this.propName = propName;
            this.propDisplayName = propDisplayName;
        }

        @Override
        public P getProp() {
            return prop;
        }

        @Override
        public String getPropName() {
            return propName;
        }

        @Override
        public boolean isAssociatedId() {
            return false;
        }

        @Override
        public String toString() {
            return propDisplayName;
        }
    }

    private static class AssociatedIdPathNodeImpl<P> implements PropConfig.PathNode<P> {

        private final P prop;

        private final String propName;

        private final String propDisplayName;

        private final String idPropName;

        AssociatedIdPathNodeImpl(P prop, String propName, String propDisplayName, String idPropName) {
            this.prop = prop;
            this.propName = propName;
            this.propDisplayName = propDisplayName;
            this.idPropName = idPropName;
        }

        @Override
        public P getProp() {
            return prop;
        }

        @Override
        public String getPropName() {
            return propName;
        }

        @Override
        public boolean isAssociatedId() {
            return true;
        }

        @Override
        public String toString() {
            return propDisplayName + "." + idPropName;
        }
    }
}
