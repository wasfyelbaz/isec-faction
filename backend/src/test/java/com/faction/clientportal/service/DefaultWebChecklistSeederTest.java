package com.faction.clientportal.service;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.model.AssessmentType;
import com.faction.clientportal.model.ChecklistTemplate;
import com.faction.clientportal.model.ChecklistTemplateQuestion;
import com.faction.clientportal.repository.AssessmentTypeRepository;
import com.faction.clientportal.repository.ChecklistTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two Web checklists are seeded on every start, so the behaviour worth pinning down is not
 * that they get created once — it is that a restart never duplicates them, never overwrites a
 * list a tester has edited, and never attaches them to the wrong kind of engagement.
 */
@SpringBootTest
@ActiveProfiles("test")
class DefaultWebChecklistSeederTest extends TestContainersConfig {

    private static final String ISEC = "iSec Web Penetration Testing Checklist";
    private static final String OWASP = "OWASP Web Top 10 (2025) Check List";

    /** Section 4.1 of the report has 52 attack types, 4.2 has the ten OWASP 2025 categories. */
    private static final int ISEC_QUESTION_COUNT = 52;
    private static final int OWASP_QUESTION_COUNT = 10;

    @Autowired private DefaultWebChecklistSeeder seeder;
    @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
    @Autowired private AssessmentTypeRepository assessmentTypeRepository;

    @BeforeEach
    void setUp() {
        checklistTemplateRepository.deleteAll();
        assessmentTypeRepository.deleteAll();
        ReflectionTestUtils.setField(seeder, "enabled", true);

        assessmentTypeRepository.save(AssessmentType.builder()
                .name(DefaultWebChecklistSeeder.WEB_ASSESSMENT_TYPE_NAME)
                .description("For the checklists to belong to")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private String webTypeId() {
        return assessmentTypeRepository.findByName(DefaultWebChecklistSeeder.WEB_ASSESSMENT_TYPE_NAME)
                .orElseThrow().getId();
    }

    private ChecklistTemplate template(String name) {
        return checklistTemplateRepository.findAll().stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No checklist template named " + name));
    }

    @Test
    void seedsBothChecklistsAgainstTheWebAssessmentType() {
        seeder.ensureWebChecklists();

        List<ChecklistTemplate> templates = checklistTemplateRepository.findAll();
        assertThat(templates).extracting(ChecklistTemplate::getName)
                .containsExactlyInAnyOrder(ISEC, OWASP);
        assertThat(templates).allSatisfy(t -> {
            assertThat(t.getAssessmentTypeId()).isEqualTo(webTypeId());
            assertThat(t.isActive()).isTrue();
            // These are a methodology record, not a gate: an assessment stays closable with
            // items left unanswered.
            assertThat(t.isPreventClosure()).isFalse();
            assertThat(t.getCreatedBy()).isEqualTo("system");
        });
    }

    @Test
    void keepsTheReportsQuestionsAndTheirOrder() {
        seeder.ensureWebChecklists();

        List<ChecklistTemplateQuestion> isec = template(ISEC).getQuestions();
        assertThat(isec).hasSize(ISEC_QUESTION_COUNT);
        assertThat(isec).extracting(ChecklistTemplateQuestion::getOrder)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, ISEC_QUESTION_COUNT).boxed().toList());
        assertThat(isec.get(0).getText()).isEqualTo("WAF Bypass");
        assertThat(isec.get(ISEC_QUESTION_COUNT - 1).getText()).isEqualTo("Improper Validation of File Upload Types");
        assertThat(isec).allSatisfy(q -> {
            assertThat(q.getId()).isNotBlank();
            assertThat(q.getText()).isNotBlank();
        });

        List<ChecklistTemplateQuestion> owasp = template(OWASP).getQuestions();
        assertThat(owasp).hasSize(OWASP_QUESTION_COUNT);
        assertThat(owasp.stream().sorted(Comparator.comparingInt(ChecklistTemplateQuestion::getOrder)).toList())
                .extracting(ChecklistTemplateQuestion::getText)
                .containsExactly(
                        "A01:2025 - Broken Access Control",
                        "A02:2025 - Security Misconfiguration",
                        "A03:2025 - Software Supply Chain Failures",
                        "A04:2025 - Cryptographic Failures",
                        "A05:2025 - Injection",
                        "A06:2025 - Insecure Design",
                        "A07:2025 - Authentication Failures",
                        "A08:2025 - Software or Data Integrity Failures",
                        "A09:2025 - Logging & Alerting Failures",
                        "A10:2025 - Mishandling of Exceptional Conditions");
    }

