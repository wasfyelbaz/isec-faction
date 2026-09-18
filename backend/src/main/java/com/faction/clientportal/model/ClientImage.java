package com.faction.clientportal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An image belonging to a client — a logo, a cover picture, a signature block.
 *
 * <p>Held in named slots rather than as a free list: a report asks for "the client's logo", not
 * "the client's third image". {@code name} is that slot key, unique per organization, so uploading
 * again to the same name replaces what was there and everything pointing at the slot follows
 * automatically.
 *
 * <p>Its own table rather than another JSONB column on {@code organizations}, because unlike the
 * distribution list this row is a pointer into object storage: the bytes live in MinIO and the row
 * records where. Keeping them separate is what lets an image be replaced, or an organization's
 * images be cleaned up, without rewriting the organization row.
 *
 * <p>Same shape as {@link InlineImage} — {@code storageKey} plus the metadata needed to serve the
 * bytes back with the right type and filename — but scoped to an organization instead of an
 * assessment, and addressed by slot name instead of by id.
 */
@Entity
@Table(name = "organization_images", indexes = {
    @Index(name = "idx_organization_images_organizationid", columnList = "organization_id"),
    @Index(name = "idx_organization_images_org_name", columnList = "organization_id, name",
            unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** The organization this image belongs to. Never null. */
    @Column(nullable = false)
    private String organizationId;

    /**
     * The slot this image fills — {@code logo}, {@code cover}, {@code signature}, or whatever else
     * a template refers to. Lowercase, unique within the organization.
     */
    @Column(nullable = false)
    private String name;

    /** Where the bytes are in object storage: {@code organizations/{orgId}/images/{id}/{file}}. */
    @Column(nullable = false)
    private String storageKey;

    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
