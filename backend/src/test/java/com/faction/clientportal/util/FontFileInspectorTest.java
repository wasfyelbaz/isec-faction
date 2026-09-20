package com.faction.clientportal.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The name-table reader, fed hand-built sfnt files so the test needs no font on the classpath.
 */
public class FontFileInspectorTest {

    /** A name record: platform, encoding, language, name id, value. */
    public record Name(int platform, int encoding, int language, int nameId, String value) {}

    /**
     * Builds the smallest file the inspector accepts: an offset table, one directory entry, and
     * a name table holding the given records.
     */
    public static byte[] font(int sfntTag, List<Name> names) {
        ByteBuffer strings = ByteBuffer.allocate(4096);
        ByteBuffer records = ByteBuffer.allocate(12 * names.size());
        for (Name n : names) {
            byte[] encoded = n.platform() == 1
                    ? n.value().getBytes(StandardCharsets.ISO_8859_1)
                    : n.value().getBytes(StandardCharsets.UTF_16BE);
            records.putShort((short) n.platform()).putShort((short) n.encoding())
                    .putShort((short) n.language()).putShort((short) n.nameId())
                    .putShort((short) encoded.length).putShort((short) strings.position());
            strings.put(encoded);
        }
        int nameTableLength = 6 + records.position() + strings.position();
        ByteBuffer nameTable = ByteBuffer.allocate(nameTableLength);
        nameTable.putShort((short) 0).putShort((short) names.size()).putShort((short) (6 + records.position()));
        nameTable.put(records.array(), 0, records.position()).put(strings.array(), 0, strings.position());

        int nameOffset = 12 + 16;
        ByteBuffer file = ByteBuffer.allocate(nameOffset + nameTableLength);
        file.putInt(sfntTag).putShort((short) 1).putShort((short) 16).putShort((short) 0).putShort((short) 0);
        file.putInt(0x6E616D65).putInt(0).putInt(nameOffset).putInt(nameTableLength);
        file.put(nameTable.array());
        return file.array();
    }

    @Test
    void readsFamilyAndStyleFromTheWindowsEnglishRecords() {
        byte[] bytes = font(0x00010000, List.of(
                new Name(3, 1, 0x0409, 1, "Test Sans"),
                new Name(3, 1, 0x0409, 2, "Bold Italic")));

        FontFileInspector.FontNames names = FontFileInspector.inspect(bytes);

        assertThat(names.family()).isEqualTo("Test Sans");
        assertThat(names.style()).isEqualTo("Bold Italic");
        assertThat(FontFileInspector.looksLikeFont(bytes)).isTrue();
        assertThat(FontFileInspector.isOpenTypeCff(bytes)).isFalse();
    }

    @Test
    void prefersTheWindowsRecordOverTheMacintoshOneAndDefaultsTheStyle() {
        byte[] bytes = font(0x4F54544F, List.of(
                new Name(1, 0, 0, 1, "Mac Name"),
                new Name(3, 1, 0x0409, 1, "Windows Name")));

        FontFileInspector.FontNames names = FontFileInspector.inspect(bytes);

        assertThat(names.family()).isEqualTo("Windows Name");
        assertThat(names.style()).isEqualTo("Regular");
        assertThat(FontFileInspector.isOpenTypeCff(bytes)).isTrue();
    }

    @Test
    void fallsBackToTheMacintoshRecordWhenItIsAllThereIs() {
        byte[] bytes = font(0x74727565, List.of(
                new Name(1, 0, 0, 1, "Old Serif"),
                new Name(1, 0, 0, 2, "Italic")));

        FontFileInspector.FontNames names = FontFileInspector.inspect(bytes);

        assertThat(names.family()).isEqualTo("Old Serif");
        assertThat(names.style()).isEqualTo("Italic");
    }

    @Test
    void refusesWhatIsNotASingleFont() {
        assertThatThrownBy(() -> FontFileInspector.inspect("not a font at all".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a TrueType or OpenType font");

        byte[] collection = font(0x74746366, List.of(new Name(3, 1, 0x0409, 1, "Family")));
        assertThatThrownBy(() -> FontFileInspector.inspect(collection))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".ttc");

        byte[] nameless = font(0x00010000, List.of(new Name(3, 1, 0x0409, 2, "Bold")));
        assertThatThrownBy(() -> FontFileInspector.inspect(nameless))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("family name");

        assertThat(FontFileInspector.looksLikeFont(new byte[3])).isFalse();
        assertThat(FontFileInspector.looksLikeFont(null)).isFalse();
    }
}
