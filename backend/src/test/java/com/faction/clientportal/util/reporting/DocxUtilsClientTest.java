package com.faction.clientportal.util.reporting;

import com.faction.clientportal.model.FieldType;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client package in a template: {@code ${asmtClient}}, the client's custom fields as
 * {@code ${asmtClient_<variableName>}}, the distribution list as a repeating table row or as a
 * block list, and {@code ${clientImage <name>}}.
 */
class DocxUtilsClientTest {

    /** 1x1 transparent PNG — the smallest thing the XHTML importer will embed. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static ReportData client() {
        return ReportData.builder()
                .vulnerabilities(new ArrayList<>())
                .clientName("BDC")
                .clientFieldValues(Map.of("legal_name", "Banque du Caire",
                                          "profile", "<p>A <b>retail</b> bank.</p>"))
                .clientFieldTypes(Map.of("legal_name", FieldType.STRING,
                                         "profile", FieldType.RICH_TEXT))
                .clientContacts(List.of(
                        ReportData.ReportContact.builder().name("Ahmed Ali").title("CISO").email("a.ali@bdc.example").build(),
                        ReportData.ReportContact.builder().name("Sara M.").title("").email("sara@bdc.example").build()))
                .clientImageBytes(Map.of("logo", PNG))
                .clientImageContentTypes(Map.of("logo", "image/png"))
                .build();
    }

    private static WordprocessingMLPackage generate(ReportData data, String... paragraphs) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        for (String p : paragraphs) main.addParagraphOfText(p);
        return new DocxUtils(pkg, data).generateDocx("");
    }

    private static String textOf(WordprocessingMLPackage pkg) {
        return XmlUtils.marshaltoString(pkg.getMainDocumentPart().getJaxbElement(), true, false);
    }

    /** Header row, a marker row, and the ${loop} row — the shape a template author builds. */
    private static Tbl contactTable(MainDocumentPart main) {
        ObjectFactory f = Context.getWmlObjectFactory();
        Tbl tbl = f.createTbl();
        tbl.getContent().add(row(f, main, "Name", "Title", "Email"));
        tbl.getContent().add(row(f, main, "${clientContactTable}", "", ""));
        tbl.getContent().add(row(f, main, "${loop}${count}. ${contactName}", "${contactTitle}", "${contactEmail}"));
        return tbl;
    }

    private static Tr row(ObjectFactory f, MainDocumentPart main, String... cells) {
        Tr tr = f.createTr();
        for (String text : cells) {
            Tc tc = f.createTc();
            tc.getContent().add(main.createParagraphOfText(text));
            tr.getContent().add(tc);
        }
        return tr;
    }

    private static Tbl firstTable(WordprocessingMLPackage pkg) {
        for (Object o : pkg.getMainDocumentPart().getContent()) {
            Object el = XmlUtils.unwrap(o);
            if (el instanceof Tbl) return (Tbl) el;
        }
        throw new AssertionError("no table in the generated document");
    }

    @Test
    void clientNameAndPlainFieldsResolveInline() throws Exception {
        WordprocessingMLPackage result = generate(client(),
                "Prepared for ${asmtClient} (${asmtClient_legal_name}).");
        String xml = textOf(result);
        assertThat(xml).contains("Prepared for BDC (Banque du Caire).");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void richTextClientFieldExpandsWhenAloneInItsParagraph() throws Exception {
        WordprocessingMLPackage result = generate(client(), "${asmtClient_profile}");
        String xml = textOf(result);
        assertThat(xml).contains("retail");
        assertThat(xml).doesNotContain("asmtClient_profile");
    }

    @Test
    void anAssessmentWithoutAClientRendersAnEmptyName() throws Exception {
        ReportData none = ReportData.builder().vulnerabilities(new ArrayList<>()).build();
        WordprocessingMLPackage result = generate(none, "Prepared for ${asmtClient}.");
        assertThat(textOf(result)).contains("Prepared for .");
    }

    @Test
    void theDistributionListRendersAsBlockLists() throws Exception {
        WordprocessingMLPackage result = generate(client(),
                "${clientContacts_Comma}", "${clientContacts_Lines}", "${clientContacts_Bullets}");
        String xml = textOf(result);
        assertThat(xml).contains("Ahmed Ali, Sara M.");
        // the title is skipped when the contact has none, the email is not
        assertThat(xml).contains("Ahmed Ali – CISO – a.ali@bdc.example");
        assertThat(xml).contains("Sara M. – sara@bdc.example");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void aContactTableRepeatsItsLoopRowOncePerContact() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        main.getContent().add(contactTable(main));
        WordprocessingMLPackage result = new DocxUtils(pkg, client()).generateDocx("");

        Tbl table = firstTable(result);
        // header + one row per contact; the marker row and the template row are gone
        assertThat(table.getContent()).hasSize(3);
        String xml = textOf(result);
        assertThat(xml).contains("1. Ahmed Ali").contains("CISO").contains("a.ali@bdc.example");
        assertThat(xml).contains("2. Sara M.").contains("sara@bdc.example");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void anEmptyDistributionListWritesTheNoContactsRow() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        main.getContent().add(contactTable(main));
        ReportData noContacts = ReportData.builder().vulnerabilities(new ArrayList<>())
                .clientName("BDC").clientContacts(List.of()).build();
        WordprocessingMLPackage result = new DocxUtils(pkg, noContacts).generateDocx("");

        assertThat(firstTable(result).getContent()).hasSize(2);
        String xml = textOf(result);
        assertThat(xml).contains("No contacts recorded for this client.");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void aClientImagePlaceholderBecomesAnEmbeddedDrawing() throws Exception {
        WordprocessingMLPackage result = generate(client(), "${clientImage logo}", "after the logo");
        String xml = textOf(result);
        assertThat(xml).contains("<w:drawing").contains("<wp:extent");
        assertThat(xml).contains("after the logo");
        assertThat(xml).doesNotContain("clientImage");
    }

    @Test
    void aFixedWidthIsHonouredOnTheImage() throws Exception {
        String natural = textOf(generate(client(), "${clientImage logo}"));
        String fixed   = textOf(generate(client(), "${clientImage logo width=300}"));
        // 300 px at 96 dpi = 2857500 EMU; the natural 1 px image is far smaller
        assertThat(fixed).contains("cx=\"2857500\"");
        assertThat(natural).doesNotContain("cx=\"2857500\"");
    }

    @Test
    void anImageTheClientDoesNotHaveLeavesNothingBehind() throws Exception {
        WordprocessingMLPackage result = generate(client(), "before", "${clientImage cover}", "after");
        String xml = textOf(result);
        assertThat(xml).contains("before").contains("after");
        assertThat(xml).doesNotContain("clientImage").doesNotContain("<w:drawing");
    }
}
