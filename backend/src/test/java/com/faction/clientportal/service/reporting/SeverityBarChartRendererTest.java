package com.faction.clientportal.service.reporting;

import com.faction.clientportal.model.Vulnerability;
import com.faction.clientportal.model.VulnerabilitySeverity;
import com.faction.clientportal.service.reporting.SeverityBarChartRenderer.BarChartOptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The severity chart a template draws with {@code ${faction-bar-chart}}.
 *
 * <p>Asserting on PNG bytes would pin the rendering library rather than the behaviour, so
 * these decode the image and check what a reader would notice: that there is a chart, that
 * it is the requested size, and that a configured value actually reaches it.
 */
class SeverityBarChartRendererTest {

    private final SeverityBarChartRenderer renderer = new SeverityBarChartRenderer();
    private final BarChartOptions defaults = BarChartOptions.from(Map.of());

    private static Vulnerability vuln(VulnerabilitySeverity severity) {
        return Vulnerability.builder().id("v").name("finding").severity(severity).build();
    }

    private static List<Vulnerability> sample() {
        return List.of(vuln(VulnerabilitySeverity.CRITICAL),
                       vuln(VulnerabilitySeverity.HIGH),
                       vuln(VulnerabilitySeverity.HIGH),
                       vuln(VulnerabilitySeverity.LOW));
    }

    private static BufferedImage decode(String html) throws Exception {
        assertThat(html).startsWith("<img").contains("data:image/png;base64,");
        String b64 = html.substring(html.indexOf("base64,") + 7, html.lastIndexOf('"'));
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
    }

    @Test
    void rendersAPngForItsOwnToken() throws Exception {
        BufferedImage image = decode(renderer.render("faction-bar-chart", sample(), defaults));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(600);
        assertThat(image.getHeight()).isEqualTo(400);
    }

    /** Null, not empty: an unclaimed placeholder must stay available to other renderers. */
    @Test
    void returnsNullForAnyOtherToken() {
        assertThat(renderer.render("checklist-owasp-api-top-10", sample(), defaults)).isNull();
        assertThat(renderer.render("vulnName", sample(), defaults)).isNull();
    }

    @Test
    void honoursConfiguredDimensions() throws Exception {
        BarChartOptions sized = BarChartOptions.from(Map.of("width", "800", "height", "300"));

        BufferedImage image = decode(renderer.render("faction-bar-chart", sample(), sized));

        assertThat(image.getWidth()).isEqualTo(800);
        assertThat(image.getHeight()).isEqualTo(300);
    }

    /**
     * Dimensions come from a text box in the report designer. A zero makes JFreeChart
     * throw and a very large one allocates a bitmap big enough to matter, so both fall
     * back rather than reaching the renderer.
     */
    @Test
    void refusesAbsurdDimensionsAndFallsBack() throws Exception {
        for (Map<String, String> bad : List.of(
                Map.of("width", "0"), Map.of("width", "-40"),
                Map.of("height", "99999"), Map.of("width", "not a number"))) {
            BufferedImage image = decode(renderer.render("faction-bar-chart", sample(), BarChartOptions.from(bad)));
            assertThat(image.getWidth()).isBetween(100, 4000);
            assertThat(image.getHeight()).isBetween(100, 4000);
        }
    }

    @Test
    void acceptsColoursWithOrWithoutAHash() throws Exception {
        for (String value : List.of("#123456", "123456")) {
            String html = renderer.render("faction-bar-chart", sample(),
                    BarChartOptions.from(Map.of("colour.critical", value)));
            assertThat(decode(html)).isNotNull();
        }
    }

    @Test
    void aMalformedColourDoesNotStopTheChart() throws Exception {
        String html = renderer.render("faction-bar-chart", sample(),
                BarChartOptions.from(Map.of("colour.critical", "not-a-colour")));

        assertThat(decode(html)).isNotNull();
    }

    /** An assessment with nothing found still gets its chart, with every bar at zero. */
    @Test
    void anAssessmentWithNoFindingsStillRenders() throws Exception {
        assertThat(decode(renderer.render("faction-bar-chart", List.of(), defaults))).isNotNull();
        assertThat(decode(renderer.render("faction-bar-chart", null, defaults))).isNotNull();
    }

    @Test
    void aFindingWithNoSeverityIsSkippedRatherThanThrowing() throws Exception {
        List<Vulnerability> withNull = List.of(vuln(VulnerabilitySeverity.HIGH), vuln(null));

        assertThat(decode(renderer.render("faction-bar-chart", withNull, defaults))).isNotNull();
    }

    @Test
    void chartsOfDifferentDataDiffer() {
        String few = renderer.render("faction-bar-chart", List.of(vuln(VulnerabilitySeverity.LOW)), defaults);
        String many = renderer.render("faction-bar-chart", sample(), defaults);

        assertThat(few).isNotEqualTo(many);
    }
}
