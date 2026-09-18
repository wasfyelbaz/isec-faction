package com.faction.clientportal.util.reporting;

import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Findings keep their order when a rich-text field expands to several blocks.
 *
 * <p>The block's insertion point used to advance once per template paragraph, but a
 * {@code ${details}} paragraph is replaced by as many blocks as its HTML holds (steps,
 * screenshots, code, tables). Every block beyond the first pushed the next finding's insertion
 * point back inside the previous finding's Proof of Concept: with three findings the report read
 * "finding 1 step 1, step 2, finding 2, finding 3, finding 1 step 3".
 */
class DocxUtilsFindingsBlockOrderTest {

    private static ReportData.ReportVulnerability finding(String name, String details) {
        return ReportData.ReportVulnerability.builder()
                .id(name).name(name).severity("High").severityKey("HIGH")
                .description("<p>" + name + " description</p>")
                .recommendation("<p>" + name + " recommendation</p>")
                .details(details)
                .build();
    }

    private static List<String> paragraphTexts(WordprocessingMLPackage pkg) throws Exception {
        List<String> out = new ArrayList<>();
        for (Object o : pkg.getMainDocumentPart().getContent()) {
            Object el = XmlUtils.unwrap(o);
            if (!(el instanceof P)) continue;
            StringWriter w = new StringWriter();
            TextUtils.extractText(el, w);
            String t = w.toString().trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    @Test
    void aMultiBlockProofOfConceptDoesNotSwallowTheNextFinding() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        main.addParagraphOfText("Intro");
        main.addParagraphOfText("${fiBegin}");
        main.addParagraphOfText("${vulnName}");
        main.addParagraphOfText("${desc}");
        main.addParagraphOfText("Proof Of Concept");
        main.addParagraphOfText("${details}");
        main.addParagraphOfText("${fiEnd}");
        main.addParagraphOfText("Outro");

        ReportData data = ReportData.builder().vulnerabilities(List.of(
                finding("Finding A", "<p>A step 1</p><p>A step 2</p><p>A step 3</p><p>A step 4</p>"),
                finding("Finding B", "<p>B step 1</p>"),
                finding("Finding C", "<p>C step 1</p><p>C step 2</p>"))).build();
        WordprocessingMLPackage result = new DocxUtils(pkg, data).generateDocx("");

        assertThat(paragraphTexts(result)).containsExactly(
                "Intro",
                "Finding A", "Finding A description", "Proof Of Concept",
                "A step 1", "A step 2", "A step 3", "A step 4",
                "Finding B", "Finding B description", "Proof Of Concept", "B step 1",
                "Finding C", "Finding C description", "Proof Of Concept", "C step 1", "C step 2",
                "Outro");
    }

    @Test
    void anEmptyRichTextFieldStillKeepsTheOrder() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        main.addParagraphOfText("${fiBegin}");
        main.addParagraphOfText("${vulnName}");
        main.addParagraphOfText("${details}");
        main.addParagraphOfText("${fiEnd}");
        main.addParagraphOfText("Outro");

        ReportData data = ReportData.builder().vulnerabilities(List.of(
                finding("Finding A", ""),
                finding("Finding B", "<p>B step 1</p><p>B step 2</p>"))).build();
        WordprocessingMLPackage result = new DocxUtils(pkg, data).generateDocx("");

        assertThat(paragraphTexts(result)).containsExactly(
                "Finding A", "Finding B", "B step 1", "B step 2", "Outro");
    }
}
