package com.faction.clientportal.service;

import com.faction.clientportal.dto.CreateReportTemplateRequest;
import com.faction.clientportal.dto.ReportTemplateDto;
import com.faction.clientportal.dto.UpdateReportTemplateRequest;
import com.faction.clientportal.edition.EditionPolicy;
import com.faction.clientportal.edition.UnrestrictedEditionPolicy;
import com.faction.clientportal.model.AssessmentType;
import com.faction.clientportal.model.ReportTemplate;
import com.faction.clientportal.repository.AssessmentRepository;
import com.faction.clientportal.repository.AssessmentTypeRepository;
import com.faction.clientportal.repository.ReportTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * How the checklist and bar-chart rendering options a template carries survive the trip
 * between the Report Designer and the renderers.
 *
 * <p>These used to be an uploaded extension's own settings. Now they are columns on the
 * template, which means every path that builds a template has to carry them: miss one and
 * the designer silently saves into nothing, which compiles and passes every other test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportTemplateRenderConfigTest {

    @Mock private ReportTemplateRepository reportTemplateRepository;
    @Mock private AssessmentTypeRepository assessmentTypeRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private StorageService storageService;
    @Spy private EditionPolicy editionPolicy = new UnrestrictedEditionPolicy();

    @InjectMocks private ReportTemplateService service;

    private static final Map<String, String> CHECKLIST =
            Map.of("passText", "Not Vulnerable", "passCellColour", "#92D050", "showComments", "false");
    private static final Map<String, String> BAR_CHART =
            Map.of("width", "800", "colour.critical", "#C00000");

    private ReportTemplate existing;

    @BeforeEach
    void setUp() {
        when(assessmentTypeRepository.findById("type-1")).thenReturn(Optional.of(new AssessmentType()));
        when(reportTemplateRepository.existsByName(any())).thenReturn(false);
        when(reportTemplateRepository.save(any(ReportTemplate.class))).thenAnswer(i -> i.getArgument(0));

        existing = ReportTemplate.builder()
                .id("tmpl-1").name("Pentest").assessmentTypeId("type-1").version(3)
                .checklistConfig(new HashMap<>(CHECKLIST))
                .barChartConfig(new HashMap<>(BAR_CHART))
                .build();
        when(reportTemplateRepository.findById("tmpl-1")).thenReturn(Optional.of(existing));
    }

    private CreateReportTemplateRequest createRequest() {
        CreateReportTemplateRequest request = new CreateReportTemplateRequest();
        request.setName("Pentest");
        request.setAssessmentTypeId("type-1");
        return request;
    }

    @Test
    void createCarriesBothConfigsThroughToTheSavedTemplate() {
        CreateReportTemplateRequest request = createRequest();
        request.setChecklistConfig(new HashMap<>(CHECKLIST));
        request.setBarChartConfig(new HashMap<>(BAR_CHART));

        ReportTemplateDto saved = service.createReportTemplate(request, "user-1");

        assertThat(saved.getChecklistConfig()).containsAllEntriesOf(CHECKLIST);
        assertThat(saved.getBarChartConfig()).containsAllEntriesOf(BAR_CHART);
    }

    /** A template created before these columns existed must not hand the renderers a null. */
    @Test
    void createWithoutAnyConfigYieldsEmptyMapsRatherThanNull() {
        ReportTemplateDto saved = service.createReportTemplate(createRequest(), "user-1");

        assertThat(saved.getChecklistConfig()).isNotNull().isEmpty();
        assertThat(saved.getBarChartConfig()).isNotNull().isEmpty();
    }

    @Test
    void updateReplacesTheConfigItIsGiven() {
        UpdateReportTemplateRequest request = new UpdateReportTemplateRequest();
        request.setChecklistConfig(new HashMap<>(Map.of("passText", "OK")));

        ReportTemplateDto updated = service.updateReportTemplate("tmpl-1", request, "user-1");

        assertThat(updated.getChecklistConfig()).containsExactly(Map.entry("passText", "OK"));
    }

    /**
     * The designer sends whole sections at a time, so an update that touches only the CSS
     * arrives with both config maps null. Treating null as "clear it" would wipe the
     * checklist colours every time someone edited something else.
     */
    @Test
    void updateLeavesAConfigItWasNotGivenAlone() {
        UpdateReportTemplateRequest request = new UpdateReportTemplateRequest();
        request.setCss("body { color: red; }");

        ReportTemplateDto updated = service.updateReportTemplate("tmpl-1", request, "user-1");

        assertThat(updated.getChecklistConfig()).containsAllEntriesOf(CHECKLIST);
        assertThat(updated.getBarChartConfig()).containsAllEntriesOf(BAR_CHART);
    }

    @Test
    void cloneCopiesBothConfigs() {
        ReportTemplateDto clone = service.cloneReportTemplate("tmpl-1", "Pentest Copy", "user-1");

        assertThat(clone.getChecklistConfig()).containsAllEntriesOf(CHECKLIST);
        assertThat(clone.getBarChartConfig()).containsAllEntriesOf(BAR_CHART);
    }

    /**
     * The clone's maps are its own: the source's are a JSON-mapped collection on a managed
     * entity, and a shared reference would let an edit on either template change the other.
     */
    @Test
    void theClonesConfigIsNotSharedWithItsSource() {
        ReportTemplateDto clone = service.cloneReportTemplate("tmpl-1", "Pentest Copy", "user-1");

        existing.getChecklistConfig().put("passText", "changed after cloning");

        assertThat(clone.getChecklistConfig()).containsEntry("passText", "Not Vulnerable");
    }

    /** Editing the config is presentation, not a field change — it must not bump the version. */
    @Test
    void changingTheConfigDoesNotBumpTheTemplateVersion() {
        UpdateReportTemplateRequest request = new UpdateReportTemplateRequest();
        request.setBarChartConfig(new HashMap<>(Map.of("width", "1000")));

        ReportTemplateDto updated = service.updateReportTemplate("tmpl-1", request, "user-1");

        assertThat(updated.getVersion()).isEqualTo(3);
    }

    /** An entity from before the migration reads back as empty, never null. */
    @Test
    void anEntityWithNullConfigMapsToEmptyMaps() {
        ReportTemplate legacy = ReportTemplate.builder().id("old").name("Legacy").build();
        legacy.setChecklistConfig(null);
        legacy.setBarChartConfig(null);

        ReportTemplateDto dto = ReportTemplateDto.fromEntity(legacy);

        assertThat(dto.getChecklistConfig()).isNotNull().isEmpty();
        assertThat(dto.getBarChartConfig()).isNotNull().isEmpty();
    }
}
