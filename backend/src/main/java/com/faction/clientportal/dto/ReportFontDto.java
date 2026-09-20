package com.faction.clientportal.dto;

import com.faction.clientportal.model.ReportFont;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An uploaded report font, as the interface sees it. No storage key, for the same reason
 * {@link ClientImageDto} carries none.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFontDto {

    private String id;
    private String family;
    private String style;
    private String fileName;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    public static ReportFontDto fromEntity(ReportFont font) {
        return ReportFontDto.builder()
                .id(font.getId())
                .family(font.getFamily())
                .style(font.getStyle())
                .fileName(font.getOriginalFileName())
                .fileSize(font.getFileSize())
                .uploadedBy(font.getUploadedBy())
                .uploadedAt(font.getUploadedAt())
                .build();
    }
}
