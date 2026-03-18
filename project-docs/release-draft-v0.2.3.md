# Release Draft: v0.2.3

This draft is for the next incremental release after `v0.2.2`.
Do not cut the release yet. One additional feature is still planned.

## Include From Prior Dev -> Main Fasttrack

- leaner app launch and resume animations
- polished app icon interaction feedback
- alphabet-row app filtering with upward drag into filtered apps, sideways glide across icons, and lift-to-launch
- usage-aware alphabet filtering so frequently launched apps surface earlier within their letter group
- immediate app-list refresh when apps are installed, without requiring an app restart
- `launcherctl tty-exec` for interactive Shizuku terminal commands through local `~/.rish/rish`
- `launcherctl tty-doctor` for validating `~/.rish` setup and printing exact remediation commands
- LauncherCtl docs and README updates covering `/v1/exec` vs `tty-exec`
- Android 14+ dex permission documentation for `~/.rish/rish_shizuku.dex`

## Include From Current Dev Branch

- removed the fork-specific exported native loader surface from app wiring
- removed the public restart broadcast path and replaced it with authenticated `launcherctl restart`
- made LauncherCtl startup lazy to reduce cold-start overhead
- moved launcher app indexing and search work off the UI thread
- reduced launcher persistence churn by coalescing usage-stats writes
- fixed alphabet-row icon rendering so icons populate in the current interaction instead of only on a second pass
- updated `tooie --restart` to use the authenticated app restart path, with safe local fallback behavior

## Suggested Release Framing

This release should be framed as:

- launcher interaction polish
- Shizuku / LauncherCtl quality-of-life improvements
- fork-specific security hardening
- launcher performance cleanup

## Hold Before Release

- add the planned final feature
- then bump `versionName` in `app/build.gradle`
- then create the GitHub release body from this draft
