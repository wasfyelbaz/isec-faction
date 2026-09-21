package com.faction.clientportal.util.reporting;

import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code ${cvssLink ...}}: the NVD calculator link built from the finding's own vector. */
class DocxUtilsCvssLinkTest {

    private static final ObjectFactory F = Context.getWmlObjectFactory();
    private static final String VECTOR = "AV:A/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H";
    private static final String NVD31 = "https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator?vector=AV:A%2FAC:H%2FPR:N%2FUI:N%2FS:U%2FC:H%2FI:H%2FA:H&version=3.1";

    private static R run(String text, boolean bold) {
        R r = F.createR();
        if (bold) {
            RPr rPr = F.createRPr();
            rPr.setB(new BooleanDefaultTrue());
            r.setRPr(rPr);
        }
        Text t = F.createText();
        t.setValue(text);
        t.setSpace("preserve");
        r.getContent().add(t);
        return r;
    }

    /** {@code ${cvssScore} (<hyperlink>${cvssLink LABEL}</hyperlink>)}, the iSec finding-table cell. */
    private static P cvssParagraph(String tag) {
        P p = F.createP();
        p.getContent().add(run("${cvssScore} (", true));
        P.Hyperlink link = F.createPHyperlink();
        link.setId("rId999");
        link.getContent().add(run(tag, true));
        p.getContent().add(link);
        p.getContent().add(run(")", true));
        return p;
    }

    private static ReportData data(String scoringType, String vector) {
        return ReportData.builder()
                .scoringType(scoringType)
                .vulnerabilities(new ArrayList<>(List.of(ReportData.ReportVulnerability.builder()
                        .id("v1").name("SMB Signing Not Required").severity("High").severityKey("HIGH")
                        .cvssScore(8.1).cvssString(vector).build())))
                .build();
    }

    private static List<P.Hyperlink> links(WordprocessingMLPackage pkg) {
        List<P.Hyperlink> out = new ArrayList<>();
        new TraversalUtil(pkg.getMainDocumentPart().getContent(), new TraversalUtil.CallbackImpl() {
            @Override
            public List<Object> apply(Object o) {
                if (o instanceof P.Hyperlink h) out.add(h);
                return null;
            }

            @Override
            public boolean shouldTraverse(Object o) {
                return true;
            }
        });
        return out;
    }

    private static String text(Object node) {
        java.io.StringWriter sw = new java.io.StringWriter();
        try {
            org.docx4j.TextUtils.extractText(node, sw);
        } catch (Exception ignored) {
        }
        return sw.toString();
    }

    private static String target(WordprocessingMLPackage pkg, P.Hyperlink h) {
        return pkg.getMainDocumentPart().getRelationshipsPart().getRelationshipByID(h.getId()).getTarget();
    }

    @Test
    void findingsBlockLinksTheLabelToTheNvdCalculator() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart mdp = pkg.getMainDocumentPart();
        mdp.addParagraphOfText("${fiBegin}");
        mdp.addParagraphOfText("${vulnName}");
        mdp.getContent().add(cvssParagraph("${cvssLink View CVSS Metrics}"));
        mdp.addParagraphOfText("${fiEnd}");

        new DocxUtils(pkg, data("CVSS_31", VECTOR)).generateDocx("");

