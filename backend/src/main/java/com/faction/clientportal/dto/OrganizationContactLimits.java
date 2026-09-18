package com.faction.clientportal.dto;

/**
 * Bounds shared by the organization create and update requests.
 *
 * <p>Its own type because a {@code @Size} message is a compile-time constant expression: the
 * number has to be inlinable, so it cannot live on either request class without the other
 * importing it, and it must not be written twice — two limits that drift apart are two different
 * answers to the same question.
 */
public final class OrganizationContactLimits {

    /**
     * How many people a client's distribution list may name.
     *
     * <p>A report's distribution list is a letterhead, not a mailing list. Past a couple of dozen
     * names it is being used for something this field cannot serve, and the column is JSONB on a
     * row that every organization screen reads whole.
     */
    public static final int MAX_DISTRIBUTION_LIST = 50;

    private OrganizationContactLimits() {
    }
}
