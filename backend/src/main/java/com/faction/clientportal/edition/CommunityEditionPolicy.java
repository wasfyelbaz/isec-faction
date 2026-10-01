package com.faction.clientportal.edition;

import org.springframework.stereotype.Service;

/**
 * The open source edition: every paid feature off, every quota capped.
 *
 * <p>User seats are deliberately not among them — the open source edition takes as many
 * people as you have.
 *
 * <p>This is the <em>only</em> {@link EditionPolicy} in the open source build, and it
 * stays registered in the enterprise build too — the overlay's bean is {@code @Primary}
 * and wins injection. Deliberately not conditional on anything: a bean that disappears
 * under some ordering is how an install accidentally becomes unlicensed.
 */
@Service
public class CommunityEditionPolicy implements EditionPolicy {

    static final int MAX_AI_PROVIDERS = 1;
    static final int MAX_AI_PROMPTS   = 4;

    @Override
    public Edition edition() {
        return Edition.COMMUNITY;
    }

    /**
     * Fork-local divergence from upstream: every feature is on, except the external portal.
     *
     * <p>Upstream returns {@code false} throughout, because this is the seam the commercial
     * overlay supersedes. This fork is self-hosted under the Apache 2.0 licence with no overlay
     * to install, so the gate is simply open. Report sections are the reason — the iSec MAPT
     * template files its findings into named sections and renders nothing without them.
     *
     * <p>{@link Feature#EXTERNAL_OWNERS} is the one exception, and it is a product decision
     * rather than a licensing one: clients and application owners never sign in to this portal,
     * so there is no reason to be able to mint an account that only they would use. Off here,
     * no account can hold an {@code :org} or {@code :owned} scope, which leaves every external
     * branch in the services unreachable rather than deleted — upstream still develops against
     * that code, so removing it would only buy merge conflicts. It also stops
     * {@code BootstrapService} seeding the App Owner and Organization Read roles.
     *
     * <p>Expect a merge conflict on this method when rebasing on upstream. Keep this version.
     */
    @Override
    public boolean enabled(Feature feature) {
        return feature != Feature.EXTERNAL_OWNERS;
    }

    @Override
    public int limit(Quota quota) {
        return switch (quota) {
            case AI_PROVIDERS -> MAX_AI_PROVIDERS;
            case AI_PROMPTS   -> MAX_AI_PROMPTS;
        };
    }
}
