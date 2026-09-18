package com.faction.clientportal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    private String name;

    private String description;

    private Map<String, String> fieldValues;

    /**
     * Who this client's finished reports go to. Omit for none.
     *
     * <p>Capped because the list is a letterhead, not a mailing list — past a couple of dozen
     * names it is being used for something this field cannot serve, and an unbounded JSONB column
     * on a row read by every organization screen is worth refusing rather than discovering.
     */
    @Valid
    @Size(max = OrganizationContactLimits.MAX_DISTRIBUTION_LIST,
            message = "A distribution list cannot hold more than "
                    + OrganizationContactLimits.MAX_DISTRIBUTION_LIST + " contacts")
    private List<ClientContactDto> distributionList;
}
