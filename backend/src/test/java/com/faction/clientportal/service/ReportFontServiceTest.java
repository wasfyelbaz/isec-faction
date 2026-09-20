package com.faction.clientportal.service;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.dto.ReportFontDto;
import com.faction.clientportal.exception.ResourceNotFoundException;
import com.faction.clientportal.repository.ReportFontRepository;
import com.faction.clientportal.util.FontFileInspectorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uploading fonts for the PDF step: what is accepted, how a font is identified, and what ends up
 * in storage and in the install directory.
 *
 * <p>Real {@link StorageService} and a real install directory (a scratch one, see
 * {@code application-test.yml}), for the same reason {@link ClientImageServiceTest} uses the MinIO
 * container: the point of the service is that the bytes come back out and land on disk. The
 * LibreOffice restart is switched off in the test profile, so the installer's sync is the only
 * side effect measured.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportFontServiceTest extends TestContainersConfig {

    @Autowired private ReportFontService service;
    @Autowired private ReportFontInstaller installer;
    @Autowired private ReportFontRepository repository;
    @Autowired private StorageService storageService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        installer.sync();
    }

    private static MockMultipartFile font(String fileName, String family, String style) {
        byte[] bytes = FontFileInspectorTest.font(0x00010000, List.of(
                new FontFileInspectorTest.Name(3, 1, 0x0409, 1, family),
                new FontFileInspectorTest.Name(3, 1, 0x0409, 2, style)));
        return new MockMultipartFile("files", fileName, "font/ttf", bytes);
    }

    @Test
    void anUploadedFontIsKeyedByWhatItDeclaresAndInstalledOnDisk() {
        List<ReportFontDto> stored = service.upload(List.of(font("whatever.bin", "Test Sans", "Bold")), "tester");

        assertThat(stored).hasSize(1);
        ReportFontDto dto = stored.get(0);
        assertThat(dto.getFamily()).isEqualTo("Test Sans");
        assertThat(dto.getStyle()).isEqualTo("Bold");
        assertThat(dto.getFileName()).isEqualTo("whatever.ttf");
        assertThat(dto.getUploadedBy()).isEqualTo("tester");

        assertThat(installer.installedFiles()).containsExactly(dto.getId() + ".ttf");
        String storageKey = repository.findById(dto.getId()).orElseThrow().getStorageKey();
        assertThat(storageService.downloadBytes(storageKey)).hasSize(dto.getFileSize().intValue());
    }

    @Test
    void uploadingTheSameStyleAgainReplacesItInsteadOfAddingASecondRow() {
        String first = service.upload(List.of(font("a.ttf", "Test Sans", "Regular")), "one").get(0).getId();
        String again = service.upload(List.of(font("b.ttf", "test sans", "regular")), "two").get(0).getId();

        assertThat(again).isEqualTo(first);
        assertThat(service.list()).hasSize(1);
        assertThat(service.list().get(0).getUploadedBy()).isEqualTo("two");
        assertThat(installer.installedFiles()).containsExactly(first + ".ttf");
    }

    @Test
    void aSelectionIsInstalledTogetherAndListedFamilyByFamily() {
        service.upload(List.of(
                font("r.ttf", "Zeta", "Regular"),
                font("b.ttf", "Zeta", "Bold"),
                font("a.ttf", "Alpha", "Regular")), "tester");

        assertThat(service.list()).extracting(ReportFontDto::getFamily, ReportFontDto::getStyle)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Alpha", "Regular"),
                        org.assertj.core.groups.Tuple.tuple("Zeta", "Bold"),
                        org.assertj.core.groups.Tuple.tuple("Zeta", "Regular"));
        assertThat(installer.installedFiles()).hasSize(3);
    }

    @Test
    void aBadFileRejectsTheWholeSelectionBeforeAnythingIsStored() {
        MockMultipartFile notAFont = new MockMultipartFile("files", "notes.txt", "text/plain",
                "just some text".getBytes());

        assertThatThrownBy(() -> service.upload(List.of(font("ok.ttf", "Fine", "Regular"), notAFont), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.txt")
                .hasMessageContaining("not a TrueType or OpenType font");

        assertThat(service.list()).isEmpty();
        assertThat(installer.installedFiles()).isEmpty();
    }

    @Test
    void emptyAndOversizedUploadsAreRefused() {
        assertThatThrownBy(() -> service.upload(List.of(), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No font file");

        MockMultipartFile huge = new MockMultipartFile("files", "huge.ttf", "font/ttf",
                new byte[(int) ReportFontService.MAX_FILE_SIZE + 1]);
        assertThatThrownBy(() -> service.upload(List.of(huge), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 MB");
    }

    @Test
    void deletingAFontRemovesItsRowItsObjectAndItsInstalledFile() {
        ReportFontDto dto = service.upload(List.of(font("x.ttf", "Gone", "Regular")), "tester").get(0);
        String storageKey = repository.findById(dto.getId()).orElseThrow().getStorageKey();

        service.delete(dto.getId());

        assertThat(service.list()).isEmpty();
        assertThat(installer.installedFiles()).isEmpty();
        assertThatThrownBy(() -> storageService.downloadBytes(storageKey)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.delete(dto.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void installedFamiliesAlwaysIncludeTheUploadedOnes() {
        service.upload(List.of(font("u.ttf", "Uploaded Family", "Regular")), "tester");

        // With fontconfig present the list is fc-list's, which now scans the install directory;
        // without it the uploaded families are reported. Either way the upload is in it.
        assertThat(service.installedFamilies())
                .anySatisfy(family -> assertThat(family).isEqualToIgnoringCase("Uploaded Family"));
    }
}
