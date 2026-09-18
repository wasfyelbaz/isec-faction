package com.faction.clientportal.edition;

/**
 * A policy with every feature off, for tests that exercise the gating mechanism itself.
 *
 * <p>Mirrors {@link UnrestrictedEditionPolicy}. It exists because this fork's
 * {@link CommunityEditionPolicy} no longer refuses anything, so it can no longer stand in
 * for "a build where this feature is absent". The interceptor and the
 * {@code FeatureNotLicensedException} path still need to be proven to work — a gate nothing
 * tests is a gate that quietly stops gating.
 */
public class FeaturesOffEditionPolicy implements EditionPolicy {

    @Override
    public Edition edition() {
        return Edition.COMMUNITY;
    }

    @Override
    public boolean enabled(Feature feature) {
        return false;
    }

    @Override
    public int limit(Quota quota) {
        return switch (quota) {
            case AI_PROVIDERS -> 1;
            case AI_PROMPTS   -> 4;
            case EXTENSIONS   -> 2;
        };
    }
}
