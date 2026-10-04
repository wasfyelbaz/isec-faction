package com.faction.clientportal.util;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Round-trip tests for {@link LibreOfficeConverter}. These exercise the real
 * soffice binary and are skipped when LibreOffice is not installed (CI without
 * a LibreOffice layer). The docx → docx round-trip is what normalizes raw
 * docx4j output into a file Microsoft Word will open.
 */
class LibreOfficeConverterTest {

    private final LibreOfficeConverter converter = new LibreOfficeConverter();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(converter, "libreofficePath", "soffice");
        ReflectionTestUtils.setField(converter, "timeoutSeconds", 120);
        assumeTrue(sofficeAvailable(), "soffice not installed — skipping LibreOffice tests");
    }

    private boolean sofficeAvailable() {
        try {
            Process p = new ProcessBuilder("soffice", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] sampleDocx() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("Hello from the converter test");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }

    @Test
    void convertToDocx_roundTripsToLoadableDocx() throws Exception {
        byte[] result = converter.convertToDocx(sampleDocx());

        assertThat(result).isNotEmpty();
        // Valid zip container
        assertThat(new String(result, 0, 2)).isEqualTo("PK");
        // Still a loadable WordprocessingML package after the round-trip
        WordprocessingMLPackage reloaded =
                WordprocessingMLPackage.load(new ByteArrayInputStream(result));
        assertThat(reloaded.getMainDocumentPart()).isNotNull();
    }

    /**
     * LibreOffice's default DOCX filter stamps compatibilityMode 12 whatever the source said, so
     * Word opened every report in Compatibility Mode and laid it out by Word 2007's rules. The
     * round-trip must keep the mode the template was saved in.
     */
    @Test
    void convertToDocx_keepsTheTemplatesWordCompatibilityMode() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("Saved by a current Word");
        org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart settingsPart =
                new org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart();
        settingsPart.setJaxbElement((org.docx4j.wml.CTSettings) org.docx4j.XmlUtils.unwrap(org.docx4j.XmlUtils.unmarshalString(
                "<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:compat>"
                + "<w:compatSetting w:name=\"compatibilityMode\" w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"/>"
                + "</w:compat></w:settings>")));
        pkg.getMainDocumentPart().addTargetPart(settingsPart);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        pkg.save(source);

        byte[] result = converter.convertToDocx(source.toByteArray());

        String settings;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(result))) {
            java.util.zip.ZipEntry e;
            settings = null;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("word/settings.xml")) settings = new String(zip.readAllBytes());
            }
        }
        assertThat(settings).isNotNull();
        assertThat(settings).containsPattern("compatibilityMode\"[^>]*w:val=\"15\"");
    }

    @Test
    void convertToPdf_producesPdfBytes() throws Exception {
        byte[] result = converter.convertToPdf(sampleDocx());

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }
}
