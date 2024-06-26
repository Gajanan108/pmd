/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.document;

import net.sourceforge.pmd.lang.LanguageVersion;

/**
 * Makes {@link StringTextFile} publicly available for unit tests.
 */
public class SimpleTestTextFile extends StringTextFile {

    public SimpleTestTextFile(String content, String pathId, String displayName, LanguageVersion languageVersion) {
        super(content, pathId, displayName, languageVersion);
    }
}