        List<P.Hyperlink> links = links(pkg);
        assertEquals(1, links.size());
        assertEquals("View CVSS Metrics", text(links.get(0)));
        assertEquals(NVD31, target(pkg, links.get(0)));
        String xml = XmlUtils.marshaltoString(mdp.getJaxbElement(), true, false);
        assertTrue(xml.contains("8.1 ("), "score printed before the link");
        assertTrue(xml.contains("<w:b/>"), "the run's bold survives");
    }

    @Test
    void tableRowLinksToo() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart mdp = pkg.getMainDocumentPart();
        Tbl tbl = F.createTbl();
        Tr config = F.createTr();
        Tc c1 = F.createTc();
        c1.getContent().add(paragraph("${vulnTable}"));
        config.getContent().add(c1);
        Tr loop = F.createTr();
        Tc c2 = F.createTc();
        c2.getContent().add(paragraph("${loop}${vulnName}"));
        Tc c3 = F.createTc();
        c3.getContent().add(cvssParagraph("${cvssLink}"));
        loop.getContent().add(c2);
        loop.getContent().add(c3);
        tbl.getContent().add(config);
        tbl.getContent().add(loop);
        mdp.getContent().add(tbl);

        new DocxUtils(pkg, data("CVSS_31", VECTOR)).generateDocx("");

        List<P.Hyperlink> links = links(pkg);
        assertEquals(1, links.size());
        assertEquals(VECTOR, text(links.get(0)), "no label: the vector is the link text");
        assertEquals(NVD31, target(pkg, links.get(0)));
    }

    private static P paragraph(String text) {
        P p = F.createP();
        p.getContent().add(run(text, false));
        return p;
    }

    @Test
    void vectorPrefixSelectsTheVersionAndIsStripped() {
        assertEquals(NVD31, DocxUtils.nvdCalculatorUrl("CVSS:3.1/" + VECTOR, false));
        assertEquals("https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator?vector=AV:N%2FAC:L&version=3.0",
                DocxUtils.nvdCalculatorUrl("CVSS:3.0/AV:N/AC:L", true));
    }

    @Test
    void cvss40TemplatesUseTheV4Calculator() {
        assertEquals("https://nvd.nist.gov/vuln-metrics/cvss/v4-calculator?vector=AV:N%2FAC:L%2FAT:N&version=4.0",
                DocxUtils.nvdCalculatorUrl("AV:N/AC:L/AT:N", false));
        assertEquals("https://nvd.nist.gov/vuln-metrics/cvss/v4-calculator?vector=AV:N%2FAC:L%2FAT:N&version=4.0",
                DocxUtils.nvdCalculatorUrl("CVSS:4.0/AV:N/AC:L/AT:N", true));
    }

    @Test
    void eachFindingGetsItsOwnCalculatorLink() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart mdp = pkg.getMainDocumentPart();
        mdp.addParagraphOfText("${fiBegin}");
        mdp.addParagraphOfText("${vulnName}");
        mdp.getContent().add(cvssParagraph("${cvssLink View CVSS Metrics}"));
        mdp.addParagraphOfText("${fiEnd}");

        ReportData two = ReportData.builder()
                .scoringType("CVSS_31")
                .vulnerabilities(new ArrayList<>(List.of(
                        ReportData.ReportVulnerability.builder().id("v1").name("A").severity("High").severityKey("HIGH")
                                .cvssScore(8.1).cvssString(VECTOR).build(),
                        ReportData.ReportVulnerability.builder().id("v2").name("B").severity("Low").severityKey("LOW")
                                .cvssScore(3.1).cvssString("AV:N/AC:H/PR:L/UI:R/S:U/C:L/I:N/A:N").build())))
                .build();
        new DocxUtils(pkg, two).generateDocx("");

        List<P.Hyperlink> links = links(pkg);
        assertEquals(2, links.size(), "one link per cloned finding block");
        assertNotEquals(links.get(0).getId(), links.get(1).getId(), "a relationship of its own, not the template's");
        assertEquals(NVD31, target(pkg, links.get(0)));
        assertTrue(target(pkg, links.get(1)).contains("AV:N%2FAC:H"), target(pkg, links.get(1)));
        assertEquals("View CVSS Metrics", text(links.get(0)));
        assertEquals("View CVSS Metrics", text(links.get(1)));
    }

    @Test
    void emptyVectorLinksToTheBareCalculator() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart mdp = pkg.getMainDocumentPart();
        mdp.addParagraphOfText("${fiBegin}");
        mdp.getContent().add(cvssParagraph("${cvssLink View CVSS Metrics}"));
        mdp.addParagraphOfText("${fiEnd}");

        new DocxUtils(pkg, data("CVSS_31", null)).generateDocx("");

        P.Hyperlink link = links(pkg).get(0);
        assertNotNull(link);
        assertEquals("View CVSS Metrics", text(link));
        assertEquals("https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator", target(pkg, link));
    }
}
