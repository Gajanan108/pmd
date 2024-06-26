/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.benchmark.TimeTracker;
import net.sourceforge.pmd.benchmark.TimedOperation;
import net.sourceforge.pmd.benchmark.TimedOperationCategory;
import net.sourceforge.pmd.lang.ast.Node;

/**
 * This is a base class for RuleChainVisitor implementations which extracts
 * interesting nodes from an AST, and lets each Rule visit the nodes it has
 * expressed interest in.
 *
 * @deprecated See {@link RuleChainVisitor}
 */
@Deprecated
public abstract class AbstractRuleChainVisitor implements RuleChainVisitor {
    private static final Logger LOG = Logger.getLogger(AbstractRuleChainVisitor.class.getName());

    /**
     * These are all the rules participating in the RuleChain, grouped by
     * RuleSet.
     */
    protected Map<RuleSet, List<Rule>> ruleSetRules = new LinkedHashMap<>();

    /**
     * This is a mapping from node names to nodes instances for the current AST.
     */
    protected Map<String, List<Node>> nodeNameToNodes;

    /**
     * @see RuleChainVisitor#add(RuleSet, Rule)
     */
    @Override
    public void add(RuleSet ruleSet, Rule rule) {

        if (!ruleSetRules.containsKey(ruleSet)) {
            ruleSetRules.put(ruleSet, new ArrayList<Rule>());
        }
        ruleSetRules.get(ruleSet).add(rule);
    }

    /**
     * @see RuleChainVisitor#visitAll(List, RuleContext)
     */
    @Override
    public void visitAll(List<Node> nodes, RuleContext ctx) {
        initialize();
        clear();

        // Perform a visitation of the AST to index nodes which need visiting by
        // type
        try (TimedOperation to = TimeTracker.startOperation(TimedOperationCategory.RULECHAIN_VISIT)) {
            indexNodes(nodes, ctx);
        }

        // For each RuleSet, only if this source file applies
        try (TimedOperation to = TimeTracker.startOperation(TimedOperationCategory.RULECHAIN_RULE)) {
            for (Map.Entry<RuleSet, List<Rule>> entry : ruleSetRules.entrySet()) {
                RuleSet ruleSet = entry.getKey();
                if (!ruleSet.applies(ctx.getSourceCodeFile())) {
                    continue;
                }

                // For each rule, allow it to visit the nodes it desires
                for (Rule rule : entry.getValue()) {
                    int visits = 0;
                    if (!RuleSet.applies(rule, ctx.getLanguageVersion())) {
                        continue;
                    }
                    // CPD-OFF
                    try (TimedOperation rcto = TimeTracker.startOperation(TimedOperationCategory.RULECHAIN_RULE, rule.getName())) {
                        ctx.setCurrentRule(rule);
                        final List<String> nodeNames = rule.getRuleChainVisits();
                        for (int j = 0; j < nodeNames.size(); j++) {
                            List<Node> ns = nodeNameToNodes.get(nodeNames.get(j));
                            for (Node node : ns) {
                                // Visit with underlying Rule, not the RuleReference
                                Rule actualRule = rule;
                                while (actualRule instanceof RuleReference) {
                                    actualRule = ((RuleReference) actualRule).getRule();
                                }
                                visit(actualRule, node, ctx);
                            }
                            visits += ns.size();
                        }
                        rcto.close(visits);
                    } catch (RuntimeException e) {
                        if (ctx.isIgnoreExceptions()) {
                            ctx.getReport().addError(new Report.ProcessingError(e, String.valueOf(ctx.getSourceCodeFile())));

                            if (LOG.isLoggable(Level.WARNING)) {
                                LOG.log(Level.WARNING, "Exception applying rule " + rule.getName() + " on file "
                                        + ctx.getSourceCodeFile() + ", continuing with next rule", e);
                            }
                        } else {
                            throw e;
                        }
                    } finally {
                        ctx.setCurrentRule(null);
                    }
                    // CPD-ON
                }
            }
        }
    }

    /**
     * Visit the given rule to the given node.
     */
    protected abstract void visit(Rule rule, Node node, RuleContext ctx);

    /**
     * Index all nodes for visitation by rules.
     */
    protected abstract void indexNodes(List<Node> nodes, RuleContext ctx);

    /**
     * Index a single node for visitation by rules.
     */
    protected void indexNode(Node node) {
        List<Node> nodes = nodeNameToNodes.get(node.getXPathNodeName());
        if (nodes != null) {
            nodes.add(node);
        }
    }

    /**
     * Initialize the RuleChainVisitor to be ready to perform visitations. This
     * method should not be called until it is known that all Rules
     * participating in the RuleChain are ready to be initialized themselves.
     * Some rules may require full initialization to determine if they will
     * participate in the RuleChain, so this has been delayed as long as
     * possible to ensure that manipulation of the Rules is no longer occurring.
     */
    protected void initialize() {
        if (nodeNameToNodes != null) {
            return;
        }

        // Determine all node types that need visiting
        Set<String> visitedNodes = new HashSet<>();
        for (Iterator<Map.Entry<RuleSet, List<Rule>>> entryIterator = ruleSetRules.entrySet().iterator(); entryIterator
                .hasNext();) {
            Map.Entry<RuleSet, List<Rule>> entry = entryIterator.next();
            for (Iterator<Rule> ruleIterator = entry.getValue().iterator(); ruleIterator.hasNext();) {
                Rule rule = ruleIterator.next();
                if (rule.isRuleChain()) {
                    visitedNodes.addAll(rule.getRuleChainVisits());

                    logXPathRuleChainUsage(true, rule);
                } else {
                    // Drop rules which do not participate in the rule chain.
                    ruleIterator.remove();

                    logXPathRuleChainUsage(false, rule);
                }
            }
            // Drop RuleSets in which all Rules have been dropped.
            if (entry.getValue().isEmpty()) {
                entryIterator.remove();
            }
        }

        // Setup the data structure to manage mapping node names to node
        // instances. We intend to reuse this data structure between
        // visits to different ASTs.
        nodeNameToNodes = new HashMap<>();
        for (String s : visitedNodes) {
            List<Node> nodes = new ArrayList<>(100);
            nodeNameToNodes.put(s, nodes);
        }
    }

    private void logXPathRuleChainUsage(boolean usesRuleChain, Rule rule) {
        if (LOG.isLoggable(Level.FINE)) {
            Rule r;
            if (rule instanceof RuleReference) {
                r = ((RuleReference) rule).getRule();
            } else {
                r = rule;
            }
            if (r instanceof XPathRule) {
                String message = (usesRuleChain ? "Using " : "no ")
                        + "rule chain for XPath " + rule.getProperty(XPathRule.VERSION_DESCRIPTOR)
                        + " rule: " + rule.getName() + " (" + rule.getRuleSetName() + ")";
                LOG.fine(message);
            }
        }
    }

    /**
     * Clears the internal data structure used to manage the nodes visited
     * between visiting different ASTs.
     */
    protected void clear() {
        for (List<Node> l : nodeNameToNodes.values()) {
            l.clear();
        }
    }
}
