package com.faction.clientportal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One person on a client's distribution list — who the finished report goes to.
 *
 * <p>Deliberately not a {@link User}. These are the client's people: the security manager who
 * commissioned the work, the CISO who signs it off, the application owner who has to act on it.
 * Most of them will never have an account here, and requiring one to be named on a report would
 * mean creating dormant logins for people whose only involvement is receiving a PDF.
 *
 * <p>Stored as a JSONB list on the organization rather than as its own table, the same way
 * {@link Stakeholder} is stored on an assessment: the list is small, always read whole with its
 * owner, and never queried across organizations.
 *
 * <p>{@code title} rather than {@link Stakeholder}'s {@code role} — a distribution list is read as
 * a letterhead ("Head of Information Security"), not as an engagement responsibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientContact {

    /** The person's name, as it should appear on the report. */
    private String name;

    /** Their job title at the client. Optional — plenty of lists are just names and addresses. */
    private String title;

    /** Where their copy goes. Optional: a list can record who receives it on paper. */
    private String email;
}
