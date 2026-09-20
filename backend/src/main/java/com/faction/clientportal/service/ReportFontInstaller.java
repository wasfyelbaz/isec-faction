package com.faction.clientportal.service;

import com.faction.clientportal.model.ReportFont;
import com.faction.clientportal.repository.ReportFontRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Puts the uploaded {@link ReportFont}s where LibreOffice will find them.
 *
 * <p>The rows point into object storage; LibreOffice reads a directory. This bridges the two:
 * every row's file is written under {@code faction.report-fonts.install-dir}, anything there that
 * no row accounts for is removed, the fontconfig cache is refreshed, and the headless server is
 * restarted so it picks the change up. Done at startup — a container rebuilt from the image starts
 * with an empty directory — and again after every upload or delete.
 *
 * <p>The PDF converter needs none of this: it is a fresh {@code soffice} per call and scans the
 * directory itself. The restart is for the long-running server that lays out the table of
 * contents, whose page numbers must be computed with the same fonts the PDF is drawn with.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportFontInstaller {

    private final ReportFontRepository reportFontRepository;
    private final StorageService storageService;
    private final LibreOfficeServerManager libreOfficeServer;

    @Value("${faction.report-fonts.install-dir:/usr/share/fonts/truetype/faction-uploaded}")
    private String installDir;

    /** fc-list output, cached until the next sync; null until first asked for. */
    private volatile List<String> installedFamilies;

    /**
     * Startup: install what storage holds, then start the server. If the install fails the server
     * is started anyway — a report with stale fonts beats no report — and the next upload retries.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void installOnStartup() {
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Could not install the uploaded report fonts at startup: {}", e.getMessage());
            libreOfficeServer.start();
        }
    }

    /**
     * Makes the install directory match the table, then restarts LibreOffice.
     *
     * @return how many fonts are installed
     * @throws UncheckedIOException when the directory cannot be written — the rows are untouched,
     *         so a later sync can complete the install
     */
    public synchronized int sync() {
        Path dir = Paths.get(installDir);
        List<ReportFont> fonts = reportFontRepository.findAllByOrderByFamilyAscStyleAsc();
        try {
            Files.createDirectories(dir);
            Set<String> wanted = new HashSet<>();
            for (ReportFont font : fonts) {
                String fileName = installedFileName(font);
                wanted.add(fileName);
                Path target = dir.resolve(fileName);
                if (Files.exists(target) && font.getFileSize() != null
                        && Files.size(target) == font.getFileSize()) {
                    continue;
                }
                Files.write(target, storageService.downloadBytes(font.getStorageKey()));
                log.debug("Installed report font {} {} as {}", font.getFamily(), font.getStyle(), target);
            }
            // Dotfiles are fontconfig's own (.uuid marks a scanned directory) and stay.
            try (Stream<Path> present = Files.list(dir)) {
                for (Path stray : present.filter(p -> !isDotFile(p) && !wanted.contains(p.getFileName().toString())).toList()) {
                    Files.deleteIfExists(stray);
                    log.debug("Removed {} — no report font row refers to it", stray);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the report fonts to " + dir, e);
        }
        refreshFontCache();
        installedFamilies = null;
        libreOfficeServer.restart();
        log.info("{} uploaded report font(s) installed in {}", fonts.size(), dir);
        return fonts.size();
    }

    /**
     * Every family LibreOffice can draw with on this server: the bundled ones as fontconfig reports
     * them, and every uploaded one.
     */
    public List<String> installedFamilies() {
        List<String> cached = installedFamilies;
        if (cached != null) {
            return cached;
        }
        Set<String> families = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        // fontconfig's view of the system, which covers the install directory when it sits under
        // /usr/share/fonts as it does in the image, plus every uploaded family by name: an install
        // directory fontconfig does not scan (a REPORT_FONTS_DIR under /opt, the test profile's
        // scratch directory) would otherwise make an installed font look missing, and without
        // fontconfig at all (a developer machine) the uploads are all that is known.
        String system = run(60, "fc-list", ":", "family");
        if (system != null) addFamilies(families, system);
        reportFontRepository.findAllByOrderByFamilyAscStyleAsc()
                .forEach(font -> families.add(font.getFamily()));
        List<String> result = List.copyOf(families);
        installedFamilies = result;
        return result;
    }

    /** fontconfig prints every name a font goes by on one line: "Calibri,Calibri Light". */
    private static void addFamilies(Set<String> families, String listing) {
        for (String line : listing.split("\n")) {
            for (String name : line.split(",")) {
                String family = name.trim();
                if (!family.isEmpty()) families.add(family);
            }
        }
    }

    /** The on-disk name: the row id plus the extension the file's own format calls for. */
    static String installedFileName(ReportFont font) {
        String original = font.getOriginalFileName() == null
                ? "" : font.getOriginalFileName().toLowerCase(Locale.ROOT);
        return font.getId() + (original.endsWith(".otf") ? ".otf" : ".ttf");
    }

    /** For tests and diagnostics: the files currently in the install directory, sorted. */
    List<String> installedFiles() {
        Path dir = Paths.get(installDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> present = Files.list(dir)) {
            return present.filter(p -> !isDotFile(p)).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean isDotFile(Path p) {
        return p.getFileName().toString().startsWith(".");
    }

    /** Best effort: fontconfig rescans a changed directory by itself, this only makes it prompt. */
    private void refreshFontCache() {
        if (run(120, "fc-cache", "-f") == null) {
            log.debug("fc-cache not available — relying on fontconfig's own directory scan");
        }
    }

    /** Runs a command and returns its output, or null when it is missing, fails or times out. */
    private static String run(int timeoutSeconds, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? new String(output, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
