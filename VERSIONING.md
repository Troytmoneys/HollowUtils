# Version increments

Use the owner's release policy:

| Change size | Increment | Example from 1.1.0 |
| --- | --- | --- |
| Minor change / fix | +0.0.1 | 1.1.1 |
| Medium feature update | +0.1.0, reset final component | 1.2.0 |
| Major release | +1.0.0, reset remaining components | 2.0.0 |

The current medium feature release is **1.1.0**. Maven's project version is the source of truth; `plugin.yml` receives it through resource filtering. `/version` reads the built plugin metadata, so it reports the installed JAR's version.

For a release, update the project version in `pom.xml`, run `mvn -B verify` with JDK 21, and copy the new JAR to `downloads/hollowutils-<version>.jar` with its SHA-256 checksum. Update the README download/build paths. Keep prior versioned downloads intact. Do not announce a GitHub push or successful tests until verified.
