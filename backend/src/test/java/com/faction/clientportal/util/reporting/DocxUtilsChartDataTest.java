package com.faction.clientportal.util.reporting;

import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.dml.chart.CTBarChart;
import org.docx4j.dml.chart.CTBarSer;
import org.docx4j.dml.chart.CTChartSpace;
import org.docx4j.dml.chart.CTNumVal;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.DrawingML.Chart;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.EmbeddedPackagePart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ${chartData ...}} markers feed the Network template's two native bar charts (the
 * fixture holds them with their embedded workbooks): cached values and workbook cells move together.
 */
class DocxUtilsChartDataTest {

    private static WordprocessingMLPackage fixture() throws Exception {
        try (InputStream in = DocxUtilsChartDataTest.class.getResourceAsStream("/reporting/network-charts.docx")) {
            assertNotNull(in, "fixture missing");
            return WordprocessingMLPackage.load(in);
        }
    }

    private static ReportData.ReportVulnerability finding(String name, String key, String label) {
        return ReportData.ReportVulnerability.builder().id(name).name(name).severityKey(key).severity(label).build();
    }

    private static ReportData data() {
        return ReportData.builder()
                .vulnerabilities(new ArrayList<>(List.of(
                        finding("a", "HIGH", "High"), finding("b", "MEDIUM", "Medium"), finding("c", "LOW", "Low"),
                        finding("d", "LOW", "Low"))))
                .checklistPassed(9).checklistFailed(4).checklistNotApplicable(0)
                .build();
    }

    private static Chart chart(WordprocessingMLPackage pkg, String name) throws Exception {
        return (Chart) pkg.getParts().get(new PartName("/word/charts/" + name));
    }

