package io.github.troytmoneys.hollowutils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class WorldArchive {
    private WorldArchive() {}
    // Called at a synchronous save barrier: no ticks or player actions can alter worlds while copying.
    static void write(Path destination, Map<String, Path> roots) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "backup-", ".partial");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (var root : roots.entrySet()) {
                    if (!root.getKey().matches("[a-zA-Z0-9_-]+")) throw new IOException("Unsafe archive root");
                    Path directory = root.getValue();
                    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing directory: " + directory);
                    try (var paths = Files.walk(directory)) {
                        for (Path file : paths.sorted().toList()) {
                            if (Files.isSymbolicLink(file)) throw new IOException("Refusing symlink: " + file);
                            if (!Files.isRegularFile(file)) continue;
                            String relative = directory.relativize(file).toString().replace(File.separatorChar, '/');
                            if (relative.equals("session.lock")) continue;
                            zip.putNextEntry(new ZipEntry(root.getKey() + "/" + relative));
                            Files.copy(file, zip);
                            zip.closeEntry();
                        }
                    }
                }
            }
            Files.move(temporary, destination); // Never overwrite an existing backup.
        } finally { Files.deleteIfExists(temporary); }
    }
}
