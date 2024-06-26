/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule.design;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.sourceforge.pmd.lang.java.ast.ASTCatchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTThrowStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTryStatement;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

/**
 * Catches the use of exception statements as a flow control device.
 *
 * @author Will Sargent
 */
public class ExceptionAsFlowControlRule extends AbstractJavaRule {

    public ExceptionAsFlowControlRule() {
        addRuleChainVisit(ASTThrowStatement.class);
    }

    @Override
    public Object visit(ASTThrowStatement node, Object data) {
        ASTTryStatement parent = node.getFirstParentOfType(ASTTryStatement.class);
        if (parent == null) {
            return data;
        }
        for (parent = parent.getFirstParentOfType(ASTTryStatement.class); parent != null; parent = parent
                .getFirstParentOfType(ASTTryStatement.class)) {

            List<ASTCatchStatement> list = parent.findDescendantsOfType(ASTCatchStatement.class);
            for (ASTCatchStatement catchStmt : list) {
                ASTFormalParameter fp = (ASTFormalParameter) catchStmt.getChild(0);
                ASTType type = fp.getFirstDescendantOfType(ASTType.class);
                ASTClassOrInterfaceType name = type.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
                if (isExceptionOfTypeThrown(node, name.getImage())) {
                    addViolation(data, name);
                }
            }
        }
        return data;
    }

    private boolean isExceptionOfTypeThrown(ASTThrowStatement throwStatement, String typeName) {
        final ASTClassOrInterfaceType t = throwStatement.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
        String thrownTypeName = t == null ? null : t.getImage();
        return StringUtils.equals(thrownTypeName, typeName);
    }
}
