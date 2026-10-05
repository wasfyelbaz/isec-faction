package com.faction.clientportal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A checklist item chosen on a finding, as a client sends it: the checklist template and the
 * question. The server resolves the names from the assessment's attached checklists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItemRef {
    private String templateId;
    private String questionId;
}
