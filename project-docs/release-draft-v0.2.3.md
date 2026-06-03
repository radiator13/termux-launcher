# v0.2.3

- Smoother launcher interactions, including improved alphabet drag filtering, icon focus, and looping page changes.
- Faster launcher response with background app indexing, reduced startup work, and less UI-thread search/load churn.
- Safer app behavior by removing the fork-only native loader entrypoint and replacing public restart broadcasts with authenticated restart handling.
- Better LauncherCtl and Shizuku workflows, including `tty-doctor` checks and improved restart support in the companion tooling.
