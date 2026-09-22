package com.faction.clientportal.service.reporting;

import com.faction.clientportal.model.AssessmentChecklist;
import com.faction.clientportal.model.ChecklistResponse;
import com.faction.clientportal.model.ChecklistResult;
import com.faction.clientportal.service.reporting.ChecklistTableRenderer.ChecklistRenderOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checklist tables a template draws with {@code ${checklist-<name>}}.
 *
 * <p>These used to come from an uploaded JAR. The behaviour worth pinning is the part a
 * template depends on: which token a checklist answers to, and that a token nobody claims
 * is left alone rather than rendered blank.
 */
class ChecklistTableRendererTest {

    private final ChecklistTableRenderer renderer = new ChecklistTableRenderer();
    private final ChecklistRenderOptions defaults = ChecklistRenderOptions.from(Map.of());

    private static ChecklistResponse row(int order, String question, ChecklistResult result, String comment) {
        return ChecklistResponse.builder()
                .questionId("q" + order).questionText(question)
                .result(result).comment(comment).order(order).build();
    }

    private static AssessmentChecklist checklist(String name, ChecklistResponse... rows) {
        return AssessmentChecklist.builder()
                .id("c1").assessmentId("a1").templateId("t1").templateName(name)
                .responses(List.of(rows)).build();
    }

    private static AssessmentChecklist sample() {
        return checklist("OWASP Mobile Top 10 Android",
                row(0, "M1: Improper Credential Usage", ChecklistResult.PASS, null),
                row(1, "M2: Inadequate Supply Chain Security", ChecklistResult.NA, "Out of scope."),
                row(2, "M5: Insecure Communication", ChecklistResult.FAIL, "No pinning."));
    }

    // ── which token a checklist answers to ───────────────────────────────────

    @Test
    void theTokenIsTheTitleLowercasedWithHyphens() {
        assertThat(ChecklistTableRenderer.variableNameFor("OWASP Mobile Top 10 Android"))
                .isEqualTo("owasp-mobile-top-10-android");
    }

    @Test
    void rendersTheChecklistWhoseNameMatchesTheToken() {
        String html = renderer.render("checklist-owasp-mobile-top-10-android",
                List.of(sample()), defaults);

        assertThat(html).isNotNull();
        assertThat(html).contains("M1: Improper Credential Usage", "M5: Insecure Communication");
    }

    /**
     * A template asking for a checklist the assessment does not have must not quietly
     * render an empty table — null leaves the placeholder for someone else to claim.
     */
    @Test
    void returnsNullForAChecklistThisAssessmentDoesNotHave() {
        assertThat(renderer.render("checklist-owasp-api-top-10", List.of(sample()), defaults)).isNull();
    }

    @Test
    void returnsNullForAnUnrelatedToken() {
        assertThat(renderer.render("faction-bar-chart", List.of(sample()), defaults)).isNull();
        assertThat(renderer.render("vulnName", List.of(sample()), defaults)).isNull();
    }

    /**
     * The published docs showed {@code columns=[…]}, which the extension never matched —
     * the tag printed itself into the report. Arguments stay unsupported, and explicitly so.
     */
    @Test
    void doesNotMatchATokenCarryingArguments() {
        assertThat(renderer.render(
                "checklist-owasp-mobile-top-10-android columns=[Question,Status]",
                List.of(sample()), defaults)).isNull();
    }

    // ── contents ─────────────────────────────────────────────────────────────

    @Test
    void numbersRowsFromOneInOrder() {
        String html = renderer.render("checklist-owasp-mobile-top-10-android", List.of(sample()), defaults);

        assertThat(html).contains("<td>1.</td>", "<td>2.</td>", "<td>3.</td>");
        assertThat(html.indexOf("<td>1.</td>")).isLessThan(html.indexOf("<td>2.</td>"));
    }

    @Test
    void rowsComeOutInTheirRecordedOrderNotTheOrderStored() {
        AssessmentChecklist scrambled = checklist("List",
                row(2, "third", ChecklistResult.PASS, null),
                row(0, "first", ChecklistResult.PASS, null),
                row(1, "second", ChecklistResult.PASS, null));

        String html = renderer.render("checklist-list", List.of(scrambled), defaults);

        assertThat(html.indexOf("first")).isLessThan(html.indexOf("second"));
        assertThat(html.indexOf("second")).isLessThan(html.indexOf("third"));
    }

    @Test
    void eachStatusTakesItsConfiguredLabelAndFill() {
        String html = renderer.render("checklist-owasp-mobile-top-10-android", List.of(sample()), defaults);

        assertThat(html).contains("Not Vulnerable").contains("#92D050");
        assertThat(html).contains("Vulnerable").contains("#C00000");
        assertThat(html).contains("N/A").contains("#D9D9D9");
    }

    @Test
    void configuredLabelsAndColoursOverrideTheDefaults() {
        ChecklistRenderOptions custom = ChecklistRenderOptions.from(Map.of(
                "passText", "OK", "passCellColour", "#001122"));

        String html = renderer.render("checklist-owasp-mobile-top-10-android", List.of(sample()), custom);

        assertThat(html).contains("OK").contains("#001122");
        assertThat(html).doesNotContain("Not Vulnerable");
        // untouched keys keep their defaults
        assertThat(html).contains("Vulnerable").contains("#C00000");
    }

    @Test
    void theCommentColumnCanBeTurnedOff() {
        ChecklistRenderOptions noComments = ChecklistRenderOptions.from(Map.of("showComments", "false"));

        String html = renderer.render("checklist-owasp-mobile-top-10-android", List.of(sample()), noComments);

        assertThat(html).doesNotContain("Comment").doesNotContain("Out of scope.");
        assertThat(html).contains("Attack Type");
    }

    /**
     * Question text and comments are typed by an assessor and land in XHTML that docx4j
     * parses. An unescaped angle bracket there fails the whole report, not just the table.
     */
    @Test
    void escapesMarkupInQuestionsAndComments() {
        AssessmentChecklist risky = checklist("List",
                row(0, "Does <script> run & break?", ChecklistResult.FAIL, "a > b \"quoted\""));

        String html = renderer.render("checklist-list", List.of(risky), defaults);

        assertThat(html).contains("&lt;script&gt;").contains("&amp;").contains("&gt;").contains("&quot;");
        assertThat(html).doesNotContain("<script>");
    }

    @Test
    void anEmptyCommentBecomesASpacerSoTheRowKeepsItsHeight() {
        AssessmentChecklist blank = checklist("List", row(0, "q", ChecklistResult.PASS, "  "));

        assertThat(renderer.render("checklist-list", List.of(blank), defaults)).contains("&nbsp;");
    }

    @Test
    void aChecklistWithNoAnswersStillRendersItsHeader() {
        AssessmentChecklist empty = checklist("List");

        String html = renderer.render("checklist-list", List.of(empty), defaults);

        assertThat(html).contains("Attack Type").contains("</table>");
    }

    @Test
    void survivesNullInput() {
        assertThat(renderer.render(null, List.of(sample()), defaults)).isNull();
        assertThat(renderer.render("checklist-list", null, defaults)).isNull();
    }
}
