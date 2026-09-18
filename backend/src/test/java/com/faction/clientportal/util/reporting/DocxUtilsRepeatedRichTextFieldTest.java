package com.faction.clientportal.util.reporting;

import com.faction.clientportal.model.FieldType;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same rich-text field used in more than one place in a template.
 *
 * <p>The iSec MAPT template does this: {@code ${limitations}} appears both in the executive
 * summary and again under "Testing Constraints and Limitations". Whether that works is not
 * obvious from reading the code — {@code replaceHTML} is called with {@code once=false} for
 * assessment rich-text fields, so the map entry survives the first hit, but each hit splices
 * the <em>same</em> object instances into a second place in the document tree. These are
 * docx4j POJOs rather than DOM nodes, so sharing them is not automatically fatal, but it is
 * the kind of thing that silently produces one filled section and one empty one.
 *
 * <p>Pinned here because the failure is invisible: the report renders, and a section that
 * should carry the engagement's limitations is simply blank in a client deliverable.
 */
class DocxUtilsRepeatedRichTextFieldTest {

    private static final String LIMITATIONS =
            "During this engagement, no limitations were encountered.";

    private static ReportData dataWithLimitations() {
        return ReportData.builder()
                .fieldValues(Map.of("limitations", "<p>" + LIMITATIONS + "</p>"))
                .fieldTypes(Map.of("limitations", FieldType.RICH_TEXT))
                .vulnerabilities(List.of())
                .sections(List.of())
                .build();
    }

    private static List<String> lines(WordprocessingMLPackage pkg) {
        return pkg.getMainDocumentPart().getContent().stream()
                .map(XmlUtils::unwrap)
                .filter(P.class::isInstance)
                .map(p -> {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    try {
                        org.docx4j.TextUtils.extractText(p, sw);
                    } catch (Exception ignored) {
                    }
                    return sw.toString().trim();
                })
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Test
    void aRichTextFieldUsedTwiceFillsBothPlaces() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("2.0 Executive Summary");
        pkg.getMainDocumentPart().addParagraphOfText("${limitations}");
        pkg.getMainDocumentPart().addParagraphOfText("3.2 Testing Constraints and Limitations");
        pkg.getMainDocumentPart().addParagraphOfText("${limitations}");

        new DocxUtils(pkg, dataWithLimitations()).generateDocx("");

        List<String> out = lines(pkg);
        assertThat(out)
                .as("neither placeholder may survive into the rendered document")
                .noneMatch(s -> s.contains("${limitations}"));
        assertThat(out.stream().filter(s -> s.contains(LIMITATIONS)).count())
                .as("both occurrences must be filled, not just the first")
                .isEqualTo(2);
    }

    @Test
    void aRichTextFieldUsedOnceStillFills() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("3.2 Testing Constraints and Limitations");
        pkg.getMainDocumentPart().addParagraphOfText("${limitations}");

        new DocxUtils(pkg, dataWithLimitations()).generateDocx("");

        List<String> out = lines(pkg);
        assertThat(out).noneMatch(s -> s.contains("${limitations}"));
        assertThat(out).anyMatch(s -> s.contains(LIMITATIONS));
    }

    /**
     * Saving and reloading is where shared object instances would show up as corruption if
     * docx4j objected to one being reachable from two places in the tree.
     */
    @Test
    void theTwiceUsedFieldSurvivesARoundTripThroughBytes() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("${limitations}");
        pkg.getMainDocumentPart().addParagraphOfText("${limitations}");

        new DocxUtils(pkg, dataWithLimitations()).generateDocx("");

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        pkg.save(out);
        WordprocessingMLPackage reloaded = WordprocessingMLPackage.load(
                new java.io.ByteArrayInputStream(out.toByteArray()));

        assertThat(lines(reloaded).stream().filter(s -> s.contains(LIMITATIONS)).count())
                .as("both copies must still be there after a save/load cycle")
                .isEqualTo(2);
    }
}
