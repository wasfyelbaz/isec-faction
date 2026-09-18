package com.faction.clientportal.dto;

import com.faction.clientportal.model.ClientImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One of a client's images, as the interface sees it.
 *
 * <p>Carries no storage key: the bytes are served from
 * {@code /api/v1/organizations/{id}/images/{name}/content}, which is the only route to them, and
 * exposing the key would invite something to build a storage URL and bypass the access check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientImageDto {

    private String id;
    /** The slot: logo, cover, signature… */
    private String name;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    public static ClientImageDto fromEntity(ClientImage image) {
        return ClientImageDto.builder()
                .id(image.getId())
                .name(image.getName())
                .originalFileName(image.getOriginalFileName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .uploadedBy(image.getUploadedBy())
                .uploadedAt(image.getUploadedAt())
                .build();
    }
}
