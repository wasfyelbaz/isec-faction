package com.faction.clientportal.util.reporting;

import com.faction.clientportal.model.FieldType;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rich text that ends with a table, placed into a table cell, must leave the cell ending with
 * a paragraph: otherwise Word and LibreOffice unwrap the outer table.
 */
class DocxUtilsCellTrailingParagraphTest {

    private static final ObjectFactory F = Context.getWmlObjectFactory();
    private static final String TABLE_HTML =
            "<table><tbody><tr><td>10.10.1.0/24</td><td>Server VLAN</td></tr></tbody></table>";

    private static P paragraph(String text) {
        P p = F.createP();
        org.docx4j.wml.R r = F.createR();
        Text t = F.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        return p;
    }

    private static ReportData data(String html) {
        return ReportData.builder()
                .vulnerabilities(new ArrayList<>())
                .fieldValues(Map.of("in_scope_ips", html))
                .fieldTypes(Map.of("in_scope_ips", FieldType.RICH_TEXT))
                .build();
    }

    /** A one-cell outer table (header row + the tagged cell), as the Network template's 3.1.1. */
    private static Tbl outerTable(String tag) {
        Tbl tbl = F.createTbl();
        Tr header = F.createTr();
        Tc h = F.createTc();
        h.getContent().add(paragraph("In Scope IPs"));
        header.getContent().add(h);
        Tr row = F.createTr();
        Tc c = F.createTc();
        c.getContent().add(paragraph(tag));
        row.getContent().add(c);
        tbl.getContent().add(header);
        tbl.getContent().add(row);
        return tbl;
    }

    private static Tc taggedCell(WordprocessingMLPackage pkg) {
        Tbl outer = (Tbl) XmlUtils.unwrap(pkg.getMainDocumentPart().getContent().get(0));
        Tr row = (Tr) XmlUtils.unwrap(outer.getContent().get(1));
        return (Tc) XmlUtils.unwrap(row.getContent().get(0));
    }

    @Test
    void cellEndingWithAnImportedTableGetsAClosingParagraph() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().getContent().add(outerTable("${in_scope_ips}"));

        new DocxUtils(pkg, data(TABLE_HTML)).generateDocx("");

        Tc cell = taggedCell(pkg);
        List<Object> content = cell.getContent();
        assertTrue(content.size() >= 2, "nested table plus the closing paragraph");
        boolean nested = content.stream().anyMatch(o -> XmlUtils.unwrap(o) instanceof Tbl);
        assertTrue(nested, "the typed table is nested in the cell");
        assertInstanceOf(P.class, XmlUtils.unwrap(content.get(content.size() - 1)), "cell ends with a paragraph");
        Tbl outer = (Tbl) XmlUtils.unwrap(pkg.getMainDocumentPart().getContent().get(0));
        assertEquals(2, outer.getContent().size(), "outer table keeps its rows");
    }

    @Test
    void cellWhoseRichTextAlreadyEndsWithAParagraphIsLeftAlone() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().getContent().add(outerTable("${in_scope_ips}"));

        new DocxUtils(pkg, data(TABLE_HTML + "<p>after</p>")).generateDocx("");

        List<Object> content = taggedCell(pkg).getContent();
        long paragraphsAfterTable = 0;
        boolean seenTable = false;
        for (Object o : content) {
            Object el = XmlUtils.unwrap(o);
            if (el instanceof Tbl) seenTable = true;
            else if (seenTable && el instanceof P) paragraphsAfterTable++;
        }
        assertEquals(1, paragraphsAfterTable, "no extra paragraph appended");
    }

    @Test
    void bodyParagraphsNeedNoClosingParagraph() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart mdp = pkg.getMainDocumentPart();
        mdp.getContent().add(paragraph("${in_scope_ips}"));

        new DocxUtils(pkg, data(TABLE_HTML)).generateDocx("");

        List<Object> content = mdp.getContent();
        assertInstanceOf(Tbl.class, XmlUtils.unwrap(content.get(content.size() - 1)), "body may end with the table");
    }
}