    /** series name -> cached values in point order */
    private static Map<String, List<String>> series(Chart chart) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        CTChartSpace space = chart.getContents();
        for (Object o : space.getChart().getPlotArea().getAreaChartOrArea3DChartOrLineChart()) {
            if (!(o instanceof CTBarChart bar)) continue;
            for (CTBarSer ser : bar.getSer()) {
                List<String> values = new ArrayList<>();
                for (CTNumVal pt : ser.getVal().getNumRef().getNumCache().getPt()) values.add(pt.getV());
                out.put(ser.getTx().getStrRef().getStrCache().getPt().get(0).getV(), values);
            }
        }
        return out;
    }

    /** cell ref -> value text of every numeric cell of the chart's embedded workbook, first sheet */
    private static Map<String, String> workbookCells(Chart chart) throws Exception {
        RelationshipsPart rels = chart.getRelationshipsPart();
        for (Relationship rel : rels.getRelationships().getRelationship()) {
            if (!(rels.getPart(rel) instanceof EmbeddedPackagePart wb)) continue;
            ByteBuffer buf = wb.getBuffer().duplicate();
            buf.rewind();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (!e.getName().equals("xl/worksheets/sheet1.xml")) continue;
                    String xml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> cells = new LinkedHashMap<>();
                    Matcher m = Pattern.compile("<c r=\"([A-Z]+\\d+)\"([^>]*)><v>([^<]*)</v>").matcher(xml);
                    while (m.find()) if (!m.group(2).contains("t=\"s\"")) cells.put(m.group(1), m.group(3));
                    return cells;
                }
            }
        }
        return Map.of();
    }

    private static List<String> paragraphTexts(WordprocessingMLPackage pkg) {
        List<String> out = new ArrayList<>();
        for (Object o : pkg.getMainDocumentPart().getContent()) {
            if (!(XmlUtils.unwrap(o) instanceof P p)) continue;
            StringWriter sw = new StringWriter();
            try {
                TextUtils.extractText(p, sw);
            } catch (Exception ignored) {
            }
            out.add(sw.toString().trim());
        }
        return out;
    }

    @Test
    void severityChartFollowsTheFindings() throws Exception {
        WordprocessingMLPackage pkg = fixture();
        new DocxUtils(pkg, data()).generateDocx("");

        Map<String, List<String>> s = series(chart(pkg, "chart2.xml"));
        assertEquals(List.of("0", "1", "1", "2", "0"), s.get("Findings"), "Critical, High, Medium, Low, Informational");
        Map<String, String> cells = workbookCells(chart(pkg, "chart2.xml"));
        assertEquals("0", cells.get("B2"));
        assertEquals("1", cells.get("B3"));
        assertEquals("1", cells.get("B4"));
        assertEquals("2", cells.get("B5"));
        assertEquals("0", cells.get("B6"));
    }

    @Test
    void eachFindingCountsOnceWhicheverSeverityFieldIsSet() throws Exception {
        WordprocessingMLPackage pkg = fixture();
        ReportData d = ReportData.builder()
                .vulnerabilities(new ArrayList<>(List.of(
                        finding("a", "HIGH", "High"),   // both set, and they are the same bucket
                        finding("b", null, "High"),     // label only
                        finding("c", "LOW", null))))    // key only
                .checklistPassed(1).checklistFailed(0).checklistNotApplicable(0)
                .build();

        new DocxUtils(pkg, d).generateDocx("");

        assertEquals(List.of("0", "2", "0", "1", "0"), series(chart(pkg, "chart2.xml")).get("Findings"),
                "Critical, High, Medium, Low, Informational");
    }

    @Test
    void checklistChartFollowsTheResponses() throws Exception {
        WordprocessingMLPackage pkg = fixture();
        new DocxUtils(pkg, data()).generateDocx("");

        Map<String, List<String>> s = series(chart(pkg, "chart1.xml"));
        assertEquals(List.of("9"), s.get("Secure"));
        assertEquals(List.of("4"), s.get("Vulnerable"));
        Map<String, String> cells = workbookCells(chart(pkg, "chart1.xml"));
        assertEquals("9", cells.get("B2"), "Secure column");
        assertEquals("4", cells.get("C2"), "Vulnerable column");
    }

    @Test
    void markersAreRemovedAndTheRestOfTheDocumentStays() throws Exception {
        WordprocessingMLPackage pkg = fixture();
        new DocxUtils(pkg, data()).generateDocx("");

        List<String> texts = paragraphTexts(pkg);
        assertFalse(texts.stream().anyMatch(t -> t.contains("${chartData")), texts.toString());
        assertTrue(texts.contains("end"));
        String xml = XmlUtils.marshaltoString(pkg.getMainDocumentPart().getJaxbElement(), true, false);
        assertEquals(2, countMatches(xml, "r:id=\"rId2"), "both chart drawings still referenced");
    }

    @Test
    void withoutMarkersTheChartsKeepTheirTemplateNumbers() throws Exception {
        WordprocessingMLPackage pkg = fixture();
        pkg.getMainDocumentPart().getContent().removeIf(o ->
                XmlUtils.unwrap(o) instanceof P p && paragraphText(p).startsWith("${chartData"));
        new DocxUtils(pkg, data()).generateDocx("");

        assertEquals(List.of("0", "0", "1", "5", "1"), series(chart(pkg, "chart2.xml")).get("Findings"));
        assertEquals(List.of("14"), series(chart(pkg, "chart1.xml")).get("Secure"));
    }

    @Test
    void cellAddressesFollowTheSeriesFormula() {
        assertEquals("Sheet1!B2", DocxUtils.cellAt("Sheet1!$B$2:$B$6", 0));
        assertEquals("Sheet1!B5", DocxUtils.cellAt("Sheet1!$B$2:$B$6", 3));
        assertNull(DocxUtils.cellAt("Sheet1!$B$2:$B$6", 5));
        assertEquals("Sheet1!C2", DocxUtils.cellAt("Sheet1!$C$2", 0));
        assertNull(DocxUtils.cellAt("Sheet1!$C$2", 1));
        assertEquals("Sheet1!D3", DocxUtils.cellAt("Sheet1!$B$3:$F$3", 2));
    }

    private static String paragraphText(P p) {
        StringWriter sw = new StringWriter();
        try {
            TextUtils.extractText(p, sw);
        } catch (Exception ignored) {
        }
        return sw.toString().trim();
    }

    private static int countMatches(String s, String needle) {
        int n = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
