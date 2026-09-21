package com.faction.clientportal.util.reporting;

import com.faction.clientportal.model.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data container holding everything DocxUtils needs to populate a report template.
 * Decouples template-processing logic from Spring/MongoDB context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportData {

    private String assessmentId;
    private String assessmentName;
    private String applicationId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /** NATIVE, CVSS_31, or CVSS_40 — drives CVSS hyperlink behaviour */
    private String scoringType;

    private String remediationManagerName;
    private String assessmentTypeName;

    private List<ReportUser> assessors;

    /**
     * Assessment-level user-defined field values.
     * Key is the field's variableName; value is the stored content.
     */
    private Map<String, String> fieldValues;

    /**
     * Assessment-level user-defined field types (STRING, RICH_TEXT, DROPDOWN).
     * Key is the field's variableName.
     */
    private Map<String, FieldType> fieldTypes;

    /** Vulnerabilities in display order */
    private List<ReportVulnerability> vulnerabilities;

    /**
     * The assessment's report sections in template order, or empty when the assessment has
     * none — or when this edition does not include them, in which case every finding renders
     * under the implicit Default section whatever its own section says.
     */
    private List<String> sections;

    // ── the client (organization) the assessment belongs to ────────────────

    /** Client name, rendered by {@code ${asmtClient}}. Null when the assessment has no organization. */
    private String clientName;

    /**
     * Client-level custom field values (organization-scoped user defined fields), keyed by the
     * field's variableName. Rendered as {@code ${asmtClient_<variableName>}}.
     */
    private Map<String, String> clientFieldValues;

    /** Types of {@link #clientFieldValues}, keyed by variableName. */
    private Map<String, FieldType> clientFieldTypes;

    /** The client's distribution list, in the order it was entered. */
    private List<ReportContact> clientContacts;

    /** Client image bytes keyed by the image's name (for example {@code logo}). */
    private Map<String, byte[]> clientImageBytes;

    /** Content types of {@link #clientImageBytes}, keyed by image name. */
    private Map<String, String> clientImageContentTypes;

    // ── the assessment's checklists, summed over every checklist attached to it ──────────

    /**
     * Items answered PASS ("Secure" / "Not Vulnerable" in the iSec charts). Zero when the assessment
     * has no checklist attached, which draws an empty chart on purpose: a chart left showing the
     * template's placeholder numbers would read as real results.
     */
    private Integer checklistPassed;
    /** Items answered FAIL ("Vulnerable"); zero when no checklist is attached. */
    private Integer checklistFailed;
    /** Items answered N/A; zero when no checklist is attached. */
    private Integer checklistNotApplicable;

    /**
     * Inline image bytes keyed by image ID.
     * Used to embed images found in rich-text field content.
     */
    private Map<String, byte[]> inlineImageBytes;

    /**
     * Content-types for inline images, keyed by image ID.
     * Required to build correct data: URIs when embedding images.
     */
    private Map<String, String> inlineImageContentTypes;

    // ── helpers ────────────────────────────────────────────────────────────

    public String getFieldValue(String variableName) {
        if (fieldValues == null || variableName == null) return "";
        return fieldValues.getOrDefault(variableName, "");
    }

    public FieldType getFieldType(String variableName) {
        if (fieldTypes == null || variableName == null) return FieldType.STRING;
        return fieldTypes.getOrDefault(variableName, FieldType.STRING);
    }

    public boolean isCvss31() {
        return "CVSS_31".equals(scoringType);
    }

    public String getClientFieldValue(String variableName) {
        if (clientFieldValues == null || variableName == null) return "";
        return clientFieldValues.getOrDefault(variableName, "");
    }

    // ── inner types ────────────────────────────────────────────────────────

    /** One row of the client's distribution list. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportContact {
        private String name;
        private String title;
        private String email;
    }

    // ── inner types ────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportUser {
        private String firstName;
        private String lastName;
        private String email;

        public String getFullName() {
            String f = firstName == null ? "" : firstName.trim();
            String l = lastName == null ? "" : lastName.trim();
            return (f + " " + l).trim();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportVulnerability {
        private String id;
        private String name;

        /**
         * What this installation calls the severity — "Critical" by default, but whatever the
         * terminology config says. This is the string templates render via {@code ${severity}}.
         */
        private String severity;

        /**
         * The unchanging enum name: CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL, or "" when unset.
         *
         * <p>Kept separate from {@link #severity} because the DOCX templates key off it — the
         * {@code ${riskCountN}} tallies and the {@code ${[asmtCRITICAL]}} finding loops. Matching
         * those on the display string would mean that renaming Critical to "Sev-1" silently
         * reported zero findings in every report built from an existing template.
         */
        private String severityKey;

        private String likelihood;
        private String impact;
        private Double cvssScore;
        private String cvssString;
        private String assetLocation;
        private String description;
        private String recommendation;
        private String details;
        private String trackingId;
        private LocalDateTime openedAt;
        private LocalDateTime closedAt;
        private LocalDateTime closedInDevAt;
        private LocalDateTime closedInStagingAt;
        private String categoryName;
        /** The report section this finding is filed under; null or blank means Default. */
        private String section;

        /**
         * Vulnerability-level user-defined field values.
         * Key is the field's variableName.
         */
        private Map<String, String> fieldValues;

        /**
         * Vulnerability-level user-defined field types.
         * Key is the field's variableName.
         */
        private Map<String, FieldType> fieldTypes;

        public String getCvssScoreStr() {
            return cvssScore == null ? "" : String.valueOf(cvssScore);
        }

        public boolean isOpen() {
            return closedAt == null;
        }

        public String getFieldValue(String variableName) {
            if (fieldValues == null || variableName == null) return "";
            return fieldValues.getOrDefault(variableName, "");
        }

        public FieldType getFieldType(String variableName) {
            if (fieldTypes == null || variableName == null) return FieldType.STRING;
            return fieldTypes.getOrDefault(variableName, FieldType.STRING);
        }
    }
}
