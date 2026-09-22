package com.faction.clientportal.dto;

import com.faction.clientportal.model.ReportTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Full DTO for ReportTemplate with all details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplateDto {

    private String id;
    private String name;
    private String description;
    private String assessmentTypeId;
    private String css;
    private String font;

    // File metadata
    private String templateFileId;
    private String templateFileName;
    private Long templateFileSize;
    private String templateFileContentType;

    private Integer version;
    private String scoringType;

    @Builder.Default
    /** Labels and colours for the checklist tables this template renders. */
    private Map<String, String> checklistConfig = new HashMap<>();

    /** Colours, axis label and size for the severity bar chart. */
    private Map<String, String> barChartConfig = new HashMap<>();

    private List<String> sections = new ArrayList<>();

    @Builder.Default
    private List<UserDefinedFieldDto> userDefinedFields = new ArrayList<>();

    private Boolean active;

    // Audit fields
    private String createdBy;
    private String lastUpdatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /**
     * Convert from entity to DTO
     */
    public static ReportTemplateDto fromEntity(ReportTemplate entity) {
        if (entity == null) {
            return null;
        }
        return ReportTemplateDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .assessmentTypeId(entity.getAssessmentTypeId())
            .css(entity.getCss())
            .font(entity.getFont())
            .templateFileId(entity.getTemplateFileId())
            .templateFileName(entity.getTemplateFileName())
            .templateFileSize(entity.getTemplateFileSize())
            .templateFileContentType(entity.getTemplateFileContentType())
            .version(entity.getVersion())
            .scoringType(entity.getScoringType())
            .sections(entity.getSections() != null ? new ArrayList<>(entity.getSections()) : new ArrayList<>())
            .checklistConfig(entity.getChecklistConfig() != null
                ? new HashMap<>(entity.getChecklistConfig()) : new HashMap<>())
            .barChartConfig(entity.getBarChartConfig() != null
                ? new HashMap<>(entity.getBarChartConfig()) : new HashMap<>())
            .userDefinedFields(entity.getUserDefinedFields() != null
                ? entity.getUserDefinedFields().stream()
                    .map(UserDefinedFieldDto::fromEntity)
                    .collect(Collectors.toList())
                : new ArrayList<>())
            .active(entity.getActive())
            .createdBy(entity.getCreatedBy())
            .lastUpdatedBy(entity.getLastUpdatedBy())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .deletedAt(entity.getDeletedAt())
            .build();
    }
}
