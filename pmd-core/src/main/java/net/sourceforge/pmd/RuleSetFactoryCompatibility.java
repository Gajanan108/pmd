/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.pmd.annotation.InternalApi;
import net.sourceforge.pmd.util.IOUtil;

/**
 * Provides a simple filter mechanism to avoid failing to parse an old ruleset,
 * which references rules, that have either been removed from PMD already or
 * renamed or moved to another ruleset.
 *
 * @see <a href="https://sourceforge.net/p/pmd/bugs/1360/">issue 1360</a>
 *
 * @deprecated Use {@link RuleSetLoader#enableCompatibility(boolean)} to enable this feature.
 *  This implementation is internal API.
 */
@InternalApi
@Deprecated
public class RuleSetFactoryCompatibility {
    private static final Logger LOG = Logger.getLogger(RuleSetFactoryCompatibility.class.getName());

    private List<RuleSetFilter> filters = new LinkedList<>();

    /**
     * Creates a new instance of the compatibility filter with the built-in
     * filters for the modified PMD rules.
     */
    public RuleSetFactoryCompatibility() {
        // PMD 5.3.0
        addFilterRuleRenamed("java", "design", "UncommentedEmptyMethod", "UncommentedEmptyMethodBody");
        addFilterRuleRemoved("java", "controversial", "BooleanInversion");

        // PMD 5.3.1
        addFilterRuleRenamed("java", "design", "UseSingleton", "UseUtilityClass");

        // PMD 5.4.0
        addFilterRuleMoved("java", "basic", "empty", "EmptyCatchBlock");
        addFilterRuleMoved("java", "basic", "empty", "EmptyIfStatement");
        addFilterRuleMoved("java", "basic", "empty", "EmptyWhileStmt");
        addFilterRuleMoved("java", "basic", "empty", "EmptyTryBlock");
        addFilterRuleMoved("java", "basic", "empty", "EmptyFinallyBlock");
        addFilterRuleMoved("java", "basic", "empty", "EmptySwitchStatements");
        addFilterRuleMoved("java", "basic", "empty", "EmptySynchronizedBlock");
        addFilterRuleMoved("java", "basic", "empty", "EmptyStatementNotInLoop");
        addFilterRuleMoved("java", "basic", "empty", "EmptyInitializer");
        addFilterRuleMoved("java", "basic", "empty", "EmptyStatementBlock");
        addFilterRuleMoved("java", "basic", "empty", "EmptyStaticInitializer");
        addFilterRuleMoved("java", "basic", "unnecessary", "UnnecessaryConversionTemporary");
        addFilterRuleMoved("java", "basic", "unnecessary", "UnnecessaryReturn");
        addFilterRuleMoved("java", "basic", "unnecessary", "UnnecessaryFinalModifier");
        addFilterRuleMoved("java", "basic", "unnecessary", "UselessOverridingMethod");
        addFilterRuleMoved("java", "basic", "unnecessary", "UselessOperationOnImmutable");
        addFilterRuleMoved("java", "basic", "unnecessary", "UnusedNullCheckInEquals");
        addFilterRuleMoved("java", "basic", "unnecessary", "UselessParentheses");

        // PMD 5.6.0
        addFilterRuleRenamed("java", "design", "AvoidConstantsInterface", "ConstantsInInterface");
        // unused/UnusedModifier moved AND renamed, order is important!
        addFilterRuleMovedAndRenamed("java", "unusedcode", "UnusedModifier", "unnecessary", "UnnecessaryModifier");

        // PMD 6.0.0
        addFilterRuleMoved("java", "controversial", "unnecessary", "UnnecessaryParentheses");
        addFilterRuleRenamed("java", "unnecessary", "UnnecessaryParentheses", "UselessParentheses");
        addFilterRuleMoved("java", "typeresolution", "coupling", "LooseCoupling");
        addFilterRuleMoved("java", "typeresolution", "clone", "CloneMethodMustImplementCloneable");
        addFilterRuleMoved("java", "typeresolution", "imports", "UnusedImports");
        addFilterRuleMoved("java", "typeresolution", "strictexception", "SignatureDeclareThrowsException");
        addFilterRuleRenamed("java", "naming", "MisleadingVariableName", "MIsLeadingVariableName");
        addFilterRuleRenamed("java", "unnecessary", "UnnecessaryFinalModifier", "UnnecessaryModifier");
        addFilterRuleRenamed("java", "empty", "EmptyStaticInitializer", "EmptyInitializer");
        // GuardLogStatementJavaUtil moved and renamed...
        addFilterRuleMovedAndRenamed("java", "logging-java", "GuardLogStatementJavaUtil", "logging-jakarta-commons", "GuardLogStatement");
        addFilterRuleRenamed("java", "logging-jakarta-commons", "GuardDebugLogging", "GuardLogStatement");
    }

    void addFilterRuleMovedAndRenamed(String language, String oldRuleset, String oldName, String newRuleset, String newName) {
        filters.add(RuleSetFilter.ruleMoved(language, oldRuleset, newRuleset, oldName));
        filters.add(RuleSetFilter.ruleRenamedMoved(language, newRuleset, oldName, newName));
    }

    void addFilterRuleRenamed(String language, String ruleset, String oldName, String newName) {
        filters.add(RuleSetFilter.ruleRenamed(language, ruleset, oldName, newName));
    }

