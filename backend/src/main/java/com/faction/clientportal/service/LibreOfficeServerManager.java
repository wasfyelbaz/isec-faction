package com.faction.clientportal.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Owns the headless LibreOffice server the TOC refresh talks to over UNO.
 *
 * <p>It used to be started by the container entrypoint, before the JVM. That left no way to
 * restart it, and a restart is exactly what installing a font needs: LibreOffice reads the font
 * list once at startup, so a server started before a font existed lays the table of contents out
 * in a substitute and gets every page number wrong, while the PDF — converted by a fresh process
 * that does see the font — paginates differently. {@link ReportFontInstaller} therefore starts the
 * server only once the uploaded fonts are on disk, and restarts it whenever they change.
 *
 * <p>{@code libreoffice.server.managed=false} switches all of this off for installs that run
 * soffice some other way; every method is then a no-op and the pool connects to whatever listens
 * on the port, as before.
 */
@Component
@Slf4j
public class LibreOfficeServerManager {

    @Value("${libreoffice.path:soffice}")
    private String sofficePath;

    @Value("${libreoffice.server.managed:true}")
    private boolean managed;

    @Value("${libreoffice.server.port:2002}")
    private int port;

    @Value("${libreoffice.server.startup-timeout-seconds:90}")
    private int startupTimeoutSeconds;

    /** The running server, or null. Guarded by {@code this}. */
    private Process process;

    public boolean isManaged() {
        return managed;
    }

    /** True while a server this manager started is alive. */
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /**
     * Starts the server unless one is already answering on the port, and waits until it accepts
     * connections. Never throws: without a server the TOC refresh falls back to the CLI round-trip,
     * which is slower and leaves the page numbers stale but still produces a report.
     */
    public synchronized void start() {
        if (!managed) {
            log.info("LibreOffice server management is off (libreoffice.server.managed=false)");
            return;
        }
        if (isRunning()) {
            return;
        }
        if (isPortOpen()) {
            log.info("A LibreOffice server is already listening on port {} — using it as it is", port);
            return;
        }
        try {
            List<String> command = List.of(sofficePath,
                    "--headless", "--norestore", "--nologo", "--nodefault", "--nofirststartwizard",
                    "--accept=socket,host=localhost,port=" + port + ";urp;StarOffice.ServiceManager");
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            log.warn("Could not start LibreOffice ({}): {} — TOC refresh will use the CLI fallback",
                    sofficePath, e.getMessage());
            process = null;
            return;
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(startupTimeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (isPortOpen()) {
                log.info("LibreOffice headless server listening on port {} (pid {})", port, process.pid());
                return;
            }
            if (!process.isAlive()) {
                log.warn("LibreOffice exited with code {} before accepting connections on port {}",
                        process.exitValue(), port);
                process = null;
                return;
            }
            pause(500);
        }
        log.warn("LibreOffice did not open port {} within {}s", port, startupTimeoutSeconds);
    }

    /**
     * Stops and starts the server so it re-reads the installed fonts. Pooled connections are
     * dropped: after the restart they all point at a dead socket.
     */
    public synchronized void restart() {
        if (!managed) {
            return;
        }
        stop();
        start();
    }

    /** Stops the server this manager started, if any, and waits for the port to close. */
    @PreDestroy
    public synchronized void stop() {
        if (process == null) {
            return;
        }
        // soffice is a launcher that starts oosplash, which starts soffice.bin: the socket lives in
        // the grandchild, so the whole tree has to go or the port stays taken by a server that
        // still has the old font list.
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (isPortOpen() && System.nanoTime() < deadline) {
            pause(250);
        }
        log.info("LibreOffice headless server stopped");
        process = null;
        LibreOfficeConnectionPool.getInstance().clear();
    }

    private boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
