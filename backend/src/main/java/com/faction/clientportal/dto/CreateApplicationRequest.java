package com.faction.clientportal.dto;

import com.faction.clientportal.model.ApplicationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApplicationRequest {

    @NotBlank(message = "Application name is required")
    private String name;

    private String appId;

    private String description;

    // Application URLs
    @Valid
    private List<ApplicationUrlDto> urls;

    // Stakeholders
    @Valid
    private List<StakeholderDto> stakeHolders;

    // Technologies
    private List<String> technologies;

    // Application Owner
    @Valid
    private AppOwnerDto appOwner;

    // Legacy owner fields (for backward compatibility)
    private String ownerName;
    @Email(message = "Invalid owner email format")
    private String ownerEmail;

    // Application Status
    private ApplicationStatus status;

    /**
     * The client this target belongs to. Required: an engagement traces to a client through its
     * target, so a target with no client is unreachable from the client it was actually for, and
     * silently pads every list it appears in.
     */
    @NotBlank(message = "Client is required")
    private String organizationId;

    /** Optional division within the organization; attribution only, not an access boundary. */
    private String subOrganizationId;

    // Region
    private String region;

    // Assessment-related fields (optional)
    private String applicationType;
    private String assessmentFrequency;
    private Integer customFrequencyMonths;
    private LocalDateTime lastAssessmentDate;

    private Map<String, String> fieldValues;
}
