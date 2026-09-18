package com.faction.clientportal.service;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.dto.ClientImageDto;
import com.faction.clientportal.exception.ResourceNotFoundException;
import com.faction.clientportal.model.ClientImage;
import com.faction.clientportal.model.Organization;
import com.faction.clientportal.repository.ApplicationRepository;
import com.faction.clientportal.repository.ClientImageRepository;
import com.faction.clientportal.repository.OrganizationRepository;
import com.faction.clientportal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A client's images: the slot rules, the upload guards, and what happens to the stored bytes.
 *
 * <p>Runs against the real {@link StorageService} rather than a stub, because the MinIO container
 * is already there ({@link TestContainersConfig}) and half of what this service does is put objects
 * in it and take them out again. A stub would assert that the service called a method, not that the
 * bytes came back.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClientImageServiceTest extends TestContainersConfig {

    /** A one-pixel PNG — real bytes, so the content type and size assertions mean something. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private ClientImageService service;
    @Autowired private ClientImageRepository clientImageRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationService organizationService;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StorageService storageService;

    private String acmeId;
    private String globexId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        applicationRepository.deleteAll();
        clientImageRepository.deleteAll();
        organizationRepository.deleteAll();

        acmeId = organizationRepository.save(Organization.builder().name("Acme").build()).getId();
        globexId = organizationRepository.save(Organization.builder().name("Globex").build()).getId();
    }

    private static MockMultipartFile png(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG);
    }

    @Test
    void anUploadedImageComesBackWithItsBytes() {
        ClientImageDto stored = service.upload(acmeId, "logo", png("acme.png"), "tester");

        assertThat(stored.getName()).isEqualTo("logo");
        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getFileSize()).isEqualTo(PNG.length);
        assertThat(stored.getUploadedBy()).isEqualTo("tester");

        try (var stream = service.open(acmeId, "logo").stream()) {
            assertThat(stream.readAllBytes()).isEqualTo(PNG);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theStorageKeyIsScopedToTheOrganization() {
        service.upload(acmeId, "logo", png("acme.png"), "tester");

        ClientImage row = clientImageRepository.findByOrganizationIdAndName(acmeId, "logo").orElseThrow();
        assertThat(row.getStorageKey())
                .startsWith("organizations/" + acmeId + "/images/")
                .endsWith("/acme.png");
    }

    @Test
    void uploadingToAFilledSlotReplacesItAndReleasesTheOldObject() {
        service.upload(acmeId, "logo", png("first.png"), "tester");
        String firstKey = clientImageRepository.findByOrganizationIdAndName(acmeId, "logo")
                .orElseThrow().getStorageKey();

        service.upload(acmeId, "logo",
                new MockMultipartFile("file", "second.png", "image/png", PNG), "tester");

        // One row, not two: the slot is the identity, so the replacement reuses it.
        assertThat(clientImageRepository.findByOrganizationIdOrderByNameAsc(acmeId)).hasSize(1);
        ClientImage row = clientImageRepository.findByOrganizationIdAndName(acmeId, "logo").orElseThrow();
        assertThat(row.getOriginalFileName()).isEqualTo("second.png");
        assertThat(row.getStorageKey()).isNotEqualTo(firstKey);

        // The superseded object is gone — otherwise every replacement would leak a file nothing
        // can ever reach again.
        assertThatThrownBy(() -> storageService.downloadBytes(firstKey)).isInstanceOf(Exception.class);
    }

    @Test
    void slotNamesAreUniquePerOrganizationNotGlobally() {
        service.upload(acmeId, "logo", png("acme.png"), "tester");
        service.upload(globexId, "logo", png("globex.png"), "tester");

        assertThat(service.list(acmeId)).extracting(ClientImageDto::getOriginalFileName)
                .containsExactly("acme.png");
        assertThat(service.list(globexId)).extracting(ClientImageDto::getOriginalFileName)
                .containsExactly("globex.png");
    }

    @Test
    void aSlotNameIsNormalisedSoLogoAndLOGOAreTheSameSlot() {
        service.upload(acmeId, "logo", png("first.png"), "tester");
        service.upload(acmeId, "  LOGO ", png("second.png"), "tester");

        assertThat(service.list(acmeId)).hasSize(1);
        assertThat(service.list(acmeId).get(0).getOriginalFileName()).isEqualTo("second.png");
    }

    @Test
    void anUnusableSlotNameIsRefused() {
        // Slot names go in URLs and are read out of templates, so anything but the boring set is
        // refused — which is also what stops a name walking out of its own storage prefix.
        for (String bad : new String[] {"", "  ", "my logo", "logo.png", "../../etc", "a".repeat(41)}) {
            assertThatThrownBy(() -> service.upload(acmeId, bad, png("x.png"), "tester"))
                    .as("slot name %s", bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(service.list(acmeId)).isEmpty();
    }

    @Test
    void aNonImageIsRefused() {
        assertThatThrownBy(() -> service.upload(acmeId, "logo",
                new MockMultipartFile("file", "payload.pdf", "application/pdf", PNG), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported image type");

        assertThat(service.list(acmeId)).isEmpty();
    }

    @Test
    void anOversizedImageIsRefused() {
        byte[] tooBig = new byte[(int) ClientImageService.MAX_FILE_SIZE + 1];
        assertThatThrownBy(() -> service.upload(acmeId, "logo",
                new MockMultipartFile("file", "huge.png", "image/png", tooBig), "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");

        assertThat(service.list(acmeId)).isEmpty();
    }

    @Test
    void anEmptyUploadIsRefused() {
        assertThatThrownBy(() -> service.upload(acmeId, "logo",
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), "tester"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownOrganizationOrSlotIsNotFound() {
        assertThatThrownBy(() -> service.upload("no-such-org", "logo", png("x.png"), "tester"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.list("no-such-org"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.open(acmeId, "logo"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(acmeId, "logo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anImageIdAloneDoesNotReachIntoAnotherOrganizationsSlots() {
        service.upload(globexId, "logo", png("globex.png"), "tester");

        // Same slot name, wrong parent: the pair is the address, not the name on its own.
        assertThatThrownBy(() -> service.open(acmeId, "logo"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(acmeId, "logo"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(service.list(globexId)).hasSize(1);
    }

    @Test
    void deletingASlotRemovesTheStoredFileToo() {
        service.upload(acmeId, "logo", png("acme.png"), "tester");
        String key = clientImageRepository.findByOrganizationIdAndName(acmeId, "logo")
                .orElseThrow().getStorageKey();

        service.delete(acmeId, "logo");

        assertThat(service.list(acmeId)).isEmpty();
        assertThatThrownBy(() -> storageService.downloadBytes(key)).isInstanceOf(Exception.class);
    }

    @Test
    void deletingTheOrganizationTakesItsImagesWithIt() {
        service.upload(acmeId, "logo", png("acme.png"), "tester");
        service.upload(acmeId, "cover", png("cover.png"), "tester");
        service.upload(globexId, "logo", png("globex.png"), "tester");
        String acmeKey = clientImageRepository.findByOrganizationIdAndName(acmeId, "logo")
                .orElseThrow().getStorageKey();

        organizationService.deleteOrganizationById(acmeId);

        // Nothing else ever scans that storage prefix, so rows or objects left behind here would
        // sit there unreachable forever.
        assertThat(clientImageRepository.findByOrganizationIdOrderByNameAsc(acmeId)).isEmpty();
        assertThatThrownBy(() -> storageService.downloadBytes(acmeKey)).isInstanceOf(Exception.class);

        // The other client's slot is untouched.
        assertThat(service.list(globexId)).hasSize(1);
    }

    @Test
    void aRefusedOrganizationDeleteLeavesTheImagesAlone() {
        service.upload(acmeId, "logo", png("acme.png"), "tester");
        applicationRepository.save(com.faction.clientportal.model.Application.builder()
                .name("Acme Web").organizationId(acmeId)
                .createdAt(java.time.LocalDateTime.now()).build());

        assertThatThrownBy(() -> organizationService.deleteOrganizationById(acmeId))
                .isInstanceOf(IllegalArgumentException.class);

        // The cleanup runs last, after every refusal — a delete that did not happen must not have
        // taken the client's logo with it.
        assertThat(service.list(acmeId)).hasSize(1);
        try (var stream = service.open(acmeId, "logo").stream()) {
            assertThat(stream.readAllBytes()).isEqualTo(PNG);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
