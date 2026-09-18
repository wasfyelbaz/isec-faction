package com.faction.clientportal.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The encrypted report variant.
 *
 * <p>The property that matters is not "the bytes changed" but "the file cannot be opened
 * without the password". A PDF written with an empty password, or with encryption silently
 * skipped, still looks like a PDF and still differs from the input — so every assertion
 * here goes through an actual open attempt.
 */
class PdfBoxReportEncryptorTest {

    private final PdfBoxReportEncryptor encryptor = new PdfBoxReportEncryptor();

    /** A real, minimal PDF — PDFBox refuses to load a byte array that is not one. */
    private static byte[] samplePdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Acme Mobile Banking - confidential");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── passwords ────────────────────────────────────────────────────────────

    @Test
    void generatesAPasswordOfTheRequestedLength() {
        assertThat(encryptor.generatePassword(20)).hasSize(20);
        assertThat(encryptor.generatePassword(1)).hasSize(1);
    }

    @Test
    void omitsCharactersThatCannotBeTranscribed() {
        String joined = String.join("", java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> encryptor.generatePassword(32)).toList());
        assertThat(joined)
                .as("0/O and 1/l/I are ambiguous when a password is read aloud or off a screen")
                .doesNotContain("0").doesNotContain("O")
                .doesNotContain("1").doesNotContain("l").doesNotContain("I");
    }

    @Test
    void passwordsDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) seen.add(encryptor.generatePassword(16));
        assertThat(seen).as("a repeat at this length means the source is not random").hasSize(500);
    }

    @Test
    void refusesANonPositiveLength() {
        assertThatThrownBy(() -> encryptor.generatePassword(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encryptor.generatePassword(-8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── encryption ───────────────────────────────────────────────────────────

    @Test
    void theEncryptedPdfCannotBeOpenedWithoutThePassword() throws Exception {
        byte[] encrypted = encryptor.encrypt(samplePdf(), "CorrectHorseBattery");

        assertThatThrownBy(() -> PDDocument.load(new ByteArrayInputStream(encrypted)).close())
                .as("loading with no password must fail")
                .isInstanceOf(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class);

        assertThatThrownBy(() -> PDDocument.load(new ByteArrayInputStream(encrypted), "wrong").close())
                .as("loading with the wrong password must fail")
                .isInstanceOf(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class);
    }

    @Test
    void theEncryptedPdfOpensWithThePasswordAndKeepsItsContent() throws Exception {
        byte[] encrypted = encryptor.encrypt(samplePdf(), "CorrectHorseBattery");

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(encrypted), "CorrectHorseBattery")) {
            assertThat(doc.isEncrypted()).isTrue();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertThat(text).contains("Acme Mobile Banking");
        }
    }

    @Test
    void usesAes256() throws Exception {
        byte[] encrypted = encryptor.encrypt(samplePdf(), "CorrectHorseBattery");

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(encrypted), "CorrectHorseBattery")) {
            assertThat(doc.getEncryption().getVersion())
                    .as("V5 is AES-256; anything lower is RC4 or AES-128")
                    .isEqualTo(5);
        }
    }

    /**
     * The recipient is the client reading their own report: reading and printing must work.
     * Only silent alteration of a signed-off deliverable is withheld.
     */
    @Test
    void theClientCanStillReadAndPrintButNotModify() throws Exception {
        byte[] encrypted = encryptor.encrypt(samplePdf(), "CorrectHorseBattery");

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(encrypted), "CorrectHorseBattery")) {
            assertThat(doc.getCurrentAccessPermission().canPrint()).isTrue();
            assertThat(doc.getCurrentAccessPermission().canExtractContent()).isTrue();
            assertThat(doc.getCurrentAccessPermission().canModify()).isFalse();
        }
    }

    @Test
    void refusesAnEmptyPasswordRatherThanProducingAnOpenFile() throws Exception {
        byte[] pdf = samplePdf();
        assertThatThrownBy(() -> encryptor.encrypt(pdf, ""))
                .as("an empty password yields a PDF that opens with no prompt")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encryptor.encrypt(pdf, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesEmptyInput() {
        assertThatThrownBy(() -> encryptor.encrypt(new byte[0], "pw"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encryptor.encrypt(null, "pw"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aGeneratedPasswordRoundTrips() throws Exception {
        String password = encryptor.generatePassword(24);
        byte[] encrypted = encryptor.encrypt(samplePdf(), password);

        assertThatCode(() -> PDDocument.load(new ByteArrayInputStream(encrypted), password).close())
                .doesNotThrowAnyException();
    }
}