    void addFilterRuleMoved(String language, String oldRuleset, String newRuleset, String ruleName) {
        filters.add(RuleSetFilter.ruleMoved(language, oldRuleset, newRuleset, ruleName));
    }

    void addFilterRuleRemoved(String language, String ruleset, String name) {
        filters.add(RuleSetFilter.ruleRemoved(language, ruleset, name));
    }

    /**
     * Applies all configured filters against the given input stream. The
     * resulting reader will contain the original ruleset modified by the
     * filters.
     *
     * @param stream the original ruleset file input stream
     * @return a reader, from which the filtered ruleset can be read
     * @throws IOException if the stream couldn't be read
     */
    public Reader filterRuleSetFile(InputStream stream) throws IOException {
        byte[] bytes = IOUtil.toByteArray(stream);
        String encoding = determineEncoding(bytes);
        String ruleset = new String(bytes, encoding);

        ruleset = applyAllFilters(ruleset);

        return new StringReader(ruleset);
    }

    private String applyAllFilters(String ruleset) {
        String result = ruleset;
        for (RuleSetFilter filter : filters) {
            result = filter.apply(result);
        }
        return result;
    }

    private static final Pattern ENCODING_PATTERN = Pattern.compile("encoding=\"([^\"]+)\"");

    /**
     * Determines the encoding of the given bytes, assuming this is a XML
     * document, which specifies the encoding in the first 1024 bytes.
     *
     * @param bytes
     *            the input bytes, might be more or less than 1024 bytes
     * @return the determined encoding, falls back to the default UTF-8 encoding
     */
    String determineEncoding(byte[] bytes) {
        String firstBytes = new String(bytes, 0, bytes.length > 1024 ? 1024 : bytes.length,
                StandardCharsets.ISO_8859_1);
        Matcher matcher = ENCODING_PATTERN.matcher(firstBytes);
        String encoding = StandardCharsets.UTF_8.name();
        if (matcher.find()) {
            encoding = matcher.group(1);
        }
        return encoding;
    }

    private static class RuleSetFilter {
        private final Pattern refPattern;
        private final String replacement;
        private Pattern exclusionPattern;
        private String exclusionReplacement;
        private final String logMessage;

        private RuleSetFilter(String refPattern, String replacement, String logMessage) {
            this.logMessage = logMessage;
            if (replacement != null) {
                this.refPattern = Pattern.compile("ref=\"" + Pattern.quote(refPattern) + "\"");
                this.replacement = "ref=\"" + replacement + "\"";
            } else {
                this.refPattern = Pattern.compile("<rule\\s+ref=\"" + Pattern.quote(refPattern) + "\"\\s*/>");
                this.replacement = "";
            }
        }

        private void setExclusionPattern(String oldName, String newName) {
            exclusionPattern = Pattern.compile("<exclude\\s+name=[\"']" + Pattern.quote(oldName) + "[\"']\\s*/>");
            if (newName != null) {
                exclusionReplacement = "<exclude name=\"" + newName + "\" />";
            } else {
                exclusionReplacement = "";
            }
        }

        public static RuleSetFilter ruleRenamed(String language, String ruleset, String oldName, String newName) {
            RuleSetFilter filter = ruleRenamedMoved(language, ruleset, oldName, newName);
            filter.setExclusionPattern(oldName, newName);
            return filter;
        }

        public static RuleSetFilter ruleRenamedMoved(String language, String ruleset, String oldName, String newName) {
            String base = "rulesets/" + language + "/" + ruleset + ".xml/";
            return new RuleSetFilter(base + oldName, base + newName, "The rule \"" + oldName
                    + "\" has been renamed to \"" + newName + "\". Please change your ruleset!");
        }

        public static RuleSetFilter ruleMoved(String language, String oldRuleset, String newRuleset, String ruleName) {
            String base = "rulesets/" + language + "/";
            return new RuleSetFilter(base + oldRuleset + ".xml/" + ruleName, base + newRuleset + ".xml/" + ruleName,
                    "The rule \"" + ruleName + "\" has been moved from ruleset \"" + oldRuleset + "\" to \""
                            + newRuleset + "\". Please change your ruleset!");
        }

        public static RuleSetFilter ruleRemoved(String language, String ruleset, String name) {
            RuleSetFilter filter = new RuleSetFilter("rulesets/" + language + "/" + ruleset + ".xml/" + name, null,
                    "The rule \"" + name + "\" in ruleset \"" + ruleset
                            + "\" has been removed from PMD and no longer exists. Please change your ruleset!");
            filter.setExclusionPattern(name, null);
            return filter;
        }

        String apply(String ruleset) {
            String result = ruleset;
            Matcher matcher = refPattern.matcher(ruleset);

            if (matcher.find()) {
                result = matcher.replaceAll(replacement);

                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.warning("Applying rule set filter: " + logMessage);
                }
            }

            if (exclusionPattern == null) {
                return result;
            }

            Matcher exclusions = exclusionPattern.matcher(result);
            if (exclusions.find()) {
                result = exclusions.replaceAll(exclusionReplacement);

                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.warning("Applying rule set filter for exclusions: " + logMessage);
                }
            }

            return result;
        }
    }
}
