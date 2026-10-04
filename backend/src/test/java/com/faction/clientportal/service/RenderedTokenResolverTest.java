package com.faction.clientportal.service;

import com.faction.clientportal.model.AssessmentChecklist;
import com.faction.clientportal.model.ChecklistResponse;
import com.faction.clientportal.model.ChecklistResult;
import com.faction.clientportal.model.Vulnerability;
import com.faction.clientportal.model.VulnerabilitySeverity;
import com.faction.clientportal.service.reporting.ChecklistTableRenderer;
import com.faction.clientportal.service.reporting.SeverityBarChartRenderer;
import com.faction.clientportal.util.reporting.DocxUtils;
import com.faction.clientportal.util.reporting.ReportData;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checklist table and the severity chart, rendered by the real renderers through a real
 * document — the seam the unit tests on either side of it cannot see.
 *
 * <p>{@link DocxUtils} offers a resolver the whole placeholder, braces included; the renderers
 * take the bare name. The renderer tests fed bare names and the {@code DocxUtils} tests used
 * stand-in resolvers that accepted braces, so both stayed green while every live report printed
 * {@code ${checklist-…}} and {@code ${faction-bar-chart}} as text. This test is the one that
 * would have failed.
 */
class RenderedTokenResolverTest {

    private static AssessmentChecklist apiChecklist() {
        return AssessmentChecklist.builder()
                .id("c1").assessmentId("a1").templateId("t1").templateName("OWASP API Top 10")
                .responses(List.of(
                        ChecklistResponse.builder().questionId("q1").order(0)
                                .questionText("API1:2023 Broken Object Level Authorization")
                                .result(ChecklistResult.FAIL).build(),
                        ChecklistResponse.builder().questionId("q2").order(1)
                                .questionText("API2:2023 Broken Authentication")
                                .result(ChecklistResult.PASS).build()))
                .build();
    }

    private static List<Vulnerability> findings() {
        return List.of(
                Vulnerability.builder().id("v1").name("IDOR").severity(VulnerabilitySeverity.CRITICAL).build(),
                Vulnerability.builder().id("v2").name("Verbose errors").severity(VulnerabilitySeverity.LOW).build());
    }

    private static DocxUtils.TokenResolver resolver() {
        return DocxReportGenerationService.renderedTokenResolver(
                new ChecklistTableRenderer(), List.of(apiChecklist()),
                ChecklistTableRenderer.ChecklistRenderOptions.from(Map.of()),
                new SeverityBarChartRenderer(), findings(),
                SeverityBarChartRenderer.BarChartOptions.from(Map.of()));
    }

    private static String render(String... paragraphs) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        for (String p : paragraphs) {
            pkg.getMainDocumentPart().addParagraphOfText(p);
        }
        DocxUtils utils = new DocxUtils(pkg, ReportData.builder().vulnerabilities(new ArrayList<>()).build());
        WordprocessingMLPackage out = utils.generateDocx("", resolver());
        return XmlUtils.marshaltoString(out.getMainDocumentPart().getJaxbElement(), true, false);
    }

    @Test
    void aChecklistPlaceholderInTheTemplateBecomesTheChecklistTable() throws Exception {
        String xml = render("${checklist-owasp-api-top-10}");

        assertThat(xml).doesNotContain("${checklist-");
        assertThat(xml).contains("API1:2023 Broken Object Level Authorization",
                                 "API2:2023 Broken Authentication");
        assertThat(xml).contains("<w:tbl");
    }

    @Test
    void theChartPlaceholderInTheTemplateBecomesAnEmbeddedPicture() throws Exception {
        String xml = render("${faction-bar-chart}");

        assertThat(xml).doesNotContain("faction-bar-chart");
        assertThat(xml).contains("<w:drawing");
    }

    @Test
    void bothResolveInTheSameDocument() throws Exception {
        String xml = render("${checklist-owasp-api-top-10}", "${faction-bar-chart}");

        assertThat(xml).doesNotContain("${");
        assertThat(xml).contains("<w:tbl").contains("<w:drawing");
    }

    /**
     * A checklist the assessment does not have is still left alone — the fix must not turn
     * "unclaimed" into "blanked".
     */
    @Test
    void aChecklistTheAssessmentDoesNotHaveStaysVisible() throws Exception {
        assertThat(render("${checklist-owasp-mobile-top-10-ios}"))
                .contains("${checklist-owasp-mobile-top-10-ios}");
    }

    @Test
    void placeholderNameStripsTheBracesAndOnlyTheBraces() {
        assertThat(DocxReportGenerationService.placeholderName("${checklist-owasp-api-top-10}"))
                .isEqualTo("checklist-owasp-api-top-10");
        assertThat(DocxReportGenerationService.placeholderName("  ${faction-bar-chart} "))
                .isEqualTo("faction-bar-chart");
        assertThat(DocxReportGenerationService.placeholderName("faction-bar-chart"))
                .isEqualTo("faction-bar-chart");
        assertThat(DocxReportGenerationService.placeholderName(null)).isNull();
    }
}
