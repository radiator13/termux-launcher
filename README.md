# VAJ Terminal Android Shared Libraries — Static Maven Mirror

Durable Maven-layout artifact branch for the forked Termux Android shared libraries.

## Source

- Repository: `https://github.com/PickleHik3/termux-launcher`
- Branch: `io-vaj-package`
- Source commit: `d86d607779798aa7fc4d2ca0627fcf4b1acf0c97`

## Publication commands

```bash
./gradlew :termux-shared:publishToMavenLocal \
          :terminal-view:publishToMavenLocal \
          :terminal-emulator:publishToMavenLocal \
          :termux-am-library:publishToMavenLocal \
          -Dmaven.repo.local=./m2
```

## Published artifacts

| groupId | artifactId | version |
|---|---|---|
| `com.termux` | `termux-shared` | `0.118.0` |
| `com.termux` | `terminal-view` | `0.118.0` |
| `com.termux` | `terminal-emulator` | `0.118.0` |
| `com.termux` | `termux-am` | `2.0.0` |

## Immutable consumption rule

Consume this repository only by exact commit SHA; do not use branch names or mutable references:

```text
https://raw.githubusercontent.com/PickleHik3/termux-launcher/<ARTIFACT_COMMIT_SHA>/maven/
```

SHA-256 checksums for every artifact file are recorded in `checksums-sha256.txt`.
