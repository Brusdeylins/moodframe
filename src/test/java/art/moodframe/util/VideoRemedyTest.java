package art.moodframe.util;

import art.moodframe.util.VideoRemedy.Kind;
import art.moodframe.util.VideoRemedy.Remedy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** What helps to play videos under Linux, from os-release files and library directories of test systems. */
class VideoRemedyTest {

    @TempDir
    Path dir;

    @AfterEach
    void forget() {
        VideoRemedy.reset();
    }

    /*  A Linux with the given os-release, null for none, and the given libraries in /usr/lib and /usr/lib64  */
    private Remedy linux(String osRelease, String... libraries) throws IOException {
        return detect("Linux", osRelease, libraries);
    }

    private Remedy detect(String osName, String osRelease, String... libraries) throws IOException {
        Path release = dir.resolve("os-release");
        if (osRelease != null)
            Files.writeString(release, osRelease);
        Path lib = Files.createDirectories(dir.resolve("usr/lib"));
        Path lib64 = Files.createDirectories(dir.resolve("usr/lib64"));
        for (int i = 0; i < libraries.length; i++)
            Files.createFile((i % 2 == 0 ? lib : lib64).resolve(libraries[i]));
        return VideoRemedy.detect(osName, release, List.of(lib, lib64, dir.resolve("usr/lib/x86_64-linux-gnu")));
    }

    @Test
    void theArchFamilyGetsFfmpeg44() throws IOException {
        assertEquals(new Remedy(Kind.ARCH, 62), linux("NAME=\"Arch Linux\"\nID=arch\n",
                "libavcodec.so.62", "libavcodec.so.62.11.100", "libavformat.so.62"));
        assertEquals("sudo pacman -S ffmpeg4.4", Kind.ARCH.command());
    }

    @Test
    void omarchyIsOfTheArchFamily() throws IOException {
        assertEquals(new Remedy(Kind.ARCH, 62), linux("NAME=\"Omarchy\"\nID=omarchy\nID_LIKE=arch\n",
                "libavcodec.so.62"));
    }

    @Test
    void manjaroWithoutAnyLibavcodecIsOfTheArchFamily() throws IOException {
        assertEquals(new Remedy(Kind.ARCH, 0), linux("ID=manjaro\nID_LIKE=\"arch\"\n"));
    }

    @Test
    void debian12WithASupportedLibavcodecNeedsNothing() throws IOException {
        assertNull(linux("PRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\"\nID=debian\n",
                "libavcodec.so.59", "libavcodec.so.59.37.100"));
    }

    @Test
    void anArchWithFfmpeg44NeedsNothing() throws IOException {
        assertNull(linux("ID=arch\n", "libavcodec.so.62", "libavcodec.so.58"));
    }

    @Test
    void debian13WithLibavcodec61NeedsNothing() throws IOException {
        assertNull(linux("ID=debian\nVERSION_ID=\"13\"\n", "libavcodec.so.61", "libavcodec.so.61.19.101"));
    }

    @Test
    void debianWithOnlyLibavcodec62OpensTheVideoPlayer() throws IOException {
        assertEquals(new Remedy(Kind.DEBIAN_NEWER, 62), linux("ID=debian\nVERSION_ID=\"14\"\n",
                "libavcodec.so.62", "libavcodec.so.62.11.100"));
        assertNull(Kind.DEBIAN_NEWER.command());
    }

    @Test
    void ubuntuWithoutLibavcodecGetsFfmpeg() throws IOException {
        assertEquals(new Remedy(Kind.DEBIAN_MISSING, 0), linux("ID=ubuntu\nID_LIKE=debian\n"));
        assertEquals("sudo apt install ffmpeg", Kind.DEBIAN_MISSING.command());
    }

    @Test
    void mintIsOfTheDebianFamily() throws IOException {
        assertEquals(new Remedy(Kind.DEBIAN_MISSING, 0), linux("ID=linuxmint\nID_LIKE='ubuntu debian'\n"));
    }

    @Test
    void fedoraIsAnotherLinux() throws IOException {
        assertEquals(new Remedy(Kind.OTHER, 62), linux("ID=fedora\n", "libavutil.so.60", "libavcodec.so.62"));
        assertNull(Kind.OTHER.command());
    }

    @Test
    void aLinuxWithoutOsReleaseIsAnotherLinux() throws IOException {
        assertEquals(new Remedy(Kind.OTHER, 0), linux(null));
    }

    @Test
    void macOSAndWindowsNeedNothing() throws IOException {
        assertNull(detect("Mac OS X", "ID=arch\n"));
        assertNull(detect("Windows 11", null));
    }

    @Test
    void detectsOncePerSession() {
        assertSame(VideoRemedy.detect(), VideoRemedy.detect());
        VideoRemedy.assume(CompletableFuture.completedFuture(new Remedy(Kind.ARCH, 0)));
        assertEquals(new Remedy(Kind.ARCH, 0), VideoRemedy.detect().join());
    }
}
