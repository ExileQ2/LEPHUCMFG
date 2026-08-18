# LEPHUCMFG native rebuild plan

## Goal

Rebuild the machine-log workflow as a native Kotlin/Jetpack Compose screen, using the current React `MachineLog` behavior as the source of truth. Keep the existing package name and signing identity so the new APK can update the installed legacy APK.

The native app removes the browser certificate/camera problem. It continues to call the internal LPWebAPI over the LAN.

## Scope

Machine-log parity includes:

- staff lookup and remembered staff identity;
- machine lookup, active-process lookup, status banner, and machines `001` through `013` override rule;
- active-process auto-fill for job, production order, serial selection, and note;
- routing-step lookup and composition of the final job number;
- production-order lookup, single-order auto-selection, multi-order choices, and serial prefetch;
- serial expansion, range compression, highlighted serials for the selected job, select-all, and one-production-order-at-a-time selection;
- Rework, Setup, active-process quantities, note, validation, submit feedback, and the five most recent successful declarations;
- native QR scanning for staff, machine, and job with a framed viewfinder, gallery import, torch, camera switch, and zoom;
- cancellation of stale requests when the operator changes input quickly.

Material Log, IoT dashboards, and test screens remain available through their existing activities. They are not rewritten in this pass.

## Architecture

- `network/MfgApiService.kt`: Retrofit contract for the live LPWebAPI.
- `data/machinelog/*`: API models, repository, local staff/history storage.
- `ui/machinelog/MachineLogLogic.kt`: pure parsing, serial, banner, and validation rules.
- `ui/machinelog/MachineLogViewModel.kt`: state machine, request cancellation, auto-fill, and submission.
- `ui/machinelog/MachineLogScreen.kt`: Compose UI and dialogs.
- `MachineLogActivity.kt`: activity-result bridge for the native scanner.
- `CustomScanActivity.kt`: CameraX/ML Kit scanner, one analyzer instance, torch, camera switching, gallery, and zoom.

## Update design

The installed app compares integer `versionCode`, never free-form version strings.

LPWebAPI publishes a release manifest containing:

- `versionCode` and `versionName`;
- version-specific `downloadUrl`;
- APK byte size and SHA-256;
- `minSupportedVersionCode` and release notes.

The app checks on launch with a throttle and exposes a manual check. It downloads to an app-private, version-specific cache filename, verifies byte count, SHA-256, package name, version code, and APK signer, then opens Android's normal install confirmation. On the next launch it deletes all cached APKs. Optional releases can be postponed; releases below `minSupportedVersionCode` cannot.

The backend keeps the old `/api/android/version` and `/api/android/download` routes for legacy clients while the new app uses `/api/android/releases/latest` and versioned download routes.

`tools/publish-android-release.ps1` is the only release entry point: it builds or accepts the signed APK, copies it to the versioned server folder, calculates SHA-256, writes the manifest, and refreshes the legacy `LPMFG.apk` copy.

## Verification

- JVM unit tests for QR parsing, serial parsing/summary, banner/blocking rules, and version decisions.
- `gradlew testDebugUnitTest` and `gradlew assembleRelease`.
- `apksigner verify --print-certs` against both old and new APKs.
- `dotnet test` and `dotnet publish` for LPWebAPI.
- Read-back of generated `latest.json`, SHA-256, APK package/version, and output paths.

## Rollback and risks

- The existing debug signing key is retained for update compatibility. It must be backed up; losing it makes future in-place updates impossible.
- The app uses cleartext HTTP only for the internal `192.168.1.68` API. APK signer verification protects installation even if update traffic is altered.
- The legacy APK remains available until the first native release is validated on at least one real device.
