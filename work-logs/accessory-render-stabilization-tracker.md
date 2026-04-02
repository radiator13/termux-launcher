# Accessory Render Stabilization Tracker

Goal: keep blur/grain/opacity and launcher features, while stabilizing rendering and reducing flicker/stale overlay state.

- [x] Step 1: Introduce an explicit Accessory Render Controller path in `TermuxActivity` for accessory visibility/background/blur/AZ+app row state.
- [ ] Step 2: Route all accessory visibility mutations through the controller (remove ad-hoc direct `setVisibility()` writes).
- [ ] Step 3: Split terminal-domain and accessory-domain state updates so terminal redraw does not carry accessory mutations.
- [ ] Step 4: Normalize keyboard/open-close transitions into one state transition entrypoint and one post-layout sync.
- [ ] Step 5: Enforce FX visibility invariants (if accessory stack hidden, all AZ/page-indicator FX must be reset+gone).
- [ ] Step 6: Reduce overlapping live-blur regions while preserving visual options (selective static tint fallback per region).
- [ ] Step 7: Stabilize insets/margin sequencing against upstream behavior with minimal dynamic edge-mode churn.
- [ ] Step 8: Add debug diagnostics for accessory/terminal state snapshots on keyboard transitions.
- [ ] Step 9: Profile frame timing (`gfxinfo`/frame metrics) for keyboard open/close and heavy terminal output scenarios.
- [ ] Step 10: Harden with regression checklist and finalize cleanup/refactor pass.
