package com.faction.clientportal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating an existing report template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportTemplateRequest {

    @Size(max = 255, message = "Template name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private String assessmentTypeId;

    @Size(max = 10485760, message = "CSS must not exceed 10MB")
    private String css;

    @Size(max = 255, message = "Font must not exceed 255 characters")
    private String font;

    /**
     * Labels and colours for the checklist tables this template renders.
     *
     * <p>Null by default, like every other optional field here: the designer sends one
     * section at a time, so an update that only touches the CSS arrives without this.
     * Defaulting to an empty map would make "absent" indistinguishable from "clear it"
     * and wipe the saved colours on every unrelated edit.
     */
    private Map<String, String> checklistConfig;

    /** Colours, axis label and size for the severity bar chart. Null means "leave alone". */
    private Map<String, String> barChartConfig;

    private List<String> sections;

    @Valid
    private List<UserDefinedFieldDto> userDefinedFields;

    private Boolean active;

    private String scoringType;
}
