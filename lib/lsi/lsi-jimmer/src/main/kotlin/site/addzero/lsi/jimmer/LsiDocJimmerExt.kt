package site.addzero.lsi.jimmer

import org.babyfish.jimmer.client.meta.Doc
import site.addzero.lsi.doc.LsiDoc

// 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata.getDoc
// 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition/handleService/handleOperation
// 迁移说明：client schema runtime 仍消费 jimmer `Doc` 时，统一在 Jimmer 语义层执行 `LsiDoc -> Doc` 显式转换，
// 保持 compiler 内部先面向 LSI 文档对象编程
fun LsiDoc.toJimmerDoc(): Doc =
    Doc(
        value,
        parameterValueMap,
        returnValue,
        propertyValueMap,
    )
