package com.faction.clientportal.service;

import com.faction.clientportal.dto.ClientImageDto;
import com.faction.clientportal.exception.ResourceNotFoundException;
import com.faction.clientportal.model.ClientImage;
import com.faction.clientportal.repository.ClientImageRepository;
import com.faction.clientportal.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A client's images, held in named slots — see {@link ClientImage}.
 *
 * <p>The bytes go to object storage and the row records where, the same arrangement
 * {@link InlineImageService} uses. What differs is addressing: an inline image is reached by an
 * opaque id, a client image by the slot it fills, so a report can ask for "this client's logo"
 * without first looking up which image that is.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientImageService {

    /**
     * What may be uploaded. SVG is included deliberately, unlike the inline-image inline-render
     * allowlist: a logo is very often an SVG, and these are uploaded by staff who can already edit
     * the organization rather than by anyone who can attach a screenshot. It is still never
     * rendered inline — {@link com.faction.clientportal.util.FileStreamResponse} downloads
     * anything off its own narrower list, which is what keeps an SVG's script from running in
     * this origin.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml");

    /**
     * Big enough for a print-resolution logo, small enough that the row stays a pointer to a file
     * rather than a way to park arbitrary payloads in the bucket.
     */
    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /**
     * Slot names are used in URLs and read out of templates, so they are deliberately boring:
     * lowercase, no spaces, no dots. Rejecting the rest here is also what keeps a name from
     * walking out of its own storage prefix.
     */
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,40}");

    private final ClientImageRepository clientImageRepository;
    private final OrganizationRepository organizationRepository;
    private final StorageService storageService;

    /**
     * Stores an image in one of the client's slots, replacing whatever was there.
     *
     * <p>The old object is deleted after the new row is saved, not before: a failed upload must
     * leave the previous image intact, and an orphaned object costs storage where a missing one
     * costs a broken report.
     */
    public ClientImageDto upload(String organizationId, String name, MultipartFile file, String userId) {
        requireOrganization(organizationId);
        String slot = normaliseName(name);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No image file was uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "An image cannot be larger than " + (MAX_FILE_SIZE / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported image type: "
                    + (contentType.isEmpty() ? "unknown" : contentType)
                    + ". Allowed: PNG, JPEG, GIF, WebP, SVG");
        }

        ClientImage existing = clientImageRepository
                .findByOrganizationIdAndName(organizationId, slot).orElse(null);

        String imageId = UUID.randomUUID().toString().replace("-", "");
        String fileName = safeFileName(file.getOriginalFilename());
        String storageKey = String.format("organizations/%s/images/%s/%s",
                organizationId, imageId, fileName);

        try (var in = file.getInputStream()) {
            storageService.uploadStream(storageKey, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded image", e);
        }

        // Reuse the existing row so the slot's identity survives a replacement — anything holding
        // the image's id keeps resolving, and the unique (organization, name) index is never
        // momentarily violated by a second row for the same slot.
        ClientImage image = existing != null ? existing : ClientImage.builder()
                .organizationId(organizationId)
                .name(slot)
                .build();
        String replacedKey = existing != null ? existing.getStorageKey() : null;

        image.setStorageKey(storageKey);
        image.setOriginalFileName(fileName);
        image.setContentType(contentType);
        image.setFileSize(file.getSize());
        image.setUploadedBy(userId);
        image.setUploadedAt(LocalDateTime.now());
        ClientImage saved = clientImageRepository.save(image);

        if (replacedKey != null && !replacedKey.equals(storageKey)) {
            deleteObjectQuietly(replacedKey);
        }
        log.info("Stored image '{}' for organization {}", slot, organizationId);
        return ClientImageDto.fromEntity(saved);
    }

    /** Every slot this client has filled. */
    public List<ClientImageDto> list(String organizationId) {
        requireOrganization(organizationId);
        return clientImageRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(ClientImageDto::fromEntity)
                .toList();
    }

    /**
     * Opens one slot's bytes for streaming. The caller owns the returned stream and must close it.
     */
    public StorageService.StoredFile open(String organizationId, String name) {
        ClientImage image = require(organizationId, name);
        return new StorageService.StoredFile(
                storageService.openStream(image.getStorageKey()),
                image.getOriginalFileName() == null ? image.getName() : image.getOriginalFileName());
    }

    /** Empties one slot, bytes and all. */
    public void delete(String organizationId, String name) {
        ClientImage image = require(organizationId, name);
        clientImageRepository.delete(image);
        deleteObjectQuietly(image.getStorageKey());
        log.info("Deleted image '{}' from organization {}", image.getName(), organizationId);
    }

    /**
     * Removes every image a client has, called as the organization itself is deleted.
     *
     * <p>Without this the rows would outlive their organization and the objects would never be
     * reachable again — nothing else ever scans this bucket prefix, so they would simply sit there.
     */
    public void deleteAllForOrganization(String organizationId) {
        List<ClientImage> images = clientImageRepository.findByOrganizationIdOrderByNameAsc(organizationId);
        if (images.isEmpty()) {
            return;
        }
        clientImageRepository.deleteAll(images);
        images.forEach(image -> deleteObjectQuietly(image.getStorageKey()));
        log.info("Deleted {} image(s) belonging to organization {}", images.size(), organizationId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ClientImage require(String organizationId, String name) {
        requireOrganization(organizationId);
        return clientImageRepository
                .findByOrganizationIdAndName(organizationId, normaliseName(name))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image named '" + name + "' for organization " + organizationId));
    }

    private void requireOrganization(String organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization not found: " + organizationId);
        }
    }

    /**
     * Lower-cases the slot name before checking it, so "Logo" and "logo" are the same slot rather
     * than two — otherwise an upload typed with a capital would quietly sit beside the real one
     * and never be the image a template asked for.
     */
    private static String normaliseName(String name) {
        String slot = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!VALID_NAME.matcher(slot).matches()) {
            throw new IllegalArgumentException(
                    "An image name must be 1-40 characters of a-z, 0-9, '_' or '-'");
        }
        return slot;
    }

    /**
     * Strips any path from the uploaded name, so a crafted filename cannot steer the storage key
     * out of its own prefix and overwrite another organization's object.
     */
    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return "image";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    /**
     * A storage object that will not delete is logged, not thrown. The row is already gone, so
     * failing here would report a delete that did in fact happen — and leave the caller unable to
     * retry it.
     */
    private void deleteObjectQuietly(String storageKey) {
        try {
            storageService.deleteObject(storageKey);
        } catch (Exception e) {
            log.warn("Could not delete storage object {}: {}", storageKey, e.getMessage());
        }
    }
}
