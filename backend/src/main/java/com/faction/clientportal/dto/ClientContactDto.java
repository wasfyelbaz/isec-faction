package com.faction.clientportal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry on a client's distribution list, over the wire.
 *
 * <p>Only the name is required. A list that records who receives a printed copy has no address to
 * give, and refusing that entry would push it into a free-text note where nothing can read it.
 * An address that <em>is</em> given must be well formed, though — a typo here is a report that
 * silently never arrives.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientContactDto {

    @NotBlank(message = "A distribution list contact needs a name")
    @Size(max = 255, message = "A contact name cannot be longer than 255 characters")
    private String name;

    @Size(max = 255, message = "A contact title cannot be longer than 255 characters")
    private String title;

    @Email(message = "A distribution list contact's email address is not valid")
    @Size(max = 255, message = "A contact email cannot be longer than 255 characters")
    private String email;
}
