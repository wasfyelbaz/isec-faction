package com.faction.clientportal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command the manager launches LibreOffice with. No soffice here: what matters is that the
 * server gets a profile of its own (a shared profile is what let PDF conversions kill it) and that
 * a stale lock in that profile is cleared before the start.
 */
class LibreOfficeServerManagerTest {

    private static LibreOfficeServerManager manager(Path profileDir, boolean managed) {
        LibreOfficeServerManager m = new LibreOfficeServerManager();
        ReflectionTestUtils.setField(m, "sofficePath", "soffice");
        ReflectionTestUtils.setField(m, "managed", managed);
        ReflectionTestUtils.setField(m, "port", 2002);
        ReflectionTestUtils.setField(m, "startupTimeoutSeconds", 90);
        ReflectionTestUtils.setField(m, "profileDir", profileDir.toString());
        return m;
    }

    @Test
    void launchesOnItsOwnProfileAndClearsAStaleLock(@TempDir Path tmp) throws Exception {
        Path profile = tmp.resolve("lo-profile");
        Files.createDirectories(profile);
        Files.writeString(profile.resolve(".lock"), "left by a killed server");

        List<String> command = manager(profile, true).buildCommand();

        assertThat(command.get(0)).isEqualTo("soffice");
        assertThat(command.get(1)).startsWith("-env:UserInstallation=file:")
                .contains(profile.getFileName().toString());
        assertThat(command).contains("--headless", "--norestore", "--nodefault")
                .contains("--accept=socket,host=localhost,port=2002;urp;StarOffice.ServiceManager");
        assertThat(profile.resolve(".lock")).doesNotExist();
    }

    @Test
    void createsTheProfileDirectoryWhenItIsMissing(@TempDir Path tmp) throws Exception {
        Path profile = tmp.resolve("not-yet-there");

        manager(profile, true).buildCommand();

        assertThat(profile).isDirectory();
    }

    @Test
    void doesNothingWhenNotManaged(@TempDir Path tmp) {
        LibreOfficeServerManager m = manager(tmp, false);

        m.start();
        m.ensureRunning();
        m.restart();

        assertThat(m.isManaged()).isFalse();
        assertThat(m.isRunning()).isFalse();
    }
}
