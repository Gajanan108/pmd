/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.rule.xpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.ast.DummyNode;
import net.sourceforge.pmd.lang.ast.DummyNodeWithListAndEnum;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

import net.sf.saxon.expr.Expression;

public class SaxonXPathRuleQueryTest {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Test
    public void testListAttribute() {
        DummyNodeWithListAndEnum dummy = new DummyNodeWithListAndEnum(1);

        assertQuery(1, "//dummyNode[@List = \"A\"]", dummy);
        assertQuery(1, "//dummyNode[@List = \"B\"]", dummy);
        assertQuery(0, "//dummyNode[@List = \"C\"]", dummy);
        assertQuery(1, "//dummyNode[@Enum = \"FOO\"]", dummy);
        assertQuery(0, "//dummyNode[@Enum = \"BAR\"]", dummy);
        assertQuery(1, "//dummyNode[@EnumList = \"FOO\"]", dummy);
        assertQuery(1, "//dummyNode[@EnumList = \"BAR\"]", dummy);
        assertQuery(1, "//dummyNode[@EnumList = (\"FOO\", \"BAR\")]", dummy);
        assertQuery(0, "//dummyNode[@EmptyList = (\"A\")]", dummy);
    }

    @Test
    public void testInvalidReturn() {
        DummyNodeWithListAndEnum dummy = new DummyNodeWithListAndEnum(1);


        expected.expect(RuntimeException.class);
        expected.expectMessage(CoreMatchers.containsString("XPath rule expression returned a non-node"));
        expected.expectMessage(CoreMatchers.containsString("Int64Value"));

        createQuery("1+2").evaluate(dummy, new RuleContext());
    }

    @Test
    public void testRootExpression() {
        DummyNode dummy = new DummyNode(); // todo in pmd 7 this should be a RootNode

        List<Node> result = assertQuery(1, "/", dummy);
        Assert.assertEquals(dummy, result.get(0));
    }

    @Test
    public void testRootExpressionIsADocumentNode() {
        DummyNode dummy = new DummyNode(); // todo in pmd 7 this should be a RootNode

        List<Node> result = assertQuery(1, "(/)[self::document-node()]", dummy);
        Assert.assertEquals(dummy, result.get(0));
    }

    @Test
    public void testRootExpressionWithName() {
        DummyNode dummy = new DummyNode(0, false, "DummyNodeA"); // todo in pmd 7 this should be a RootNode

        List<Node> result = assertQuery(1, "(/)[self::document-node(element(DummyNodeA))]", dummy);
        Assert.assertEquals(dummy, result.get(0));

        assertQuery(0, "(/)[self::document-node(element(DummyNodeX))]", dummy);
    }

    @Test
    public void ruleChainVisits() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[@Image='baz']/foo | //bar[@Public = 'true'] | //dummyNode[@Public = false()] | //dummyNode");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(2, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertTrue(ruleChainVisits.contains("bar"));

