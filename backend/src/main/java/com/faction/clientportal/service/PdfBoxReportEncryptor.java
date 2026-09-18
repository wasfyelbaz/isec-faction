package com.faction.clientportal.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

/**
 * Password-protects the PDF variant of a report, using PDFBox.
 *
 * <p>Fork-local. Upstream ships only {@link UnavailableReportEncryptor}, which refuses —
 * the real encryptor lives in the commercial overlay, which this fork does not have. Since
 * {@code CommunityEditionPolicy} here reports every feature as available, the report
 * pipeline attempts the encrypted variant and the refusing stub is genuinely reached, so
 * something has to do the work.
 *
 * <p>{@code @Primary} so this supersedes the stub by injection, exactly as the overlay's
 * bean would. The stub is deliberately left in place: it is upstream's file, so leaving it
 * untouched keeps rebases clean, and it remains the right answer for any call site that
 * resolves {@link ReportEncryptor} without this bean present.
 */
@Slf4j
@Service
@Primary
public class PdfBoxReportEncryptor implements ReportEncryptor {

    /**
     * Unambiguous characters only. These passwords are read off a screen and typed into a
     * PDF reader, frequently over the phone, so 0/O and 1/l/I are omitted — a password that
     * cannot be transcribed is a support ticket, and the four characters cost about 0.3
     * bits each at this length.
     */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    /** {@link SecureRandom}, not {@code Random}: this is the only thing protecting the file. */
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generatePassword(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("password length must be positive, was " + length);
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    public byte[] encrypt(byte[] pdfBytes, String password) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("no PDF bytes to encrypt");
        }
        if (password == null || password.isEmpty()) {
            // Empty owner/user passwords produce a file that opens without prompting, which
            // would be an unencrypted report wearing the name of an encrypted one.
            throw new IllegalArgumentException("refusing to encrypt with an empty password");
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream(pdfBytes.length + 4096)) {

            AccessPermission permissions = new AccessPermission();
            // The recipient is the client: they may read, print and extract their own report.
            // What is withheld is silent alteration of a signed-off deliverable.
            permissions.setCanModify(false);
            permissions.setCanModifyAnnotations(false);
            permissions.setCanFillInForm(false);
            permissions.setCanAssembleDocument(false);

            // The owner password is random and immediately discarded. It must not equal the
            // user password: PDF readers grant full owner rights to whoever opens with the
            // owner password, so reusing it here would hand the recipient the very
            // permissions set above and make them decorative. Nobody needs owner rights on a
            // delivered report, so the value is never stored or returned.
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(generatePassword(32), password, permissions);
            policy.setEncryptionKeyLength(256);   // AES-256; PDF 2.0 / Acrobat X and later
            policy.setPreferAES(true);

            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        }
    }
}
