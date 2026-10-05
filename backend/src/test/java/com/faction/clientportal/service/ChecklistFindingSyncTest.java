package com.faction.clientportal.service;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.dto.ChecklistItemRef;
import com.faction.clientportal.model.AssessmentChecklist;
import com.faction.clientportal.model.ChecklistResponse;
import com.faction.clientportal.model.ChecklistResult;
import com.faction.clientportal.model.Vulnerability;
import com.faction.clientportal.model.VulnerabilityChecklistItem;
import com.faction.clientportal.model.VulnerabilitySeverity;
import com.faction.clientportal.repository.AssessmentChecklistRepository;
import com.faction.clientportal.repository.VulnerabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A finding's checklist items drive the assessment's checklist: chosen items turn Vulnerable,
 * removed ones go back to Not Vulnerable unless another finding still names them.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChecklistFindingSyncTest extends TestContainersConfig {

    private static final String ASSESSMENT = "asmt-sync";
    private static final String ISEC = "tpl-isec";

    @Autowired private ChecklistFindingSync sync;
    @Autowired private AssessmentChecklistRepository checklistRepository;
    @Autowired private VulnerabilityRepository vulnerabilityRepository;

    @BeforeEach
    void setUp() {
        vulnerabilityRepository.deleteAll();
        checklistRepository.deleteAll();
        checklistRepository.save(AssessmentChecklist.builder()
                .assessmentId(ASSESSMENT)
                .templateId(ISEC)
                .templateName("iSec Web Penetration Testing Checklist")
                .responses(new ArrayList<>(List.of(
                        response("q-xss", "Cross-Site Scripting (XSS)", ChecklistResult.PASS),
                        response("q-sqli", "SQL Injection", null),
                        response("q-csp", "Content Security Policy (CSP)", ChecklistResult.NA))))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    @Test
    void resolveFillsInTheNamesFromTheAttachedChecklist() {
        List<VulnerabilityChecklistItem> items = sync.resolve(ASSESSMENT,
                List.of(new ChecklistItemRef(ISEC, "q-xss"), new ChecklistItemRef(ISEC, "q-xss")));

        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.getChecklistName()).isEqualTo("iSec Web Penetration Testing Checklist");
            assertThat(i.getQuestionText()).isEqualTo("Cross-Site Scripting (XSS)");
        });
    }

    @Test
    void resolveRefusesAnItemNoAttachedChecklistHas() {
        assertThatThrownBy(() -> sync.resolve(ASSESSMENT, List.of(new ChecklistItemRef(ISEC, "q-nope"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choosingAnItemMarksItVulnerableAndRemovingItSetsItBackToNotVulnerable() {
        List<VulnerabilityChecklistItem> items = sync.resolve(ASSESSMENT,
                List.of(new ChecklistItemRef(ISEC, "q-xss"), new ChecklistItemRef(ISEC, "q-csp")));
        Vulnerability v = finding(items);

        sync.apply(ASSESSMENT, List.of(), items);
        assertThat(result("q-xss")).isEqualTo(ChecklistResult.FAIL);
        assertThat(result("q-csp")).isEqualTo(ChecklistResult.FAIL);
        assertThat(result("q-sqli")).isNull(); // untouched

        v.setChecklistItems(new ArrayList<>(items.subList(0, 1)));
        vulnerabilityRepository.save(v);
        sync.apply(ASSESSMENT, items, v.getChecklistItems());
        assertThat(result("q-xss")).isEqualTo(ChecklistResult.FAIL);
        assertThat(result("q-csp")).isEqualTo(ChecklistResult.PASS);
    }

    @Test
    void anItemStaysVulnerableWhileAnotherFindingStillNamesIt() {
        List<VulnerabilityChecklistItem> xss = sync.resolve(ASSESSMENT, List.of(new ChecklistItemRef(ISEC, "q-xss")));
        Vulnerability first = finding(xss);
        finding(xss);
        sync.apply(ASSESSMENT, List.of(), xss);

        first.setDeletedAt(LocalDateTime.now());
        vulnerabilityRepository.save(first);
        sync.apply(ASSESSMENT, xss, List.of());
        assertThat(result("q-xss")).isEqualTo(ChecklistResult.FAIL);
    }

    @Test
    void aChecklistAttachedLaterPicksUpTheFindingsAlreadyFiledUnderIt() {
        finding(List.of(VulnerabilityChecklistItem.builder()
                .templateId("tpl-owasp").questionId("a05").checklistName("OWASP Web Top 10")
                .questionText("A05:2025 - Injection").build()));
        AssessmentChecklist owasp = AssessmentChecklist.builder()
                .assessmentId(ASSESSMENT).templateId("tpl-owasp").templateName("OWASP Web Top 10")
                .responses(new ArrayList<>(List.of(response("a05", "A05:2025 - Injection", null),
                        response("a01", "A01:2025 - Broken Access Control", null))))
                .build();

        sync.markReferenced(owasp);

        assertThat(owasp.getResponses().get(0).getResult()).isEqualTo(ChecklistResult.FAIL);
        assertThat(owasp.getResponses().get(1).getResult()).isNull();
    }

    private ChecklistResult result(String questionId) {
        return checklistRepository.findByAssessmentId(ASSESSMENT).stream()
                .filter(c -> ISEC.equals(c.getTemplateId()))
                .flatMap(c -> c.getResponses().stream())
                .filter(r -> questionId.equals(r.getQuestionId()))
                .findFirst().orElseThrow().getResult();
    }

    private Vulnerability finding(List<VulnerabilityChecklistItem> items) {
        return vulnerabilityRepository.save(Vulnerability.builder()
                .name("v-" + System.nanoTime())
                .severity(VulnerabilitySeverity.HIGH)
                .assessmentId(ASSESSMENT)
                .order(0)
                .checklistItems(new ArrayList<>(items))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    private static ChecklistResponse response(String id, String text, ChecklistResult result) {
        return ChecklistResponse.builder().questionId(id).questionText(text).result(result).build();
    }
}
