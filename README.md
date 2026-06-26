# Phase 0B Static Maven Probe

Temporary artifact branch for a bounded public static-Maven feasibility proof.
Do not promote or rely on this branch beyond the probe.

## Source

- Repository: `https://github.com/PickleHik3/termux-launcher`
- Branch: `io-vaj-package`
- Source commit: `841b424a46ad20c382579e21f59e82ceb29b59ee`

## Publication commands

```bash
./gradlew :termux-shared:publishToMavenLocal \
          :terminal-view:publishToMavenLocal \
          :terminal-emulator:publishToMavenLocal \
          :termux-am-library:publishToMavenLocal
```

The `terminal-view` release resource-verification task (`:terminal-view:verifyReleaseResources`)
fails when run as part of `:terminal-view:assembleRelease`, but the Maven-publish path above
publishes the artifacts without executing that task, which is why this static-Maven carrier is
being probed separately from the JitPack attempt.

## Published artifacts

| groupId | artifactId | version | files |
|---|---|---|---|
| `com.termux` | `termux-shared` | `0.118.0` | POM, module metadata, release/debug AAR, sources jar, javadoc jar |
| `com.termux` | `terminal-view` | `0.118.0` | POM, module metadata, release/debug AAR, sources jar, javadoc jar |
| `com.termux` | `terminal-emulator` | `0.118.0` | POM, module metadata, release/debug AAR, sources jar, javadoc jar |
| `com.termux` | `termux-am` | `2.0.0` | POM, sources jar |

SHA-256 checksums are recorded in `checksums-sha256.txt`.
Total repository tree size: ~8.3 MB; no file reaches GitHub's 100 MB limit.

## Maven repository base

```text
https://raw.githubusercontent.com/PickleHik3/termux-launcher/<ARTIFACT_COMMIT_SHA>/maven/
```

Replace `<ARTIFACT_COMMIT_SHA>` with the exact commit SHA of this artifact branch.