    @Test
    void aSecondRunCreatesNothing() {
        seeder.ensureWebChecklists();
        List<String> idsAfterFirstRun = checklistTemplateRepository.findAll().stream()
                .map(ChecklistTemplate::getId).sorted().toList();

        seeder.ensureWebChecklists();

        assertThat(checklistTemplateRepository.findAll()).hasSize(2);
        assertThat(checklistTemplateRepository.findAll().stream()
                .map(ChecklistTemplate::getId).sorted().toList())
                .as("the same rows, not replacements")
                .isEqualTo(idsAfterFirstRun);
    }

    @Test
    void leavesATemplateThatAlreadyCarriesTheNameAlone() {
        // The realistic case: a tester trimmed the list for their own methodology, or retired it.
        // A restart must not put our version back over the top of that.
        ChecklistTemplate edited = checklistTemplateRepository.save(ChecklistTemplate.builder()
                .name(ISEC)
                .assessmentTypeId(webTypeId())
                .questions(List.of(ChecklistTemplateQuestion.builder()
                        .id("q1").text("Only the one we care about").order(0).build()))
                .active(false)
                .preventClosure(true)
                .createdBy("someone")
                .lastUpdatedBy("someone")
                .build());

        seeder.ensureWebChecklists();

        ChecklistTemplate after = checklistTemplateRepository.findById(edited.getId()).orElseThrow();
        assertThat(after.getQuestions()).hasSize(1);
        assertThat(after.isActive()).isFalse();
        assertThat(after.isPreventClosure()).isTrue();
        // The other list was still missing, so it is created.
        assertThat(checklistTemplateRepository.findAll()).hasSize(2);
        assertThat(template(OWASP).getQuestions()).hasSize(OWASP_QUESTION_COUNT);
    }

    @Test
    void aTemplateOfTheSameNameOnAnotherTypeDoesNotCount() {
        // Names are only unique per assessment type, so the skip has to be scoped that way —
        // otherwise a Mobile checklist of the same name would suppress the Web one.
        AssessmentType mobile = assessmentTypeRepository.save(AssessmentType.builder()
                .name("Mobile Application Pentest")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        checklistTemplateRepository.save(ChecklistTemplate.builder()
                .name(ISEC)
                .assessmentTypeId(mobile.getId())
                .questions(List.of())
                .active(true)
                .build());

        seeder.ensureWebChecklists();

        assertThat(checklistTemplateRepository.findAll()).hasSize(3);
        assertThat(checklistTemplateRepository.findAll().stream()
                .filter(t -> webTypeId().equals(t.getAssessmentTypeId()))
                .map(ChecklistTemplate::getName))
                .containsExactlyInAnyOrder(ISEC, OWASP);
    }

    @Test
    void withoutTheWebAssessmentTypeItSeedsNothing() {
        assessmentTypeRepository.deleteAll();

        seeder.ensureWebChecklists();

        assertThat(checklistTemplateRepository.findAll()).isEmpty();
    }

    @Test
    void disabledSeedsNothing() {
        ReflectionTestUtils.setField(seeder, "enabled", false);

        seeder.ensureWebChecklists();

        assertThat(checklistTemplateRepository.findAll()).isEmpty();
    }

    @Test
    void theResourceParsesIntoTheTwoListsInReportOrder() throws Exception {
        // The JSON is the one source of truth for both lists; a typo in it is a silently wrong
        // checklist in every install, so parse it directly rather than only through the seeder.
        List<DefaultWebChecklistSeeder.ChecklistDefinition> definitions = seeder.loadDefinitions();

        assertThat(definitions).extracting(DefaultWebChecklistSeeder.ChecklistDefinition::name)
                .containsExactly(ISEC, OWASP);
        assertThat(definitions.get(0).questions()).hasSize(ISEC_QUESTION_COUNT).doesNotHaveDuplicates();
        assertThat(definitions.get(1).questions()).hasSize(OWASP_QUESTION_COUNT).doesNotHaveDuplicates();
        assertThat(definitions).allSatisfy(d -> {
            assertThat(d.preventClosure()).isFalse();
            assertThat(d.questions()).allSatisfy(q -> assertThat(q).isNotBlank().isEqualTo(q.strip()));
        });
    }
}
