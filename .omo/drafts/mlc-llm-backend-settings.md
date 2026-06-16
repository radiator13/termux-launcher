# Draft: MLC LLM Backend and Settings Options

## Requirements (confirmed)
- Add `https://github.com/mlc-ai/mlc-llm` backend.
- Add user-facing options in settings/preferences pages.
- Support direct model downloads.
- Add feature gating depending on device support.
- App should host its own local LLM server that users can expose on the local network.
- Users should be able to configure existing terminal LLM projects to use the app-provided OpenAI-compatible endpoint.
- Existing Google LiteRT backend already does a similar local-server/OpenAI-compatible flow; MLC should be a separate backend for more model choices.
- Model downloads should support curated catalog plus custom sources.
- Unsupported devices should show disabled controls with a clear reason.

## Technical Decisions
- Intent classified as Architecture: new backend integration plus UI settings, download flow, and device capability gating likely spans multiple modules.
- Existing OpenSpec/Spec Kit directories not detected in initial workspace scan.
- First-release backend scope: separate MLC-powered local server backend, not replacing the existing Google LiteRT backend.
- UX decision: feature gates disable MLC controls with explicit reason instead of hiding or allowing unsupported experimental runs.
- Download UX decision: curated catalog plus advanced custom model source entry.
- API compatibility decision: first release should support chat plus embeddings for MLC-backed OpenAI-compatible endpoint.
- Network exposure decision: LAN exposure must be opt-in only; default should remain local/private.
- Test strategy decision: tests-after recommended, with new/updated automated coverage after implementation and agent-executed QA scenarios.
- Custom model decision: first release accepts only precompiled MLC-compatible packages/artifacts/manifests; raw model weights must be rejected with a clear reason.
- Server lifecycle default: mirror current app-process server startup/shutdown and LiteRT keep-warm/idle-unload/cancel semantics rather than introducing a new foreground inference service.
- LAN security default: bind localhost by default; LAN bind is opt-in, token remains required, CORS remains disabled unless explicitly added later, and settings must display a warning.
- Native artifact trust decision: first release must not load custom downloadable native `.so`/compiled model-library code. Custom URLs may provide data/config only when compatible with app-bundled or curated/signed model libraries; otherwise reject with a clear unsafe-native-artifact reason.

## Research Findings
- Codebase: existing AI stack is LiteRT-first around `TaiManager`, `TaiPreferencesFragment`, `TaiModelDownloader`, `TaiModelDownloadService`, `TaiModelCatalog`, `TaiRemoteCatalog`, `TaiDeviceCapabilities`, and `LiteRtTaiRuntime`.
- Codebase: `TaiModelSpec.inferBackend()` and catalog filtering are currently hardwired to LiteRT backend values, so MLC requires schema/backend branches rather than just adding a catalog row.
- Codebase: no generic app-side feature flag system is actively used; feature gating is via device/runtime checks and preference visibility/disabled state.
- Tests: Gradle unit tests and Android instrumentation exist; no automated e2e suite found. Baseline commands include `./gradlew assembleDebug`, `./gradlew :app:testDebugUnitTest :terminal-emulator:test`, and CI uses `./gradlew --no-daemon testDebugUnitTest` plus `assembleDebug`.
- MLC docs: official Android path assumes native Android/NDK packaging on physical devices, not Termux as an official target. MLC artifacts include compiled libs plus weights/config; model sources should be compatible MLC packages, commonly hosted on Hugging Face.
- MLC docs: official REST/OpenAI-compatible docs clearly cover `/v1/models` and `/v1/chat/completions`; embedding support may need explicit model/runtime capability detection and disabled endpoint behavior if unavailable.
- Existing local server: `LauncherCtlApiServer` binds `127.0.0.1`, defaults port `41237`, requires bearer token, exposes `/v1/models`, `/v1/chat/completions`, `/v1/completions`, and has no LAN toggle/CORS/embeddings endpoint today.
- Existing lifecycle: `TermuxActivity.onStart()` starts the API server, `TermuxApplication.onTerminate()` stops it, and `LiteRtTaiRuntime` provides load/keep-warm/idle-unload/cancel/unload semantics.

## Open Questions
- Whether MLC supports embeddings in the target integration path, or whether the plan needs graceful disablement for embedding endpoints when unsupported by selected model/runtime.

## Scope Boundaries
- INCLUDE: planning backend integration, settings/preferences UI, direct model download flow, and device support gating.
- EXCLUDE: implementation until a work plan is generated and executed via `/start-work`.
