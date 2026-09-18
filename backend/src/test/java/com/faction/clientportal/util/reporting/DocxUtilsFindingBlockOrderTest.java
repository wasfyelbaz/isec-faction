package com.faction.clientportal.util.reporting;

import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Br;
import org.docx4j.wml.P;
import org.docx4j.wml.STBrType;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A findings block that contains both a rich-text field and trailing content must repeat
 * intact, once per finding.
 *
 * <p>Rich text is what makes this non-trivial: {@code ${details}} becomes as many paragraphs as
 * its HTML needs, so a block is longer after rendering than the template was. The renderer used
 * to expand it inside the insertion loop, while the cursor it inserts at counted template
 * paragraphs — so every finding after the first landed inside its predecessor's block and shoved
 * that block's tail to the end of the section.
 *
 * <p>The visible damage was a {@code ${pageBreak}} at the end of the block: instead of one break
 * per finding, the breaks collected at the section end, so findings shared pages and blank pages
 * appeared where two displaced breaks ended up adjacent. Asserted on structure rather than on a
 * rendered page count, because that is what the renderer actually controls.
 */
class DocxUtilsFindingBlockOrderTest {

    private static ReportData.ReportVulnerability finding(String name, String details) {
        return ReportData.ReportVulnerability.builder()
                .id(name).name(name).severity("High")
                .details(details)
                .build();
    }

    /** A template whose findings block ends with trailing content, as a real one does. */
    private static WordprocessingMLPackage template() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        var mdp = pkg.getMainDocumentPart();
        mdp.addParagraphOfText("${fiBegin}");
        mdp.addParagraphOfText("${vulnName}");
        mdp.addParagraphOfText("${details}");
        mdp.addParagraphOfText("${pageBreak}");
        mdp.addParagraphOfText("${fiEnd}");
        return pkg;
    }

    private static List<String> lines(WordprocessingMLPackage pkg) {
        List<String> out = new ArrayList<>();
        for (Object o : pkg.getMainDocumentPart().getContent()) {
            Object el = XmlUtils.unwrap(o);
            if (!(el instanceof P p)) continue;
            StringWriter sw = new StringWriter();
            try {
                TextUtils.extractText(p, sw);
            } catch (Exception ignored) {
            }
            String text = sw.toString().trim();
            if (hasPageBreak(p)) {
                out.add("<<BREAK>>");
            } else if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    private static boolean hasPageBreak(Object node) {
        if (node instanceof Br br) {
            return br.getType() == STBrType.PAGE;
        }
        if (node instanceof org.docx4j.wml.ContentAccessor ca) {
            for (Object child : ca.getContent()) {
                if (hasPageBreak(XmlUtils.unwrap(child))) return true;
            }
        }
        return false;
    }

    @Test
    void eachFindingKeepsItsOwnPageBreakInsteadOfPilingThemAtTheEnd() throws Exception {
        WordprocessingMLPackage pkg = template();

        new DocxUtils(pkg, ReportData.builder()
                .vulnerabilities(List.of(
                        finding("First finding", "<p>step one</p><p>step two</p><p>step three</p>"),
                        finding("Second finding", "<p>only step</p>"),
                        finding("Third finding", "<p>alpha</p><p>beta</p>")))
                .sections(List.of())
                .build())
                .generateDocx("");

        List<String> out = lines(pkg);

        // Every finding is followed by its own break, before the next finding starts.
        assertThat(out)
                .as("each finding must be followed by its own page break")
                .containsSubsequence(
                        "First finding", "<<BREAK>>",
                        "Second finding", "<<BREAK>>",
                        "Third finding", "<<BREAK>>");

        assertThat(out.stream().filter("<<BREAK>>"::equals).count())
                .as("one break per finding, no more")
                .isEqualTo(3);

        // The regression: two breaks with nothing between them is what rendered a blank page.
        for (int i = 1; i < out.size(); i++) {
            assertThat(out.get(i - 1) + "|" + out.get(i))
                    .as("adjacent page breaks produce a blank page")
                    .isNotEqualTo("<<BREAK>>|<<BREAK>>");
        }
    }

    @Test
    void richTextDetailsStayWithTheirOwnFinding() throws Exception {
        WordprocessingMLPackage pkg = template();

        new DocxUtils(pkg, ReportData.builder()
                .vulnerabilities(List.of(
                        finding("Alpha", "<p>alpha detail</p>"),
                        finding("Beta", "<p>beta detail</p>")))
                .sections(List.of())
                .build())
                .generateDocx("");

        assertThat(lines(pkg))
                .as("details must follow the finding they belong to, not drift into the next")
                .containsSubsequence("Alpha", "alpha detail", "Beta", "beta detail");
    }

    /** One finding cannot drift, so this pins that the fix did not change the simple case. */
    @Test
    void aSingleFindingIsUnchanged() throws Exception {
        WordprocessingMLPackage pkg = template();

        new DocxUtils(pkg, ReportData.builder()
                .vulnerabilities(List.of(finding("Only finding", "<p>the detail</p>")))
                .sections(List.of())
                .build())
                .generateDocx("");

        assertThat(lines(pkg)).containsSubsequence("Only finding", "the detail", "<<BREAK>>");
    }
}
