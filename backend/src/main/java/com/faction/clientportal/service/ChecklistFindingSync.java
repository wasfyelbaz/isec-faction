package com.faction.clientportal.service;

import com.faction.clientportal.dto.ChecklistItemRef;
import com.faction.clientportal.model.AssessmentChecklist;
import com.faction.clientportal.model.ChecklistResponse;
import com.faction.clientportal.model.ChecklistResult;
import com.faction.clientportal.model.Vulnerability;
import com.faction.clientportal.model.VulnerabilityChecklistItem;
import com.faction.clientportal.repository.AssessmentChecklistRepository;
import com.faction.clientportal.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps an assessment's checklists in step with the checklist items its findings are filed under.
 *
 * <p>Choosing an item on a finding marks it Vulnerable (FAIL) in the assessment's checklist.
 * Taking it off — or deleting the finding — sets it back to Not Vulnerable (PASS), unless another
 * live finding on the assessment still names it. Items nothing has touched are left as the tester
 * answered them, so a hand-set answer survives until a finding claims the item.
 *
 * <p>Items are keyed on the checklist template and question rather than on the assessment's copy
 * of the checklist, which is what lets a carried-forward finding mark the right item in its new
 * assessment.
 */
@Service
@RequiredArgsConstructor
public class ChecklistFindingSync {

    private final AssessmentChecklistRepository checklistRepository;
    private final VulnerabilityRepository vulnerabilityRepository;

    private record Key(String templateId, String questionId) {
        static Key of(VulnerabilityChecklistItem item) {
            return new Key(item.getTemplateId(), item.getQuestionId());
        }
    }

    /**
     * The items {@code refs} name, with their checklist and question names, resolved against the
     * checklists attached to the assessment. Duplicates collapse to one.
     *
     * @throws IllegalArgumentException for an item no attached checklist has
     */
    public List<VulnerabilityChecklistItem> resolve(String assessmentId, List<ChecklistItemRef> refs) {
        if (refs == null || refs.isEmpty()) return new ArrayList<>();
        Map<Key, VulnerabilityChecklistItem> available = new LinkedHashMap<>();
        for (AssessmentChecklist checklist : checklistRepository.findByAssessmentId(assessmentId)) {
            if (checklist.getResponses() == null) continue;
            for (ChecklistResponse r : checklist.getResponses()) {
                available.putIfAbsent(new Key(checklist.getTemplateId(), r.getQuestionId()),
                        VulnerabilityChecklistItem.builder()
                                .templateId(checklist.getTemplateId())
                                .questionId(r.getQuestionId())
                                .checklistName(checklist.getTemplateName())
                                .questionText(r.getQuestionText())
                                .build());
            }
        }
        Map<Key, VulnerabilityChecklistItem> chosen = new LinkedHashMap<>();
        for (ChecklistItemRef ref : refs) {
            if (ref == null) continue;
            Key key = new Key(ref.getTemplateId(), ref.getQuestionId());
            VulnerabilityChecklistItem item = available.get(key);
            if (item == null) {
                throw new IllegalArgumentException(
                        "Checklist item " + ref.getQuestionId() + " is not on a checklist attached to this assessment");
            }
            chosen.putIfAbsent(key, item);
        }
        return new ArrayList<>(chosen.values());
    }

    /**
     * Applies a finding's change of items, after the finding has been saved: items added are
     * marked Vulnerable, items removed go back to Not Vulnerable unless another live finding on
     * the assessment still names them.
     */
    public void apply(String assessmentId, Collection<VulnerabilityChecklistItem> before,
                      Collection<VulnerabilityChecklistItem> after) {
        Set<Key> was = keys(before);
        Set<Key> now = keys(after);
        Set<Key> added = new HashSet<>(now);
        added.removeAll(was);
        Set<Key> removed = new HashSet<>(was);
        removed.removeAll(now);
        if (added.isEmpty() && removed.isEmpty()) return;

        if (!removed.isEmpty()) {
            removed.removeAll(stillReferenced(assessmentId));
        }
        if (added.isEmpty() && removed.isEmpty()) return;

        for (AssessmentChecklist checklist : checklistRepository.findByAssessmentId(assessmentId)) {
            boolean changed = false;
            if (checklist.getResponses() == null) continue;
            for (ChecklistResponse r : checklist.getResponses()) {
                Key key = new Key(checklist.getTemplateId(), r.getQuestionId());
                if (added.contains(key) && r.getResult() != ChecklistResult.FAIL) {
                    r.setResult(ChecklistResult.FAIL);
                    changed = true;
                } else if (removed.contains(key) && r.getResult() != ChecklistResult.PASS) {
                    r.setResult(ChecklistResult.PASS);
                    changed = true;
                }
            }
            if (changed) {
                checklist.setUpdatedAt(Instant.now());
                checklistRepository.save(checklist);
            }
        }
    }

    /**
     * Marks, on a checklist about to be attached, every item a live finding on its assessment is
     * already filed under. Changes {@code checklist} in place; the caller saves it.
     */
    public void markReferenced(AssessmentChecklist checklist) {
        if (checklist.getResponses() == null) return;
        Set<Key> referenced = stillReferenced(checklist.getAssessmentId());
        for (ChecklistResponse r : checklist.getResponses()) {
            if (referenced.contains(new Key(checklist.getTemplateId(), r.getQuestionId()))) {
                r.setResult(ChecklistResult.FAIL);
            }
        }
    }

    private Set<Key> stillReferenced(String assessmentId) {
        Set<Key> keys = new HashSet<>();
        for (Vulnerability v : vulnerabilityRepository.findByAssessmentIdAndDeletedAtIsNull(assessmentId)) {
            keys.addAll(keys(v.getChecklistItems()));
        }
        return keys;
    }

    private static Set<Key> keys(Collection<VulnerabilityChecklistItem> items) {
        Set<Key> keys = new HashSet<>();
        if (items != null) {
            items.stream().filter(Objects::nonNull).map(Key::of).forEach(keys::add);
        }
        return keys;
    }
}
