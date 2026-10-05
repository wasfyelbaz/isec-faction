package com.faction.clientportal.service;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.dto.AcceptPeerReviewRequest;
import com.faction.clientportal.dto.ChecklistItemRef;
import com.faction.clientportal.dto.CreateVulnerabilityRequest;
import com.faction.clientportal.dto.PeerReviewDto;
import com.faction.clientportal.dto.PeerReviewVulnerabilityDto;
import com.faction.clientportal.dto.UpdatePeerReviewRequest;
import com.faction.clientportal.model.*;
import com.faction.clientportal.repository.AssessmentChecklistRepository;
import com.faction.clientportal.repository.AssessmentRepository;
import com.faction.clientportal.repository.PeerReviewRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reviewer's revisions to any part of a finding reach the live finding when the assessor accepts
 * them, with the same side effects as the assessor making the edit: checklist answers follow the
 * finding's items.
 */
@SpringBootTest
@ActiveProfiles("test")
class PeerReviewFindingAcceptTest extends TestContainersConfig {

    @Autowired private PeerReviewService peerReviewService;
    @Autowired private VulnerabilityService vulnerabilityService;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private VulnerabilityRepository vulnerabilityRepository;
    @Autowired private AssessmentChecklistRepository checklistRepository;
    @Autowired private PeerReviewRepository peerReviewRepository;

    private Assessment assessment;

    @BeforeEach
    void setUp() {
        peerReviewRepository.deleteAll();
        vulnerabilityRepository.deleteAll();
        checklistRepository.deleteAll();
        assessmentRepository.deleteAll();
        assessment = assessmentRepository.save(Assessment.builder()
                .name("Review").applicationId("app-1").organizationId("org-1")
                .assessmentTypeId("t").status("Testing")
                .createdAt(LocalDateTime.now()).build());
        checklistRepository.save(AssessmentChecklist.builder()
                .assessmentId(assessment.getId()).templateId("tpl").templateName("OWASP Web Top 10")
                .responses(new ArrayList<>(List.of(
                        ChecklistResponse.builder().questionId("a05").questionText("A05 - Injection")
                                .result(ChecklistResult.PASS).build(),
                        ChecklistResponse.builder().questionId("a01").questionText("A01 - Broken Access Control")
                                .result(ChecklistResult.PASS).build())))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    private ChecklistResult answer(String questionId) {
        return checklistRepository.findByAssessmentId(assessment.getId()).get(0).getResponses().stream()
                .filter(r -> questionId.equals(r.getQuestionId())).findFirst().orElseThrow().getResult();
    }

    @Test
    void acceptedRevisionsToEveryPartOfAFindingReachTheLiveFinding() {
        CreateVulnerabilityRequest create = new CreateVulnerabilityRequest();
        create.setName("Sqli");
        create.setSeverity(VulnerabilitySeverity.MEDIUM);
        create.setImpactNarrative("<p>Data can be read.</p>");
        create.setAssetLocation("https://app/login");
        create.setChecklistItems(List.of(new ChecklistItemRef("tpl", "a05")));
        String vulnId = vulnerabilityService.create(assessment.getId(), create, "tester").getId();

        PeerReviewDto review = peerReviewService.submitForPeerReview(assessment.getId(), "tester");
        PeerReviewVulnerabilityDto snap = review.getVulnerabilities().get(0);
        assertThat(snap.getImpactNarrative()).isEqualTo("<p>Data can be read.</p>");
        assertThat(snap.getChecklistItems()).extracting(VulnerabilityChecklistItem::getQuestionId).containsExactly("a05");

        peerReviewService.startReview(review.getId(), "reviewer");
        snap.setRevisedName("SQL Injection in Login");
        snap.setRevisedSeverity(VulnerabilitySeverity.HIGH);
        snap.setRevisedImpactNarrative("<p>The whole customer database can be read.</p>");
        snap.setRevisedAssetLocation("https://app/api/login");
        snap.setRevisedChecklistItems(List.of(VulnerabilityChecklistItem.builder()
                .templateId("tpl").questionId("a01").build()));
        snap.setAttributesNotes("Severity should be High: unauthenticated.");
        UpdatePeerReviewRequest edits = new UpdatePeerReviewRequest();
        edits.setVulnerabilities(List.of(snap));
        peerReviewService.updateReview(review.getId(), edits, "reviewer");
        peerReviewService.completeReview(review.getId(), "reviewer");

        peerReviewService.acceptChanges(review.getId(), AcceptPeerReviewRequest.builder()
                .acceptedAssessmentFieldIds(List.of())
                .acceptedVulnerabilityChanges(Map.of(vulnId, List.of(
                        "name", "severity", "impactNarrative", "assetLocation", "checklistItems")))
                .build(), "tester");

        Vulnerability live = vulnerabilityRepository.findById(vulnId).orElseThrow();
        assertThat(live.getName()).isEqualTo("SQL Injection in Login");
        assertThat(live.getSeverity()).isEqualTo(VulnerabilitySeverity.HIGH);
        assertThat(live.getImpactNarrative()).isEqualTo("<p>The whole customer database can be read.</p>");
        assertThat(live.getAssetLocation()).isEqualTo("https://app/api/login");
        assertThat(live.getChecklistItems()).extracting(VulnerabilityChecklistItem::getQuestionId).containsExactly("a01");
        assertThat(answer("a01")).isEqualTo(ChecklistResult.FAIL);
        assertThat(answer("a05")).isEqualTo(ChecklistResult.PASS);
        assertThat(peerReviewRepository.findById(review.getId()).orElseThrow()
                .getVulnerabilities().get(0).getAttributesNotes())
                .isEqualTo("Severity should be High: unauthenticated.");
    }
}
