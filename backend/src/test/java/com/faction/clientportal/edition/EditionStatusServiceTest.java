package com.faction.clientportal.edition;

import com.faction.clientportal.dto.EditionStatusDto;
import com.faction.clientportal.edition.UnrestrictedEditionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditionStatusServiceTest {

    private static final String UPGRADE_URL = "https://example.test/contact";

    @Mock private QuotaUsageService quotaUsageService;

    private EditionStatusService serviceFor(EditionPolicy policy) {
        return serviceFor(policy, false);
    }

    private EditionStatusService serviceFor(EditionPolicy policy, boolean assessmentTypeMenu) {
        return new EditionStatusService(policy, quotaUsageService, UPGRADE_URL, assessmentTypeMenu);
    }

    /**
     * An install preference rather than an edition capability, so it rides alongside {@code features}
     * rather than inside it: every edition can turn the assessment type menu on, and no edition
     * grants it.
     */
    @Test
    void reportsTheAssessmentTypeMenuPreferenceSeparatelyFromEditionFeatures() {
        assertThat(serviceFor(new CommunityEditionPolicy(), true).status().getAssessmentTypeMenu())
                .isTrue();
        assertThat(serviceFor(new UnrestrictedEditionPolicy(), false).status().getAssessmentTypeMenu())
                .isFalse();
        assertThat(serviceFor(new CommunityEditionPolicy(), true).status().getFeatures())
                .doesNotContainKey("assessment_type_menu");
    }

    /**
     * Fork-local: features are all on, but the quota caps are untouched — this fork opened
     * the feature gate only, not the countable limits.
     */
    @Test
    void reportsEveryFeatureAsOnAndEveryCapForCommunity() {
        when(quotaUsageService.current(Quota.AI_PROVIDERS)).thenReturn(1L);
        when(quotaUsageService.current(Quota.AI_PROMPTS)).thenReturn(0L);

        EditionStatusDto status = serviceFor(new CommunityEditionPolicy()).status();

        assertThat(status.getEdition()).isEqualTo("COMMUNITY");
        assertThat(status.getFeatures()).hasSize(Feature.values().length).containsValue(true);
        // Every feature is reported on, and only the external portal comes back off — the
        // client sidebar and user form key off exactly this map.
        assertThat(status.getFeatures()).containsEntry("external_owners", false);
        assertThat(status.getFeatures()).containsOnlyKeys(
                java.util.Arrays.stream(Feature.values()).map(Feature::getKey).toArray(String[]::new));
        assertThat(status.getFeatures().entrySet().stream()
                .filter(e -> !e.getValue()).map(java.util.Map.Entry::getKey))
                .containsExactly("external_owners");
        assertThat(status.getLimits())
                .doesNotContainKey("users")
                .containsEntry("ai_providers", 1)
                .containsEntry("ai_prompts", 4);
        assertThat(status.getUsage()).containsEntry("ai_providers", 1L);
        assertThat(status.getUpgradeUrl()).isEqualTo(UPGRADE_URL);
    }

    /**
     * An unlimited quota is absent from {@code limits} rather than sent as MAX_VALUE, so
     * the UI can treat "no key" as "no cap" instead of rendering 2147483647 next to a
     * user count.
     */
    @Test
    void omitsLimitsEntirelyForEnterpriseButStillReportsUsage() {

        EditionStatusDto status = serviceFor(new UnrestrictedEditionPolicy()).status();

        assertThat(status.getEdition()).isEqualTo("ENTERPRISE");
        assertThat(status.getLimits()).isEmpty();
        assertThat(status.getFeatures()).doesNotContainValue(false);
        assertThat(status.getUsage()).hasSize(Quota.values().length);
    }
}
