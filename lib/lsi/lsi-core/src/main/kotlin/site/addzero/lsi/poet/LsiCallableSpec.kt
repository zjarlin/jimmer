package site.addzero.lsi.poet

enum class LsiCallableSpecKind {
    CONSTRUCTOR,
    FUNCTION,
}

enum class LsiConstructorDelegateKind {
    THIS,
    SUPER,
}

data class LsiConstructorDelegateCall(
    val kind: LsiConstructorDelegateKind,
    val arguments: List<LsiExpression> = emptyList(),
)

data class LsiCallableSpec(
    val kind: LsiCallableSpecKind,
    val name: String? = null,
    val primary: Boolean = false,
    val receiverType: LsiTypeName? = null,
    val annotations: List<LsiAnnotationSpec> = emptyList(),
    val modifiers: Set<LsiModifier> = emptySet(),
    val typeVariables: List<LsiTypeVariableName> = emptyList(),
    val parameters: List<LsiParameterSpec> = emptyList(),
    val returnType: LsiTypeName? = null,
    val thrownTypes: List<LsiTypeName> = emptyList(),
    val delegateCall: LsiConstructorDelegateCall? = null,
    val statements: List<LsiStatement> = emptyList(),
) {
    init {
        when (kind) {
            LsiCallableSpecKind.CONSTRUCTOR -> {
                require(name == null) {
                    "Constructor must not define name"
                }
                require(receiverType == null) {
                    "Constructor must not define receiverType"
                }
            }
            LsiCallableSpecKind.FUNCTION -> require(!name.isNullOrBlank()) {
                "Function must define name"
            }
        }
    }
}

sealed interface LsiStatement

data class LsiExpressionStatement(
    val expression: LsiExpression,
) : LsiStatement

data class LsiVariableDeclarationStatement(
    val name: String,
    val type: LsiTypeName? = null,
    val mutable: Boolean = false,
    val initializer: LsiExpression,
) : LsiStatement

data class LsiAssignmentStatement(
    val target: LsiExpression,
    val expression: LsiExpression,
) : LsiStatement

data class LsiPropertySetStatement(
    val receiver: LsiExpression,
    val name: String,
    val expression: LsiExpression,
) : LsiStatement {
    init {
        require(name.isNotBlank()) {
            "Property set name must not be blank"
        }
    }
}

data class LsiReturnStatement(
    val expression: LsiExpression?,
) : LsiStatement

data class LsiThrowStatement(
    val expression: LsiExpression,
) : LsiStatement

data class LsiIfStatement(
    val condition: LsiExpression,
    val thenStatements: List<LsiStatement>,
    val elseStatements: List<LsiStatement> = emptyList(),
) : LsiStatement {
    init {
        require(thenStatements.isNotEmpty()) {
            "If statement must define thenStatements"
        }
    }
}

data class LsiTryStatement(
    val tryStatements: List<LsiStatement>,
    val finallyStatements: List<LsiStatement> = emptyList(),
) : LsiStatement {
    init {
        require(tryStatements.isNotEmpty()) {
            "Try statement must define tryStatements"
        }
    }
}

data class LsiForRangeStatement(
    val variableName: String,
    val from: LsiExpression,
    val until: LsiExpression,
    val statements: List<LsiStatement>,
) : LsiStatement {
    init {
        require(variableName.isNotBlank()) {
            "For-range variableName must not be blank"
        }
        require(statements.isNotEmpty()) {
            "For-range statement must define statements"
        }
    }
}

data class LsiWhenCase(
    val conditions: List<LsiExpression>,
    val statements: List<LsiStatement>,
) {
    init {
        require(conditions.isNotEmpty()) {
            "When case must define conditions"
        }
        require(statements.isNotEmpty()) {
            "When case must define statements"
        }
    }
}

data class LsiWhenStatement(
    val subject: LsiExpression,
    val cases: List<LsiWhenCase>,
    val elseStatements: List<LsiStatement>,
) : LsiStatement {
    init {
        require(cases.isNotEmpty()) {
            "When statement must define cases"
        }
        require(elseStatements.isNotEmpty()) {
            "When statement must define elseStatements"
        }
    }
}

sealed interface LsiExpression

data object LsiNullExpression : LsiExpression

data object LsiThisExpression : LsiExpression

data object LsiSuperExpression : LsiExpression

data class LsiNameExpression(
    val name: String,
) : LsiExpression

