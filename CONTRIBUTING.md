# Development and forks

> **This project is abandoned.** There is no active maintainer, support commitment, planned release schedule or promise that issues and pull requests will be reviewed. If you want changes, **fork this repository and maintain your own version**. Preserve the license and third-party notices.

For installation and everyday use, start with the [README](README.md).

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/xoclassic/app/` | Native interface, forum parser/client, session storage, previews and update flow |
| `app/src/main/assets/` | Bundled offline article reader and its licenses |
| `app/src/main/res/` | Icons and Android resources |
| `app/libs/jsoup-1.24.1.jar` | Pinned HTML parser dependency |
| `build.ps1` | Standalone Windows build and packaging checks |
| `app/build.gradle`, `build.gradle`, `settings.gradle` | Android Studio / Gradle project |
| `docs/media/` | README artwork and illustrative screens; not APK assets |
| `.local/`, `dist/` | Ignored private build state and generated APKs |

## Build on Windows

The standalone build expects Java 17, Android platform API 36 and Android Build Tools 36.0.0. Minimum runtime is Android API 26. Inspect the manifest and build files for the current target and release version.

From the repository directory in PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

By default, the script uses the Android toolchain supplied with Unity 6000.3.2f1. To use another AndroidPlayer-style toolchain directory containing `SDK` and `OpenJDK`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -AndroidTools "C:\AndroidToolchain"
```

The script checks the bundled jsoup JAR checksum, compiles resources and Java, produces DEX, validates the APK payload, normalizes archive timestamps, aligns the APK and signs it. It does not download dependencies. Output is written under `dist/`; the exact filename and checksum are printed by the build.

You can also open the project in Android Studio. Gradle project files are supplied, but a Gradle wrapper is not bundled. Android Studio may download the Android Gradle Plugin and SDK components. The standalone PowerShell build is the documented release path; do not assume Android Studio uses the same signing configuration.

## Signing and existing installations

The standalone build keeps its development keystore in `.local/signing/`. If the key is absent, it generates a new development key with a generic certificate name and the standard public development password. That password is not a website credential, and it does not make the key suitable for public storage.

**Never commit or publish any private signing key.** Keep a separate secure backup of the signing identity used for your own releases. A fork must create and protect its own signing identity; it does not receive the original distributor’s private key.

For an in-place Android update, the package name and signing identity must match the installed app and the Android `versionCode` must increase. Changing the signing identity prevents an ordinary update, and uninstalling can erase local data. Give a fork a different application ID if it should install alongside another distribution; review hardcoded authorities and package references as part of that change.

Release versions currently appear in both `build.ps1` and `app/build.gradle`. Keep those values consistent. Keep signing identity stable for your distribution.

## Website compatibility

Forum pages are parsed as data and rendered natively. Native HTTP maintains separate per-site cookies. External media/article fetches and hosted preview WebViews have separate responsibilities; do not pass forum credentials to external providers.

Review real website forms before changing login, posting, editing or moniker flows. Preserve the distinction between a new thread’s hidden title (`subj`) and an optional post subject (`message_subject`). Preserve required hidden fields, successful submit controls and advertised limits. Do not infer ownership from a posting name: use the Edit links exposed by the site.

A mocked success response is not proof of a successful real post. Tests should compare complete outgoing payloads with sanitized, observed forms. Keep captured forms, derived variants, deliberately malformed inputs and pure unit fixtures clearly distinguished.

Do not automatically retry uncertain mutations. Check the actual result before another submission. Preserve reading position, explicit-only tabs, request pacing, content freshness and cancellation behavior when changing asynchronous work.

## Testing honestly

No self-contained, verified regression suite is included in this public source tree. The original development checks used external scripts and fixtures; their machine-local paths and private data are intentionally not part of this repository. Historical check counts are not a substitute for reproducible validation of a fork.

For your fork:

- Test first launch, login/logout, account switching, thread creation, direct and thread-level replies, optional subjects, editing and uncertain submissions.
- Verify both supported hosts against current HTML. Record unavailable authenticated checks as incomplete rather than passed.
- Use intercepted mutation requests or controlled fixtures first. Do not post test content to public threads without deliberate authorization.
- Exercise thread refresh, scroll return, tabs/undo/restoration, unread tracking, search, landscape, large fonts and accessibility.
- Check media/provider failures, upload cancellation, APK download limits, update signing checks and Android’s installation confirmation.
- Never commit real passwords, cookies, API keys, private keys, session tokens, browsing history or screenshots of private account details. Sanitize before saving any fixture or report.

Recent header/unread and updater changes have not completed live end-to-end phone verification. The updater also needs a real public release source before its complete GitHub-to-installer flow can be exercised. Verify these yourself before distributing a fork.

## GitHub release updates

The update source defaults to the official public repository and can be changed under Account → Updates. Fork maintainers should replace AppUpdater.DEFAULT_REPOSITORY with their own public release repository. It uses public GitHub Releases without a token. Publishing an APK does not require publishing your private build folder or signing material.

For a release of your fork:

1. Increase `versionCode` and set a numeric version name in both build configurations.
2. Build, inspect and test the signed APK. Preserve the correct signing identity for existing users.
3. Create a stable GitHub Release with a numeric tag such as `v1.0.0`.
4. Attach the APK as **`AutoAdmit.apk`**. If there is no asset with that name, the updater accepts a release only when it has exactly one APK asset.
5. Add useful release notes. Users enter your public `owner/repository` in the app.
6. Verify the release list, chosen-version notes, downloaded APK identity and Android install prompt on a device.

Drafts, prereleases, unsupported tags, missing/ambiguous APKs, unapproved download destinations and oversized assets are rejected or omitted. Older versions can be inspected but are not installed as downgrades. Update checks are manual. Do not add silent installation or weaken package/signing checks to work around a release configuration mistake.

## Source and distribution privacy

`.gitignore` excludes private build state, generated APKs, keystores, environment files, common credential files, editor state and scratch diagnostics. **Ignore rules do not remove files already tracked or erase commit history.** Inspect the actual Git index before publishing:

```powershell
git status --short
git diff --cached --stat
git ls-files
```

Review commit author/committer identities too. Use your own GitHub no-reply address if you do not want an email address published. Keep account-specific Git settings local to the repository rather than changing every project on the machine.

The packaging guard checks the APK allowlist and several recognizable private-path/secret patterns. These checks are useful but do not prove that all possible personal data is absent. Inspect dependency contents, compiled strings, resources, archive/image metadata and signing certificate metadata when preparing a public build. Preserve required public vendor attribution. Keep README media fictional and free of personal accounts, device status bars and private paths.

Uploaded user images retain their original embedded metadata; the APK privacy checks do not sanitize those uploads. Treat an upload-time metadata-removal feature as a separate change.

## Documentation artwork

The README’s GIF banner and PNG screens are illustrative artwork with fictional content. They are not device screenshots or evidence of tested behavior. Keep that disclosure when reusing them. The media lives under `docs/media/` and is excluded from the APK by the packaging allowlist.

## Maintenance policy

This repository should be treated as **abandoned**. Do not depend on responses, upstream fixes or security maintenance. If you need a fix, feature, new build or ongoing support, **fork it, make your changes, and distribute your own maintained version** under the license terms.
