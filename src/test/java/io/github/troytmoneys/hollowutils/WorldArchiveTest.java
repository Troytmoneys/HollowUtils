package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class WorldArchiveTest {
    @TempDir Path temp;
    @Test void preservesWorldDataAndSkipsSessionLock() throws Exception {
        Path world = Files.createDirectories(temp.resolve("world/region"));
        byte[] bytes = {1, 2, 3, 0, -1};
        Files.write(world.resolve("r.0.0.mca"), bytes);
        Files.writeString(world.getParent().resolve("session.lock"), "lock");
        Path backup = temp.resolve("backups/test.zip");
        WorldArchive.write(backup, Map.of("hu_test", world.getParent()));
        try (ZipFile zip = new ZipFile(backup.toFile())) {
            assertEquals(1, zip.size());
            assertArrayEquals(bytes, zip.getInputStream(zip.getEntry("hu_test/region/r.0.0.mca")).readAllBytes());
            assertNull(zip.getEntry("hu_test/session.lock"));
        }
    }
    @Test void refusesSymlinksAndRemovesPartialBackup() throws Exception {
        Path world = Files.createDirectory(temp.resolve("world"));
        Path outside = Files.writeString(temp.resolve("secret"), "private");
        Files.createSymbolicLink(world.resolve("link"), outside);
        Path backup = temp.resolve("backups/test.zip");
        assertThrows(IOException.class, () -> WorldArchive.write(backup, Map.of("hu_test", world)));
        assertFalse(Files.exists(backup));
        try (var files = Files.list(backup.getParent())) { assertEquals(0, files.count()); }
    }
    @Test void refusesMissingWorldsInsteadOfReportingSuccessfulBackup() {
        assertThrows(IOException.class, () -> WorldArchive.write(temp.resolve("backup.zip"), Map.of("test", temp.resolve("missing"))));
    }
    @Test void refusesUnsafeArchiveRoot() throws Exception {
        Path world = Files.createDirectory(temp.resolve("world"));
        assertThrows(IOException.class, () -> WorldArchive.write(temp.resolve("backup.zip"), Map.of("../escape", world)));
    }
    @Test void neverOverwritesAnExistingBackup() throws Exception {
        Path world = Files.createDirectory(temp.resolve("world"));
        Path backup = Files.writeString(temp.resolve("backup.zip"), "existing");
        assertThrows(IOException.class, () -> WorldArchive.write(backup, Map.of("test", world)));
        assertEquals("existing", Files.readString(backup));
    }
}
