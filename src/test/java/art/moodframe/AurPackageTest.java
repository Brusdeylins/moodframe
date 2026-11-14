package art.moodframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AUR package moodframe-bin in packaging/aur: the .SRCINFO, from which the AUR shows the package, matches the
 * PKGBUILD as bash reads it, and the checksums of the files next to the PKGBUILD are right. Both are kept up to date by
 * packaging/aur/update.sh.
 */
@DisabledOnOs(OS.WINDOWS)
class AurPackageTest {

    private static final Path PACKAGE = Path.of("packaging/aur/moodframe-bin");

    /*  prints pkgname, then every set attribute as name=value in the order of "makepkg --printsrcinfo"  */
    private static final String READ_PKGBUILD = """
            source "$1"
            sums="cksums md5sums sha1sums sha224sums sha256sums sha384sums sha512sums b2sums"
            emit() {
                local ref="$1[@]" value
                if declare -p "$1" >/dev/null 2>&1; then
                    for value in "${!ref}"; do
                        printf "%s=%s\\n" "$1" "$value"
                    done
                fi
            }
            echo "pkgname=$pkgname"
            for name in pkgdesc pkgver pkgrel epoch url install changelog arch groups license checkdepends \\
                    makedepends depends optdepends provides conflicts replaces noextract options backup source \\
                    validpgpkeys $sums; do
                emit "$name"
            done
            for a in "${arch[@]}"; do
                [ "$a" = any ] && continue
                for name in source provides conflicts depends replaces optdepends makedepends checkdepends $sums; do
                    emit "${name}_$a"
                done
            done
            """;

    /*  the lines name=value of the PKGBUILD, the first one with the pkgname  */
    private static List<String> readPkgbuild() throws Exception {
        Process bash = new ProcessBuilder("bash", "-c", READ_PKGBUILD, "bash", PACKAGE.resolve("PKGBUILD").toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(bash.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(bash.waitFor(10, TimeUnit.SECONDS) && bash.exitValue() == 0, output);
        return output.lines().toList();
    }

    private static List<String> values(List<String> attributes, String name) {
        return attributes.stream()
                .filter((line) -> line.startsWith(name + "="))
                .map((line) -> line.substring(name.length() + 1))
                .toList();
    }

    @Test
    void srcinfoMatchesThePkgbuild() throws Exception {
        List<String> attributes = readPkgbuild();
        String pkgname = values(attributes, "pkgname").get(0);
        String expected = "pkgbase = " + pkgname + "\n"
                + attributes.stream().skip(1)
                        .map((line) -> "\t" + line.replaceFirst("=", " = ") + "\n")
                        .collect(Collectors.joining())
                + "\npkgname = " + pkgname + "\n";
        assertEquals(expected, Files.readString(PACKAGE.resolve(".SRCINFO")), "run packaging/aur/update.sh");
        assertEquals("moodframe-bin", pkgname);
    }

    @Test
    void theChecksumsOfTheFilesNextToThePkgbuildAreRight() throws Exception {
        List<String> attributes = readPkgbuild();
        List<String> sources = values(attributes, "source");
        List<String> sums = values(attributes, "sha256sums");
        assertEquals(sources.size(), sums.size(), "one checksum per source");

        /*  the download is checked by update.sh against the build, here only the files of this repository  */
        int files = 0;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).contains("://"))
                continue;
            byte[] content = Files.readAllBytes(PACKAGE.resolve(sources.get(i)));
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), sums.get(i),
                    sources.get(i) + ": run packaging/aur/update.sh");
            files++;
        }
        assertTrue(files > 0, "the .desktop file and the license");
    }
}
