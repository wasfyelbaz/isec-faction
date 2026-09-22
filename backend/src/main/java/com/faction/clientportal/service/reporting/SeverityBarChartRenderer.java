package com.faction.clientportal.service.reporting;

import com.faction.clientportal.model.Vulnerability;
import com.faction.clientportal.model.VulnerabilitySeverity;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the severity bar chart a template asks for with {@code ${faction-bar-chart}}.
 *
 * <p>Ported from the Faction-Vulnerability-Bar-Chart App Store extension, which produced
 * the same JFreeChart PNG embedded as a base64 {@code data:} URI. It runs in-process now:
 * the chart is a fixed piece of the report, not something an operator should have to
 * upload a JAR to obtain.
 *
 * <p>One count per severity, ordered highest first, so the chart reads the same way as the
 * findings tables beside it.
 */
@Slf4j
@Service
public class SeverityBarChartRenderer {

    /** The placeholder body, without {@code ${}}. */
    public static final String TOKEN = "faction-bar-chart";

    /** Highest first — the order the report presents severities everywhere else. */
    private static final List<VulnerabilitySeverity> ORDER = List.of(
            VulnerabilitySeverity.CRITICAL,
            VulnerabilitySeverity.HIGH,
            VulnerabilitySeverity.MEDIUM,
            VulnerabilitySeverity.LOW,
            VulnerabilitySeverity.INFORMATIONAL);

    /**
     * The chart as an {@code <img>} carrying a base64 PNG, or null when the token is not
     * this one. Null rather than empty so the caller can offer the placeholder elsewhere.
     */
    public String render(String token, List<Vulnerability> vulns, BarChartOptions options) {
        if (!TOKEN.equals(token)) return null;
        try {
            byte[] png = chartPng(vulns, options);
            return "<img class=\"faction-vuln-chart\" src=\"data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(png) + "\"/>";
        } catch (IOException e) {
            // A report without its chart is worth more than no report: the section renders
            // empty and the failure is in the log, rather than the whole generation dying.
            log.warn("Severity bar chart could not be rendered: {}", e.getMessage());
            return "";
        }
    }

