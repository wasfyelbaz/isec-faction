package com.faction.clientportal.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads the family and style a TrueType or OpenType font declares about itself.
 *
 * <p>Just enough of the sfnt container to reach the {@code name} table: the offset table, the
 * table directory, then the name records for family (id 1) and subfamily (id 2). Nothing else in
 * the file is looked at, and nothing is rendered — this is what lets an upload be keyed and listed
 * by what the font is called rather than by whatever the file was named, without a font library
 * on the classpath or a working display.
 *
 * <p>Collections ({@code .ttc}) are refused: one file carrying several fonts would need several
 * rows, and every collection ships as separate files as well.
 */
public final class FontFileInspector {

    /** What the font calls itself. {@code style} is {@code Regular} when the file says nothing. */
    public record FontNames(String family, String style) {}

    private static final int SFNT_TRUETYPE = 0x00010000;
    private static final int SFNT_OPENTYPE_CFF = 0x4F54544F; // 'OTTO'
    private static final int SFNT_MAC_TRUETYPE = 0x74727565; // 'true'
    private static final int SFNT_COLLECTION = 0x74746366;   // 'ttcf'
    private static final int TABLE_NAME = 0x6E616D65;        // 'name'

    private static final int NAME_ID_FAMILY = 1;
    private static final int NAME_ID_SUBFAMILY = 2;

    private static final int PLATFORM_UNICODE = 0;
    private static final int PLATFORM_MACINTOSH = 1;
    private static final int PLATFORM_WINDOWS = 3;
    private static final int LANGUAGE_WINDOWS_EN_US = 0x0409;

    private FontFileInspector() {}

    /** True when the bytes start like a font this class can read; the cheap check before parsing. */
    public static boolean looksLikeFont(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return false;
        int tag = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        return tag == SFNT_TRUETYPE || tag == SFNT_OPENTYPE_CFF || tag == SFNT_MAC_TRUETYPE;
    }

    /** True for a CFF-flavoured OpenType file, the kind conventionally named {@code .otf}. */
    public static boolean isOpenTypeCff(byte[] bytes) {
        return bytes != null && bytes.length >= 4
                && ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt() == SFNT_OPENTYPE_CFF;
    }

    /**
     * @throws IllegalArgumentException when the bytes are not a single TrueType/OpenType font, or
     *         the font declares no family name — the message is meant for the person who uploaded
     *         the file
     */
    public static FontNames inspect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("not a TrueType or OpenType font");
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int tag = buf.getInt(0);
        if (tag == SFNT_COLLECTION) {
            throw new IllegalArgumentException(
                    "font collections (.ttc) are not supported — upload the individual .ttf files");
        }
        if (tag != SFNT_TRUETYPE && tag != SFNT_OPENTYPE_CFF && tag != SFNT_MAC_TRUETYPE) {
            throw new IllegalArgumentException("not a TrueType or OpenType font");
        }

        int numTables = u16(buf, 4);
        if (numTables == 0 || 12 + numTables * 16L > bytes.length) {
            throw new IllegalArgumentException("the font's table directory is damaged");
        }
        int nameOffset = -1;
        for (int i = 0; i < numTables; i++) {
            int record = 12 + i * 16;
            if (buf.getInt(record) == TABLE_NAME) {
                nameOffset = buf.getInt(record + 8);
                break;
            }
        }
        if (nameOffset < 0 || nameOffset + 6L > bytes.length) {
            throw new IllegalArgumentException("the font has no name table");
        }

        int count = u16(buf, nameOffset + 2);
        int stringStorage = nameOffset + u16(buf, nameOffset + 4);
        String family = null;
        String style = null;
        int familyRank = -1;
        int styleRank = -1;
        for (int i = 0; i < count; i++) {
            int record = nameOffset + 6 + i * 12;
            if (record + 12L > bytes.length) break;
            int platform = u16(buf, record);
            int encoding = u16(buf, record + 2);
            int language = u16(buf, record + 4);
            int nameId = u16(buf, record + 6);
            int length = u16(buf, record + 8);
            int offset = u16(buf, record + 10);
            if (nameId != NAME_ID_FAMILY && nameId != NAME_ID_SUBFAMILY) continue;
            int start = stringStorage + offset;
            if (start < 0 || start + (long) length > bytes.length) continue;

            // Prefer the Windows English record, which is what fontconfig reports, then any other
            // Unicode record, and fall back to the Macintosh Roman one older fonts carry alone.
            String value;
            int rank;
            if (platform == PLATFORM_WINDOWS) {
                value = new String(bytes, start, length, StandardCharsets.UTF_16BE);
                rank = language == LANGUAGE_WINDOWS_EN_US ? 3 : 2;
            } else if (platform == PLATFORM_UNICODE) {
                value = new String(bytes, start, length, StandardCharsets.UTF_16BE);
                rank = 1;
            } else if (platform == PLATFORM_MACINTOSH && encoding == 0) {
                value = new String(bytes, start, length, StandardCharsets.ISO_8859_1);
                rank = 0;
            } else {
                continue;
            }
            value = value.trim();
            if (value.isEmpty()) continue;
            if (nameId == NAME_ID_FAMILY && rank > familyRank) {
                family = value;
                familyRank = rank;
            } else if (nameId == NAME_ID_SUBFAMILY && rank > styleRank) {
                style = value;
                styleRank = rank;
            }
        }
        if (family == null) {
            throw new IllegalArgumentException("the font does not declare a family name");
        }
        return new FontNames(family, style == null ? "Regular" : style);
    }

    private static int u16(ByteBuffer buf, int at) {
        return buf.getShort(at) & 0xFFFF;
    }
}
