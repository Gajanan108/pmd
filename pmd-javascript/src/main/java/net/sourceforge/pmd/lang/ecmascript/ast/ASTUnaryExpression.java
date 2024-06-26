/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.ecmascript.ast;

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.UnaryExpression;

import net.sourceforge.pmd.annotation.InternalApi;

public class ASTUnaryExpression extends AbstractEcmascriptNode<UnaryExpression> {
    @Deprecated
    @InternalApi
    public ASTUnaryExpression(UnaryExpression unaryExpression) {
        super(unaryExpression);
        super.setImage(AstRoot.operatorToString(unaryExpression.getOperator()));
    }

    @Override
    public Object jjtAccept(EcmascriptParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    public EcmascriptNode<?> getOperand() {
        return (EcmascriptNode<?>) getChild(0);
    }

    public boolean isPrefix() {
        return !isPostfix();
    }

    public boolean isPostfix() {
        return node.getOperator() == Token.INC || node.getOperator() == Token.DEC;
    }
}
