package com.faction.clientportal.util.reporting;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code ${checklistItems <name>}} in a finding's table: that checklist's items, one per line. */
class DocxUtilsChecklistItemsTest {

    private static final String CELL = "<w:r><w:rPr><w:b/></w:rPr><w:t>${checklistItems isec-web-penetration-testing-checklist}</w:t></w:r>";

    private DocxUtils utils() throws Exception {
        return new DocxUtils(WordprocessingMLPackage.createPackage(),
                ReportData.builder().vulnerabilities(new ArrayList<>()).build());
    }

    private static ReportData.ReportVulnerability finding(Map<String, List<String>> items) {
        return ReportData.ReportVulnerability.builder().checklistItems(items).build();
    }

    @Test
    void eachItemGetsItsOwnLineInTheSameRun() throws Exception {
        String out = utils().replaceChecklistItems(CELL, finding(Map.of(
                "isec-web-penetration-testing-checklist",
                List.of("Cross-Site Scripting (XSS)", "JavaScript Execution", "Insufficient Input Validation"))));

        assertThat(out).doesNotContain("${");
        assertThat(out).containsSubsequence("Cross-Site Scripting (XSS)", "<w:br/>", "JavaScript Execution",
                "<w:br/>", "Insufficient Input Validation");
        // still one run, so the cell's formatting carries to every line
        assertThat(out.split("<w:r>", -1)).hasSize(2);
        assertThat(out.split("<w:br/>", -1)).hasSize(3);
    }

    @Test
    void theChecklistNameIsMatchedWhateverItsCase() throws Exception {
        String out = utils().replaceChecklistItems(
                "<w:t>${checklistItems OWASP-Web-Top-10}</w:t>",
                finding(Map.of("owasp-web-top-10", List.of("A05:2025 - Injection"))));
        assertThat(out).contains("A05:2025 - Injection").doesNotContain("${");
    }

    @Test
    void aFindingWithNoItemsOnThatChecklistPrintsNothing() throws Exception {
        String out = utils().replaceChecklistItems(CELL, finding(Map.of("owasp-web-top-10", List.of("A01"))));
        assertThat(out).isEqualTo("<w:r><w:rPr><w:b/></w:rPr><w:t></w:t></w:r>");
    }
}