        Assert.assertEquals(3, query.nodeNameToXPaths.size());
        assertExpression("((self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Image, xs:anyAtomicType)), ($qq:qq106374177 singleton eq \"baz\"))])/child::element(foo, xs:anyType))", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("(self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq609962972 singleton eq false()))])", query.nodeNameToXPaths.get("dummyNode").get(1));
        assertExpression("self::node()", query.nodeNameToXPaths.get("dummyNode").get(2));
        assertExpression("(self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq232307208 singleton eq \"true\"))])", query.nodeNameToXPaths.get("bar").get(0));
        assertExpression("(((DocumentSorter(((((/)/descendant::element(dummyNode, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Image, xs:anyAtomicType)), ($qq:qq000 singleton eq \"baz\"))])/child::element(foo, xs:anyType))) | (((/)/descendant::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq000 singleton eq \"true\"))])) | (((/)/descendant::element(dummyNode, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq000 singleton eq false()))])) | ((/)/descendant::element(dummyNode, xs:anyType)))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitsMultipleFilters() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[@Test1 = false()][@Test2 = true()]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("((self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Test2, xs:anyAtomicType)), ($qq:qq1741979653 singleton eq true()))])[QuantifiedExpression(Atomizer(attribute::attribute(Test1, xs:anyAtomicType)), ($qq:qq1529060733 singleton eq false()))])", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("((((/)/descendant::element(dummyNode, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Test2, xs:anyAtomicType)), ($qq:qq1741979653 singleton eq true()))])[QuantifiedExpression(Atomizer(attribute::attribute(Test1, xs:anyAtomicType)), ($qq:qq1529060733 singleton eq false()))])", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitsCustomFunctions() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[pmd-dummy:typeIs(@Image)]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("(self::node()[pmd-dummy:typeIs(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(Image, xs:anyAtomicType))))))])", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("DocumentSorter((((/)/descendant-or-self::node())/(child::element(dummyNode, xs:anyType)[pmd-dummy:typeIs(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(Image, xs:anyAtomicType))))))])))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    /**
     * If a query contains another unbounded path expression other than the first one, it must be
     * excluded from rule chain execution. Saxon itself optimizes this quite good already.
     */
    @Test
    public void ruleChainVisitsUnboundedPathExpressions() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[//ClassOrInterfaceType]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
        Assert.assertEquals(1, query.nodeNameToXPaths.size());
        assertExpression("LetExpression(LazyExpression(((/)/descendant::element(ClassOrInterfaceType, xs:anyType))), (((/)/descendant::element(dummyNode, xs:anyType))[$zz:zz771775563]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));

        // second sample, more complex
        query = createQuery("//dummyNode[ancestor::ClassOrInterfaceDeclaration[//ClassOrInterfaceType]]");
        ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
        Assert.assertEquals(1, query.nodeNameToXPaths.size());
        assertExpression("LetExpression(LazyExpression(((/)/descendant::element(ClassOrInterfaceType, xs:anyType))), (((/)/descendant::element(dummyNode, xs:anyType))[(ancestor::element(ClassOrInterfaceDeclaration, xs:anyType)[$zz:zz106374177])]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));

        // third example, with boolean expr
        query = createQuery("//dummyNode[//ClassOrInterfaceType or //OtherNode]");
        ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
        Assert.assertEquals(1, query.nodeNameToXPaths.size());
        assertExpression("LetExpression(LazyExpression((((/)/descendant::element(ClassOrInterfaceType, xs:anyType)) or ((/)/descendant::element(OtherNode, xs:anyType)))), (((/)/descendant::element(dummyNode, xs:anyType))[$zz:zz1364913072]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitsNested() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode/foo/*/bar[@Test = 'false']");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("((((self::node()/child::element(foo, xs:anyType))/child::element())/child::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Test, xs:anyAtomicType)), ($qq:qq166794956 singleton eq \"false\"))])", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("DocumentSorter(((((((/)/descendant::element(dummyNode, xs:anyType))/child::element(foo, xs:anyType))/child::element())/child::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Test, xs:anyAtomicType)), ($qq:qq166794956 singleton eq \"false\"))]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitsNested2() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode/foo[@Baz = 'a']/*/bar[@Test = 'false']");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("(((((self::node()/child::element(foo, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Baz, xs:anyAtomicType)), ($qq:qq306612792 singleton eq \"a\"))])/child::element())/child::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Test, xs:anyAtomicType)), ($qq:qq1803669141 singleton eq \"false\"))])", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("DocumentSorter((((((((/)/descendant::element(dummyNode, xs:anyType))/child::element(foo, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Baz, xs:anyAtomicType)), ($qq:qq306612792 singleton eq \"a\"))])/child::element())/child::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Test, xs:anyAtomicType)), ($qq:qq1803669141 singleton eq \"false\"))]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitWithVariable() {
        PropertyDescriptor<String> testClassPattern = PropertyFactory.stringProperty("testClassPattern").desc("test").defaultValue("a").build();
        SaxonXPathRuleQuery query = createQuery("//dummyNode[matches(@SimpleName, $testClassPattern)]", testClassPattern);
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("LetExpression(LazyExpression(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer($testClassPattern))))), (self::node()[matches(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(SimpleName, xs:anyAtomicType))))), $zz:zz952562199)]))", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("LetExpression(LazyExpression(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer($testClassPattern))))), (((/)/descendant::element(dummyNode, xs:anyType))[matches(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(SimpleName, xs:anyAtomicType))))), $zz:zz952562199)]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitWithVariable2() {
        PropertyDescriptor<String> testClassPattern = PropertyFactory.stringProperty("testClassPattern").desc("test").defaultValue("a").build();
        SaxonXPathRuleQuery query = createQuery("//dummyNode[matches(@SimpleName, $testClassPattern)]/foo", testClassPattern);
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("(LetExpression(LazyExpression(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer($testClassPattern))))), (self::node()[matches(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(SimpleName, xs:anyAtomicType))))), $zz:zz952562199)]))/child::element(foo, xs:anyType))", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("DocumentSorter((LetExpression(LazyExpression(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer($testClassPattern))))), (((/)/descendant::element(dummyNode, xs:anyType))[matches(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(SimpleName, xs:anyAtomicType))))), $zz:zz952562199)]))/child::element(foo, xs:anyType)))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    @Test
    public void ruleChainVisitWithTwoFunctions() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[ends-with(@Image, 'foo')][pmd-dummy:typeIs('bar')]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(1, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertEquals(2, query.nodeNameToXPaths.size());
        assertExpression("((self::node()[ends-with(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(Image, xs:anyAtomicType))))), \"foo\")])[pmd-dummy:typeIs(\"bar\")])", query.nodeNameToXPaths.get("dummyNode").get(0));
    }

    private static void assertExpression(String expected, Expression actual) {
        Assert.assertEquals(normalizeExprDump(expected),
                            normalizeExprDump(actual.toString()));
        //Assert.assertEquals(expected, actual);
    }

    private static String normalizeExprDump(String dump) {
        return dump.replaceAll("\\$qq:qq-?\\d+", "\\$qq:qq000")
                   .replaceAll("\\$zz:zz-?\\d+", "\\$zz:zz000");
    }

    @Test
    public void ruleChainVisitsCompatibilityMode() {
        SaxonXPathRuleQuery query = createQuery("//dummyNode[@Image='baz']/foo | //bar[@Public = 'true'] | //dummyNode[@Public = 'false']");
        query.setVersion(XPathRuleQuery.XPATH_1_0_COMPATIBILITY);
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(2, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("dummyNode"));
        Assert.assertTrue(ruleChainVisits.contains("bar"));

        Assert.assertEquals(3, query.nodeNameToXPaths.size());
        assertExpression("((self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Image, xs:anyAtomicType)), ($qq:qq6519275 singleton eq \"baz\"))])/child::element(foo, xs:anyType))", query.nodeNameToXPaths.get("dummyNode").get(0));
        assertExpression("(self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq1529060733 singleton eq \"false\"))])", query.nodeNameToXPaths.get("dummyNode").get(1));
        assertExpression("(self::node()[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq1484171695 singleton eq \"true\"))])", query.nodeNameToXPaths.get("bar").get(0));
        assertExpression("((DocumentSorter(((((/)/descendant::element(dummyNode, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Image, xs:anyAtomicType)), ($qq:qq692331943 singleton eq \"baz\"))])/child::element(foo, xs:anyType))) | (((/)/descendant::element(bar, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq2127036371 singleton eq \"true\"))])) | (((/)/descendant::element(dummyNode, xs:anyType))[QuantifiedExpression(Atomizer(attribute::attribute(Public, xs:anyAtomicType)), ($qq:qq1529060733 singleton eq \"false\"))]))", query.nodeNameToXPaths.get(SaxonXPathRuleQuery.AST_ROOT).get(0));
    }

    private static List<Node> assertQuery(int resultSize, String xpath, Node node) {
        SaxonXPathRuleQuery query = createQuery(xpath);
        List<Node> result = query.evaluate(node, new RuleContext());
        Assert.assertEquals(resultSize, result.size());
        return result;
    }

    private static SaxonXPathRuleQuery createQuery(String xpath, PropertyDescriptor<?>... descriptors) {
        SaxonXPathRuleQuery query = new SaxonXPathRuleQuery();
        query.setVersion(XPathRuleQuery.XPATH_2_0);
        if (descriptors != null) {
            Map<PropertyDescriptor<?>, Object> props = new HashMap<PropertyDescriptor<?>, Object>();
            for (PropertyDescriptor<?> prop : descriptors) {
                props.put(prop, prop.defaultValue());
            }
            query.setProperties(props);
        } else {
            query.setProperties(Collections.<PropertyDescriptor<?>, Object>emptyMap());
        }
        query.setXPath(xpath);
        return query;
    }

    @Test
    public void ruleChainWithUnions() {
        SaxonXPathRuleQuery query = createQuery("(//ForStatement | //WhileStatement | //DoStatement)//AssignmentOperator");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
    }

    @Test
    public void ruleChainWithUnionsAndFilter() {
        SaxonXPathRuleQuery query = createQuery("(//ForStatement | //WhileStatement | //DoStatement)//AssignmentOperator[@Image='foo']");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
    }

    @Test
    public void ruleChainWithUnionsCustomFunctionsVariant1() {
        SaxonXPathRuleQuery query = createQuery("(//ForStatement | //WhileStatement | //DoStatement)//dummyNode[pmd-dummy:typeIs(@Image)]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
    }

    @Test
    public void ruleChainWithUnionsCustomFunctionsVariant2() {
        SaxonXPathRuleQuery query = createQuery("//(ForStatement | WhileStatement | DoStatement)//dummyNode[pmd-dummy:typeIs(@Image)]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(0, ruleChainVisits.size());
    }

    @Test
    public void ruleChainWithUnionsCustomFunctionsVariant3() {
        SaxonXPathRuleQuery query = createQuery("//ForStatement//dummyNode[pmd-dummy:typeIs(@Image)]"
                + " | //WhileStatement//dummyNode[pmd-dummy:typeIs(@Image)]"
                + " | //DoStatement//dummyNode[pmd-dummy:typeIs(@Image)]");
        List<String> ruleChainVisits = query.getRuleChainVisits();
        Assert.assertEquals(3, ruleChainVisits.size());
        Assert.assertTrue(ruleChainVisits.contains("ForStatement"));
        Assert.assertTrue(ruleChainVisits.contains("WhileStatement"));
        Assert.assertTrue(ruleChainVisits.contains("DoStatement"));

        final String expectedSubexpression = "((self::node()/descendant-or-self::node())/(child::element(dummyNode, xs:anyType)[pmd-dummy:typeIs(CardinalityChecker(ItemChecker(UntypedAtomicConverter(Atomizer(attribute::attribute(Image, xs:anyAtomicType))))))]))";
        assertExpression(expectedSubexpression, query.nodeNameToXPaths.get("ForStatement").get(0));
        assertExpression(expectedSubexpression, query.nodeNameToXPaths.get("WhileStatement").get(0));
        assertExpression(expectedSubexpression, query.nodeNameToXPaths.get("DoStatement").get(0));
    }
}
