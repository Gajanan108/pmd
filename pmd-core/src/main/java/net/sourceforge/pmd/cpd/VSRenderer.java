/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.cpd;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.cpd.renderer.CPDRenderer;
import net.sourceforge.pmd.cpd.renderer.CPDReportRenderer;

public class VSRenderer implements Renderer, CPDRenderer, CPDReportRenderer {

    @Override
    public String render(Iterator<Match> matches) {
        StringWriter writer = new StringWriter(300);
        try {
            render(matches, writer);
        } catch (IOException ignored) {
            // Not really possible with a StringWriter
        }
        return writer.toString();
    }

    @Override
    public void render(Iterator<Match> matches, Writer writer) throws IOException {
        for (Match match; matches.hasNext();) {
            match = matches.next();
            Mark mark;
            for (Iterator<Mark> iterator = match.iterator(); iterator.hasNext();) {
                mark = iterator.next();
                writer.append(mark.getFilename())
                        .append('(').append(String.valueOf(mark.getBeginLine())).append("):")
                        .append(" Between lines ").append(String.valueOf(mark.getBeginLine())).append(" and ").append(String.valueOf(mark.getBeginLine() + match.getLineCount())).append(PMD.EOL);
            }
        }
        writer.flush();
    }

    @Override
    public void render(CPDReport report, Writer writer) throws IOException {
        render(report.getMatches().iterator(), writer);
    }
}
