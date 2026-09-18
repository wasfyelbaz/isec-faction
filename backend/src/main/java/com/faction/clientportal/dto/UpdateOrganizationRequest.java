package com.faction.clientportal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    private String name;

    private String description;

    private Map<String, String> fieldValues;

    /** Full replacement of the organization's remediation owners; null leaves them unchanged. */
    private java.util.List<String> remediationOwnerIds;

    /**
     * Full replacement of the client's distribution list.
     *
     * <p>Same contract as {@code remediationOwnerIds}: null leaves the stored list alone, an empty
     * list clears it. The distinction matters because the organization form is also submitted by
     * screens that never loaded this field — treating "absent" as "clear" would have them wipe a
     * list they were not editing.
     */
    @Valid
    @Size(max = OrganizationContactLimits.MAX_DISTRIBUTION_LIST,
            message = "A distribution list cannot hold more than "
                    + OrganizationContactLimits.MAX_DISTRIBUTION_LIST + " contacts")
    private java.util.List<ClientContactDto> distributionList;
}
