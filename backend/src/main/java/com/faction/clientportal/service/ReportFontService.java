package com.faction.clientportal.service;

import com.faction.clientportal.dto.ReportFontDto;
import com.faction.clientportal.exception.ResourceNotFoundException;
import com.faction.clientportal.model.ReportFont;
import com.faction.clientportal.repository.ReportFontRepository;
import com.faction.clientportal.util.FontFileInspector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The fonts uploaded for the PDF step — see {@link ReportFont}.
 *
 * <p>A font is identified by what it says it is, not by its file name: {@link FontFileInspector}
 * reads the family and style out of the bytes, and that pair is the key. So a re-upload of
 * {@code calibrib.ttf} replaces "Calibri Bold" whatever the file was called this time, and a file
 * that is not a font is refused before anything is stored.
 *
 * <p>Uploads take the whole selection in one call and install once at the end: every install
 * restarts LibreOffice, and a family is typically four files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportFontService {

    /**
     * Generous: a Latin family is a couple of megabytes a face, a CJK font twenty. Beyond that a
     * file is not a font anyone wants in a report.
     */
    static final long MAX_FILE_SIZE = 25L * 1024 * 1024;

    private final ReportFontRepository reportFontRepository;
    private final StorageService storageService;
    private final ReportFontInstaller installer;

    /** Every uploaded font, families together, styles in order. */
    public List<ReportFontDto> list() {
        return reportFontRepository.findAllByOrderByFamilyAscStyleAsc().stream()
                .map(ReportFontDto::fromEntity)
                .toList();
    }

    /** Every family the server can draw with, bundled and uploaded alike. */
    public List<String> installedFamilies() {
        return installer.installedFamilies();
    }

    /**
     * Stores one or more font files and installs them.
     *
     * <p>All files are checked before any is stored, so a bad file in a selection of four rejects
     * the selection with its name rather than leaving three installed and one missing.
     */
    public List<ReportFontDto> upload(List<MultipartFile> files, String userId) {
        record Parsed(MultipartFile file, byte[] bytes, FontFileInspector.FontNames names) {}

        List<Parsed> parsed = new ArrayList<>();
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            if (file == null || file.isEmpty()) continue;
            String label = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "the uploaded file" : file.getOriginalFilename();
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException(label + ": a font file cannot be larger than "
                        + (MAX_FILE_SIZE / (1024 * 1024)) + " MB");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read " + label, e);
            }
            FontFileInspector.FontNames names;
            try {
                names = FontFileInspector.inspect(bytes);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(label + ": " + e.getMessage()
                        + ". Upload TrueType or OpenType files (.ttf, .otf).");
            }
            parsed.add(new Parsed(file, bytes, names));
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("No font file was uploaded");
        }

        List<ReportFont> saved = new ArrayList<>();
        for (Parsed p : parsed) {
            saved.add(store(p.file(), p.bytes(), p.names(), userId));
        }
        install();
        return saved.stream().map(ReportFontDto::fromEntity).toList();
    }

    /** Removes the font, its stored file and its installed copy. */
    public void delete(String id) {
        ReportFont font = reportFontRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report font not found: " + id));
        reportFontRepository.delete(font);
        deleteObjectQuietly(font.getStorageKey());
        log.info("Deleted report font {} {}", font.getFamily(), font.getStyle());
        install();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /**
     * Same replacement order as a client image: the new object is stored and the row saved before
     * the old object goes, so a failed upload leaves the previous file in place.
     */
    private ReportFont store(MultipartFile file, byte[] bytes, FontFileInspector.FontNames names,
                             String userId) {
        ReportFont existing = reportFontRepository
                .findByFamilyIgnoreCaseAndStyleIgnoreCase(names.family(), names.style())
                .orElse(null);

        String fontId = UUID.randomUUID().toString().replace("-", "");
        boolean cff = FontFileInspector.isOpenTypeCff(bytes);
        String fileName = safeFileName(file.getOriginalFilename(), cff ? ".otf" : ".ttf");
        String contentType = cff ? "font/otf" : "font/ttf";
        String storageKey = String.format("report-fonts/%s/%s", fontId, fileName);
        storageService.uploadBytes(storageKey, bytes, contentType);

        ReportFont font = existing != null ? existing : ReportFont.builder()
                .family(names.family())
                .style(names.style())
                .build();
        String replacedKey = existing != null ? existing.getStorageKey() : null;

        font.setStorageKey(storageKey);
        font.setOriginalFileName(fileName);
        font.setContentType(contentType);
        font.setFileSize((long) bytes.length);
        font.setUploadedBy(userId);
        font.setUploadedAt(LocalDateTime.now());
        ReportFont saved = reportFontRepository.save(font);

        if (replacedKey != null && !replacedKey.equals(storageKey)) {
            deleteObjectQuietly(replacedKey);
        }
        log.info("Stored report font {} {} ({})", names.family(), names.style(), fileName);
        return saved;
    }

    /**
     * The rows are already right when this runs, so an install failure is reported as what it is —
     * stored but not yet on the server — rather than as a failed upload. The next startup, upload
     * or delete syncs again.
     */
    private void install() {
        try {
            installer.sync();
        } catch (RuntimeException e) {
            log.warn("Report fonts stored but not installed: {}", e.getMessage());
            throw new IllegalStateException(
                    "The font was stored but could not be installed on the server: " + e.getMessage(), e);
        }
    }

    /** Strips any path and odd characters, and makes the extension say what the bytes are. */
    private static String safeFileName(String original, String extension) {
        String base = original == null ? "" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "font";
        if (base.length() > 100) base = base.substring(0, 100);
        return base + extension;
    }

    private void deleteObjectQuietly(String storageKey) {
        try {
            storageService.deleteObject(storageKey);
        } catch (Exception e) {
            log.warn("Could not delete storage object {}: {}", storageKey, e.getMessage());
        }
    }
}
