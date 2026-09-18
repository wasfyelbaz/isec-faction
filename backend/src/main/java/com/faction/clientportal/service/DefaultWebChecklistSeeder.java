package com.faction.clientportal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faction.clientportal.dto.ChecklistTemplateQuestionDto;
import com.faction.clientportal.dto.CreateChecklistTemplateRequest;
import com.faction.clientportal.model.AssessmentType;
import com.faction.clientportal.repository.AssessmentTypeRepository;
import com.faction.clientportal.repository.ChecklistTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gives a Web assessment the two checklists every Web engagement is actually run against.
 *
 * <p>A fresh install ships no checklist templates at all, so the first Web assessment has
 * nothing to work through and the methodology lives in whoever's head is doing the testing.
 * These two lists are the ones the Web report already prints — section 4.1 "iSec Web
 * Penetration Testing Checklist" and section 4.2 "OWASP Web Top 10 Check List" — so seeding
 * them makes the tool agree with the deliverable instead of diverging from it.
 *
 * <p>The item texts live in {@code resources/checklists/web-default-checklists.json}, verbatim
 * and in report order; this class only turns them into templates. Faction records one result
 * per question — PASS, FAIL or NA — plus a comment, and the report's own wording maps onto
 * that: "Not Vulnerable"/"Passed" is PASS, "Vulnerable" is FAIL, "N/A" is NA. Nothing here
 * introduces a new result value.
 *
 * <p>Idempotent by name, per assessment type: a template already carrying one of these names
 * on the Web type is left exactly as it is, however it was edited, so restarts never duplicate
 * or overwrite a customised list. Deleting one does bring it back on the next restart — set
 * {@code faction.default-web-checklists.enabled=false} to stop that.
 *
 * <p>Best effort, like the default report template: a missing assessment type or an unreadable
 * resource is logged and skipped, never fatal. Startup is not the place to fail over a
 * checklist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultWebChecklistSeeder {

    /** The assessment type these lists belong to — the one {@link BootstrapService} seeds. */
    static final String WEB_ASSESSMENT_TYPE_NAME = "Web Application Pentest";

    static final String RESOURCE_PATH = "checklists/web-default-checklists.json";

    /** Bootstrap writes as "system", the same as every other seeded row. */
    private static final String SYSTEM_USER = "system";

    private final ChecklistTemplateRepository checklistTemplateRepository;
    private final AssessmentTypeRepository assessmentTypeRepository;
    private final ChecklistTemplateService checklistTemplateService;
    private final ObjectMapper objectMapper;

    @Value("${faction.default-web-checklists.enabled:true}")
    private boolean enabled;

    /**
     * Install the two Web checklist templates if they are not already there.
     *
     * <p>Called from bootstrap after the assessment types are seeded — a checklist template
     * belongs to one, so there is nothing to attach to before that.
     */
    public void ensureWebChecklists() {
        if (!enabled) {
            log.debug("Default Web checklists are disabled. Skipping.");
            return;
        }

        Optional<AssessmentType> webType = assessmentTypeRepository.findByName(WEB_ASSESSMENT_TYPE_NAME);
        if (webType.isEmpty()) {
            // An install that renamed or removed the Web type has its own taxonomy; guessing at a
            // replacement would attach these to the wrong kind of engagement.
            log.info("No '{}' assessment type. Skipping the default Web checklists.", WEB_ASSESSMENT_TYPE_NAME);
            return;
        }
        String assessmentTypeId = webType.get().getId();

        List<ChecklistDefinition> definitions;
        try {
            definitions = loadDefinitions();
        } catch (IOException e) {
            log.warn("Could not read {}. Skipping the default Web checklists.", RESOURCE_PATH, e);
            return;
        }

        for (ChecklistDefinition definition : definitions) {
            if (checklistTemplateRepository.existsByAssessmentTypeIdAndName(assessmentTypeId, definition.name())) {
                log.debug("Checklist template '{}' already exists for the Web assessment type. Skipping.",
                        definition.name());
                continue;
            }

            CreateChecklistTemplateRequest request = new CreateChecklistTemplateRequest();
            request.setName(definition.name());
            request.setAssessmentTypeId(assessmentTypeId);
            request.setPreventClosure(definition.preventClosure());
            request.setQuestions(toQuestions(definition.questions()));

            // Through the service, so a seeded template is built exactly like one created from
            // Assessment Config — same question ids, same active flag, same timestamps.
            checklistTemplateService.create(request, SYSTEM_USER);
            log.info("Created default Web checklist template '{}' with {} questions.",
                    definition.name(), request.getQuestions().size());
        }
    }

    /** The definitions as the resource file has them; visible for the test that parses it. */
    List<ChecklistDefinition> loadDefinitions() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            ChecklistFile file = objectMapper.readValue(in, ChecklistFile.class);
            return file.checklists() == null ? List.of() : file.checklists();
        }
    }

    /** Order is the order of the list, zero-based — the same convention the UI writes. */
    private static List<ChecklistTemplateQuestionDto> toQuestions(List<String> texts) {
        List<ChecklistTemplateQuestionDto> questions = new ArrayList<>();
        if (texts == null) {
            return questions;
        }
        for (int i = 0; i < texts.size(); i++) {
            questions.add(ChecklistTemplateQuestionDto.builder()
                    .text(texts.get(i))
                    .order(i)
                    .build());
        }
        return questions;
    }

    /** Unknown fields are ignored on purpose: the file carries prose comments for the reader. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChecklistFile(List<ChecklistDefinition> checklists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChecklistDefinition(String name, boolean preventClosure, List<String> questions) {
    }
}