data class LsiCodeExpression(
    val code: LsiCodeBlock,
) : LsiExpression

data class LsiLiteralExpression(
    val value: Any?,
) : LsiExpression

data class LsiTypeExpression(
    val type: LsiClassName,
) : LsiExpression

data class LsiClassLiteralExpression(
    val type: LsiTypeName,
) : LsiExpression

data class LsiJavaClassExpression(
    val type: LsiTypeName,
) : LsiExpression

data class LsiArrayExpression(
    val elementType: LsiTypeName,
    val elements: List<LsiExpression>,
) : LsiExpression

data class LsiIntArrayExpression(
    val elements: List<LsiExpression>,
) : LsiExpression

data class LsiListExpression(
    val elements: List<LsiExpression>,
) : LsiExpression

data class LsiPropertyAccessExpression(
    val receiver: LsiExpression,
    val name: String,
) : LsiExpression

data class LsiPropertyGetExpression(
    val receiver: LsiExpression,
    val name: String,
    val type: LsiTypeName,
) : LsiExpression {
    init {
        require(name.isNotBlank()) {
            "Property get name must not be blank"
        }
    }
}

data class LsiCollectionSizeExpression(
    val receiver: LsiExpression,
) : LsiExpression

data class LsiLengthExpression(
    val receiver: LsiExpression,
) : LsiExpression

data class LsiCollectionElementExpression(
    val receiver: LsiExpression,
    val index: LsiExpression,
) : LsiExpression

data class LsiIndexAccessExpression(
    val receiver: LsiExpression,
    val index: LsiExpression,
) : LsiExpression

data class LsiCallExpression(
    val receiver: LsiExpression? = null,
    val name: String,
    val typeArguments: List<LsiTypeName> = emptyList(),
    val arguments: List<LsiExpression> = emptyList(),
) : LsiExpression

data class LsiCallableReferenceExpression(
    val receiver: LsiExpression,
    val name: String,
    val receiverLabel: String? = null,
) : LsiExpression {
    init {
        require(name.isNotBlank()) {
            "Callable reference name must not be blank"
        }
        require(receiverLabel == null || receiver is LsiThisExpression) {
            "Callable reference receiverLabel is only supported for labeled this receiver"
        }
    }
}

data class LsiEnumConstantExpression(
    val type: LsiClassName,
    val constantName: String,
) : LsiExpression

data class LsiNewExpression(
    val type: LsiClassName,
    val arguments: List<LsiExpression> = emptyList(),
) : LsiExpression

data class LsiMakeIdOnlyExpression(
    val targetType: LsiTypeName,
    val idExpression: LsiExpression,
) : LsiExpression

data class LsiCastExpression(
    val type: LsiTypeName,
    val expression: LsiExpression,
) : LsiExpression

data class LsiSafeCastExpression(
    val type: LsiTypeName,
    val expression: LsiExpression,
) : LsiExpression

enum class LsiBinaryOperator {
    PLUS,
    TIMES,
    LESS_THAN,
    LESS_THAN_OR_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUALS,
    EQUALS,
    NOT_EQUALS,
    IDENTITY_EQUALS,
    IDENTITY_NOT_EQUALS,
    AND,
    OR,
}

data class LsiBinaryExpression(
    val left: LsiExpression,
    val operator: LsiBinaryOperator,
    val right: LsiExpression,
) : LsiExpression

data class LsiArrayOfNullsExpression(
    val elementType: LsiTypeName,
    val size: LsiExpression,
    val castTo: LsiTypeName? = null,
) : LsiExpression

data class LsiVarargExpression(
    val expression: LsiExpression,
) : LsiExpression

enum class LsiLambdaMode {
    EXPRESSION,
    UNIT,
    BLOCK,
}

data class LsiLambdaExpression(
    val mode: LsiLambdaMode,
    val parameterNames: List<String> = emptyList(),
    val expression: LsiExpression? = null,
    val statements: List<LsiStatement> = emptyList(),
) : LsiExpression {
    init {
        require(parameterNames.none { it.isBlank() }) {
            "Lambda parameterNames must not contain blank values"
        }
        when (mode) {
            LsiLambdaMode.EXPRESSION -> require(expression != null) {
                "Expression lambda must define expression"
            }
            LsiLambdaMode.UNIT -> require(statements.isNotEmpty()) {
                "Unit lambda must define statements"
            }
            LsiLambdaMode.BLOCK -> {}
        }
    }
}
