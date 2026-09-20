package com.faction.clientportal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A font file uploaded for the PDF step.
 *
 * <p>A PDF can only be drawn in a font that exists on the server. The "Report Font" a template
 * names, and the fonts its DOCX uses, are otherwise just names: LibreOffice substitutes whatever it
 * has, which in the image is DejaVu Sans. This row records a file that closes that gap, uploaded
 * from the Report Designer so nobody has to rebuild the image or log in to the server to change a
 * font.
 *
 * <p>Same shape as {@link ClientImage}: the bytes live in object storage and the row records where.
 * {@link com.faction.clientportal.service.ReportFontInstaller} writes every row's file into the
 * server's font directory at startup and whenever the set changes, so a container rebuilt from the
 * image gets its fonts back from storage rather than losing them.
 *
 * <p>Keyed by the family and style read from the font itself ({@code Calibri} + {@code Bold}),
 * unique together, so uploading the same style again replaces it.
 */
@Entity
@Table(name = "report_fonts", indexes = {
    @Index(name = "idx_report_fonts_family_style", columnList = "family, style", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFont {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** The family name the font declares — what CSS and the Report Font field refer to. */
    @Column(nullable = false)
    private String family;

    /** The style the font declares: Regular, Bold, Italic, Bold Italic, Light… */
    @Column(nullable = false)
    private String style;

    /** Where the bytes are in object storage: {@code report-fonts/{id}/{file}}. */
    @Column(nullable = false)
    private String storageKey;

    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