    private byte[] chartPng(List<Vulnerability> vulns, BarChartOptions options) throws IOException {
        Map<VulnerabilitySeverity, Integer> counts = new LinkedHashMap<>();
        for (VulnerabilitySeverity severity : ORDER) counts.put(severity, 0);
        if (vulns != null) {
            for (Vulnerability v : vulns) {
                if (v.getSeverity() != null) counts.computeIfPresent(v.getSeverity(), (k, n) -> n + 1);
            }
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (VulnerabilitySeverity severity : ORDER) {
            dataset.addValue(counts.get(severity), options.yAxisLabel(), label(severity));
        }

        JFreeChart chart = ChartFactory.createBarChart(
                null, null, options.yAxisLabel(), dataset,
                PlotOrientation.VERTICAL, false, false, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new Color(0xD0, 0xD0, 0xD0));

        // Counts are whole findings: a "2.5 Critical" tick would be nonsense.
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        // The default painter puts a gradient sheen on every bar, which fights the flat
        // severity colours the rest of the report uses.
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(options.showBorders());
        renderer.setDefaultOutlineStroke(new BasicStroke(1.0f));
        renderer.setMaximumBarWidth(0.12);

        Font font = new Font(options.font(), Font.PLAIN, 12);
        plot.getDomainAxis().setTickLabelFont(font);
        range.setTickLabelFont(font);
        range.setLabelFont(new Font(options.font(), Font.BOLD, 12));

        for (int i = 0; i < ORDER.size(); i++) {
            Color colour = options.colourFor(ORDER.get(i));
            renderer.setSeriesPaint(i, colour);
            renderer.setSeriesOutlinePaint(i, options.borderFor(ORDER.get(i)));
        }
        // One series holding every severity means per-bar colour has to come from the
        // renderer rather than the series index.
        plot.setRenderer(new PerCategoryColourRenderer(options, renderer));

        if (options.showNumbers()) {
            BarRenderer active = (BarRenderer) plot.getRenderer();
            active.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
            active.setDefaultItemLabelsVisible(true);
            active.setDefaultItemLabelFont(font);
            active.setDefaultPositiveItemLabelPosition(
                    new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(out, chart, options.width(), options.height());
        return out.toByteArray();
    }

    private static String label(VulnerabilitySeverity severity) {
        String name = severity.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /** Paints each bar from its severity rather than from the series it belongs to. */
    private static final class PerCategoryColourRenderer extends BarRenderer {
        private final transient BarChartOptions options;

        private PerCategoryColourRenderer(BarChartOptions options, BarRenderer copyFrom) {
            this.options = options;
            setBarPainter(copyFrom.getBarPainter());
            setShadowVisible(false);
            setDrawBarOutline(options.showBorders());
            setMaximumBarWidth(0.12);
        }

        @Override
        public java.awt.Paint getItemPaint(int row, int column) {
            return options.colourFor(ORDER.get(Math.min(column, ORDER.size() - 1)));
        }

        @Override
        public java.awt.Paint getItemOutlinePaint(int row, int column) {
            return options.borderFor(ORDER.get(Math.min(column, ORDER.size() - 1)));
        }
    }

    /**
     * Per-template chart options, read from {@code ReportTemplate.barChartConfig}.
     *
     * <p>Defaults are the house severity palette, so the chart matches the findings tables
     * and the portal without anyone configuring anything.
     */
    public record BarChartOptions(
            String yAxisLabel, String font, int width, int height,
            boolean showNumbers, boolean showBorders,
            Map<VulnerabilitySeverity, Color> colours,
            Map<VulnerabilitySeverity, Color> borders) {

        private static final Map<VulnerabilitySeverity, String> DEFAULT_COLOURS = Map.of(
                VulnerabilitySeverity.CRITICAL, "C00000",
                VulnerabilitySeverity.HIGH, "FFC000",
                VulnerabilitySeverity.MEDIUM, "FFFF00",
                VulnerabilitySeverity.LOW, "00B050",
                VulnerabilitySeverity.INFORMATIONAL, "00B0F0");

        public static BarChartOptions from(Map<String, String> config) {
            Map<String, String> c = config == null ? Map.of() : config;
            Map<VulnerabilitySeverity, Color> colours = new LinkedHashMap<>();
            Map<VulnerabilitySeverity, Color> borders = new LinkedHashMap<>();
            for (VulnerabilitySeverity severity : ORDER) {
                String key = severity.name().toLowerCase();
                Color fill = colour(c.get("colour." + key), DEFAULT_COLOURS.get(severity));
                colours.put(severity, fill);
                borders.put(severity, colour(c.get("border." + key), null) == null
                        ? fill.darker() : colour(c.get("border." + key), DEFAULT_COLOURS.get(severity)));
            }
            return new BarChartOptions(
                    value(c, "yAxisLabel", "Number of Findings"),
                    value(c, "font", "Arial"),
                    number(c.get("width"), 600),
                    number(c.get("height"), 400),
                    !"false".equalsIgnoreCase(value(c, "showNumbers", "true")),
                    !"false".equalsIgnoreCase(value(c, "showBorders", "true")),
                    colours, borders);
        }

        Color colourFor(VulnerabilitySeverity severity) {
            return colours.getOrDefault(severity, Color.GRAY);
        }

        Color borderFor(VulnerabilitySeverity severity) {
            return borders.getOrDefault(severity, Color.DARK_GRAY);
        }

        private static String value(Map<String, String> config, String key, String fallback) {
            String v = config.get(key);
            return v == null || v.isBlank() ? fallback : v;
        }

        private static int number(String raw, int fallback) {
            if (raw == null || raw.isBlank()) return fallback;
            try {
                int n = Integer.parseInt(raw.trim());
                // A zero or negative dimension makes JFreeChart throw; a wild one makes a
                // 100MB PNG. Neither should be reachable from a text box in the UI.
                return n < 100 || n > 4000 ? fallback : n;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        /** Accepts {@code #RRGGBB} or bare {@code RRGGBB}; anything else takes the fallback. */
        private static Color colour(String raw, String fallback) {
            String hex = raw == null || raw.isBlank() ? fallback : raw.trim();
            if (hex == null) return null;
            if (hex.startsWith("#")) hex = hex.substring(1);
            try {
                return new Color(Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
                return fallback == null ? null : new Color(Integer.parseInt(fallback, 16));
            }
        }
    }
}
