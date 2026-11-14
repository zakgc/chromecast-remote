# GitHub releases

Pushing a tag such as `v1.0.0` runs `.github/workflows/release.yml` on GitHub.
The workflow installs the existing Windows build tools, runs all unit tests and
Android lint, builds the APK, verifies its signing certificate, and publishes a
GitHub release with the APK, SHA-256 checksum and generated release notes.
Nothing is published if a build or verification step fails.

## One-time setup

1. In the repository on GitHub, open **Settings → Secrets and variables → Actions → New repository secret**.
2. Name the secret `ANDROID_DEBUG_KEYSTORE_BASE64`.
3. From the project folder in PowerShell, copy the existing key to the clipboard as Base64:

   ```powershell
   $keyPath = (Resolve-Path .tools/android-user/debug.keystore).Path
   [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
   ```

4. Paste the clipboard into the secret value and save. Clear the clipboard afterwards:

   ```powershell
   Set-Clipboard -Value ''
   ```

Keep the signing key backed up privately. Base64 is an encoding, so treat the
secret value like the original key. Do not commit the key or its encoded value.
The workflow checks against the signing certificate of the existing APK so it
cannot accidentally publish an update signed with a different key. The APK
continues to use the original debug signing key to support existing installs.
This workflow does not change it to an Android release build.

GitHub Actions must be enabled for the repository. It uses GitHub's automatic
token with `contents: write`; no personal access token is needed.
The setup step accepts the Android SDK licenses; review those terms before
enabling this workflow.

## Publish a version

1. In `app/build.gradle.kts`, set `versionName` (currently `1.0.1`) and increase
   `versionCode` (currently `14`). Every update needs a higher code.
2. Commit and push the changes, including the release workflow on its first run.
3. Tag that commit using the exact version with a `v` prefix, then push the tag:

   ```powershell
   git tag -a v1.0.1 -m "Chromecast Remote 1.0.1"
   git push origin v1.0.1
   ```

Review progress under **Actions → Release Android APK**.
When it succeeds, the download is on the repository's **Releases** page.
These tags publish regular releases; prerelease suffixes are not supported.
An existing release for the same tag is never overwritten. If publishing fails
after a draft is created, inspect that draft before retrying.

If a tag fails because it does not match `versionName`, rerunning the same tag
will still build the old commit. Update the app version, commit and push it,
then create a matching tag. To reuse a failed tag with no published release,
replace it after committing the corrected version:

```powershell
git tag -f -a v1.0.1 -m "Chromecast Remote 1.0.1"
git push --force origin refs/tags/v1.0.1
```

Local builds still work with `./scripts/build.ps1`. Filenames now follow the
version in `app/build.gradle.kts` automatically. To check tag matching locally,
run `./scripts/build.ps1 -ReleaseTag v1.0.1`.

## References

- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create)
