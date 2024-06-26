/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.xml.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.exception.ContextedRuntimeException;
import org.w3c.dom.Document;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.rule.xpath.SaxonXPathRuleQuery;
import net.sourceforge.pmd.lang.xml.ast.XmlNode;
import net.sourceforge.pmd.lang.xml.ast.XmlParser.RootXmlNode;
import net.sourceforge.pmd.lang.xpath.Initializer;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertySource;
import net.sourceforge.pmd.util.DataMap;
import net.sourceforge.pmd.util.DataMap.DataKey;
import net.sourceforge.pmd.util.DataMap.SimpleDataKey;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.dom.NodeWrapper;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceConstant;
import net.sf.saxon.om.ValueRepresentation;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathDynamicContext;
import net.sf.saxon.sxpath.XPathEvaluator;
import net.sf.saxon.sxpath.XPathExpression;
import net.sf.saxon.sxpath.XPathStaticContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.XPathException;

final class SaxonDomXPathQuery {

    private static final NamePool NAME_POOL = new NamePool();

    private static final SimpleDataKey<DocumentWrapper> SAXON_DOM_WRAPPER
        = DataMap.simpleDataKey("pmd.saxon.dom.wrapper");

    /** The XPath expression as a string. */
    private final String xpath;
    /** The executable XPath expression. */
    private final XPathExpressionWithProperties xpathExpression;


    private final Configuration configuration;

    SaxonDomXPathQuery(String xpath, String defaultNsUri, List<PropertyDescriptor<?>> properties) {
        this.xpath = xpath;
        configuration = new Configuration();
        configuration.setNamePool(NAME_POOL);
        xpathExpression = makeXPathExpression(this.xpath, defaultNsUri, properties);
    }

    private XPathExpressionWithProperties makeXPathExpression(String xpath, String defaultUri, List<PropertyDescriptor<?>> properties) {
        final IndependentContext xpathStaticContext = new IndependentContext(configuration);
        xpathStaticContext.declareNamespace("fn", NamespaceConstant.FN);
        xpathStaticContext.setDefaultElementNamespace(defaultUri);


        // Register PMD functions
        Initializer.initialize(xpathStaticContext);

        Map<PropertyDescriptor<?>, XPathVariable> xpathVariables = declareXPathVariables(properties, xpathStaticContext);

        try {
            final XPathEvaluator xpathEvaluator = new XPathEvaluator(configuration);
            xpathEvaluator.setStaticContext(xpathStaticContext);
            XPathExpression expression = xpathEvaluator.createExpression(xpath);
            return new XPathExpressionWithProperties(
                expression,
                xpathVariables
            );
        } catch (final XPathException e) {
            throw new ContextedRuntimeException(e)
                .addContextValue("XPath", xpath);
        }
    }

    private Map<PropertyDescriptor<?>, XPathVariable> declareXPathVariables(List<PropertyDescriptor<?>> accessibleProperties, XPathStaticContext xpathStaticContext) {
        Map<PropertyDescriptor<?>, XPathVariable> xpathVariables = new HashMap<>();
        for (final PropertyDescriptor<?> propertyDescriptor : accessibleProperties) {
            final String name = propertyDescriptor.name();
            if (!isExcludedProperty(name)) {
                final XPathVariable xpathVariable = xpathStaticContext.declareVariable(null, name);
                xpathVariables.put(propertyDescriptor, xpathVariable);
            }
        }
        return Collections.unmodifiableMap(xpathVariables);
    }

    private boolean isExcludedProperty(String name) {
        return "xpath".equals(name)
               || "defaultNsUri".equals(name)
               || "violationSuppressRegex".equals(name)
               || "violationSuppressXPath".equals(name);
    }

    @Override
    public String toString() {
        return xpath;
    }

    public List<Node> evaluate(RootXmlNode root, PropertySource propertyValues) {
        DocumentWrapper wrapper = getSaxonDomWrapper(root);

        try {
            List<Node> result = new ArrayList<>();
            for (Item item : this.xpathExpression.evaluate(wrapper, propertyValues)) {
                if (item instanceof NodeWrapper) {
                    NodeWrapper nodeInfo = (NodeWrapper) item;
                    Object domNode = nodeInfo.getUnderlyingNode();
                    if (domNode instanceof org.w3c.dom.Node) {
                        XmlNode wrapped = root.wrap((org.w3c.dom.Node) domNode);
                        result.add(wrapped);
                    }
                }
            }
            return result;
        } catch (XPathException e) {
            throw new ContextedRuntimeException(e)
                .addContextValue("XPath", xpath);
        }

    }

    private DocumentWrapper getSaxonDomWrapper(RootXmlNode node) {
        DataMap<DataKey<?, ?>> userMap = node.getUserMap();
        if (userMap.isSet(SAXON_DOM_WRAPPER)) {
            return userMap.get(SAXON_DOM_WRAPPER);
        }
        Document domRoot = node.getNode();
        DocumentWrapper wrapper = new DocumentWrapper(
            domRoot, domRoot.getBaseURI(), configuration
        );
        userMap.set(SAXON_DOM_WRAPPER, wrapper);
        return wrapper;
    }

    static final class XPathExpressionWithProperties {

        final XPathExpression expr;
        final Map<PropertyDescriptor<?>, XPathVariable> xpathVariables;

        XPathExpressionWithProperties(XPathExpression expr, Map<PropertyDescriptor<?>, XPathVariable> xpathVariables) {
            this.expr = expr;
            this.xpathVariables = xpathVariables;
        }

        @SuppressWarnings("unchecked")
        private List<Item> evaluate(final DocumentWrapper elementNode, PropertySource properties) throws XPathException {
            XPathDynamicContext dynamicContext = createDynamicContext(elementNode, properties);
            return (List<Item>) expr.evaluate(dynamicContext);
        }

        private XPathDynamicContext createDynamicContext(final DocumentWrapper elementNode, PropertySource properties) {
            final XPathDynamicContext dynamicContext = expr.createDynamicContext(elementNode);

            // Set variable values on the dynamic context
            for (final Entry<PropertyDescriptor<?>, XPathVariable> entry : xpathVariables.entrySet()) {
                ValueRepresentation saxonValue = getSaxonValue(properties, entry);
                XPathVariable variable = entry.getValue();
                try {
                    dynamicContext.setVariable(variable, saxonValue);
                } catch (XPathException e) {
                    throw new ContextedRuntimeException(e)
                        .addContextValue("Variable", variable);
                }
            }
            return dynamicContext;
        }

        private static ValueRepresentation getSaxonValue(PropertySource properties, Entry<PropertyDescriptor<?>, XPathVariable> entry) {
            Object value = properties.getProperty(entry.getKey());
            Objects.requireNonNull(value, "null property value for " + entry.getKey());
            return SaxonXPathRuleQuery.getRepresentation(entry.getKey(), value);
        }
    }

}
