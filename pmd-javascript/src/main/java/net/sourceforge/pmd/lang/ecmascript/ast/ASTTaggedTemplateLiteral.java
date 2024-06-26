/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.ecmascript.ast;

import org.mozilla.javascript.ast.TaggedTemplateLiteral;

public final class ASTTaggedTemplateLiteral extends AbstractEcmascriptNode<TaggedTemplateLiteral> {

    ASTTaggedTemplateLiteral(TaggedTemplateLiteral taggedTemplateLiteral) {
        super(taggedTemplateLiteral);
    }

    @Override
    public Object jjtAccept(EcmascriptParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }
}
