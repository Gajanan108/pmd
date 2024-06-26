/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.ast;

import java.util.List;
import java.util.Objects;

import net.sourceforge.pmd.annotation.Experimental;

/**
 * A type pattern (JDK16). This can be found on
 * the right-hand side of an {@link ASTInstanceOfExpression InstanceOfExpression}.
 *
 * <pre class="grammar">
 *
 * TypePattern ::= ( "final" | {@linkplain ASTAnnotation Annotation} )* {@linkplain ASTType Type} {@link ASTVariableDeclaratorId VariableDeclaratorId}
 *
 * </pre>
 *
 * @see <a href="https://openjdk.java.net/jeps/394">JEP 394: Pattern Matching for instanceof</a>
*/
public final class ASTTypePattern extends AbstractJavaAnnotatableNode implements ASTPattern {

    private boolean isFinal;
    private int parenDepth;

    ASTTypePattern(int id) {
        super(id);
    }

    ASTTypePattern(JavaParser p, int id) {
        super(p, id);
    }


    @Override
    public Object jjtAccept(JavaParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    @Override
    public List<ASTAnnotation> getDeclaredAnnotations() {
        return this.findChildrenOfType(ASTAnnotation.class);
    }

    /**
     * Gets the type against which the expression is tested.
     */
    public ASTType getTypeNode() {
        return Objects.requireNonNull(getFirstChildOfType(ASTType.class));
    }

    /** Returns the declared variable. */
    public ASTVariableDeclaratorId getVarId() {
        return getFirstChildOfType(ASTVariableDeclaratorId.class);
    }

    void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }

    boolean isFinal() {
        return isFinal;
    }

    void bumpParenDepth() {
        parenDepth++;
    }

    @Override
    @Experimental
    public int getParenthesisDepth() {
        return parenDepth;
    }
}
