# Repository guidance

Follow the owner's release increments in VERSIONING.md: minor/fix +0.0.1, medium feature update +0.1.0, major +1.0.0. Reset lower components when incrementing a higher one. Maven's root project version is the source of truth; plugin.yml receives it through filtering.

Build with Java 21 and `mvn -B verify`. For a release, update the README and PERMISSIONS.md as needed, preserve earlier downloads, and add the new versioned JAR and SHA-256 checksum to downloads/. Distinguish unit validation from live Minecraft/LuckPerms/Multiverse testing. Do not claim a push or deployment without verifying it.
