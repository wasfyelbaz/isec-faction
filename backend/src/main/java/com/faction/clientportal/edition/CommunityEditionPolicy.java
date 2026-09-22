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
     * Fork-local divergence from upstream: every feature is on.
     *
     * <p>Upstream returns {@code false} here, because this is the seam the commercial overlay
     * supersedes. This fork is self-hosted under the Apache 2.0 licence with no overlay to
     * install, so the gate is simply open. Report sections are the reason — the iSec MAPT
     * template files its findings into named sections and renders nothing without them — but
     * the switch is deliberately blanket rather than per-feature.
     *
     * <p>Expect a merge conflict on this method when rebasing on upstream. Keep this version.
     */
    @Override
    public boolean enabled(Feature feature) {
        return true;
    }

    @Override
    public int limit(Quota quota) {
        return switch (quota) {
            case AI_PROVIDERS -> MAX_AI_PROVIDERS;
            case AI_PROMPTS   -> MAX_AI_PROMPTS;
        };
    }
}
