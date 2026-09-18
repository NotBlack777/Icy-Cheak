# CI signing key

`devcheck-ci.jks` is a self-signed certificate used **only** by GitHub Actions so
that every APK published to Releases is signed with the *same* key.

That matters for the in-app updater: Android refuses to install an update whose
signature differs from the installed package (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
A runner-generated debug keystore changes on every workflow run, so builds signed
with it could never update each other.

| Field         | Value          |
| ------------- | -------------- |
| Alias         | `devcheck`     |
| Store password| `devcheckci`   |
| Key password  | `devcheckci`   |
| Validity      | 30 years       |

The password is not a secret — this key is committed on purpose, and anyone with
the source tree can already build and sign their own copy of the app. It grants
nothing beyond "this APK came from a DevCheck+ CI build".

## Replacing it with your own key

Point Gradle at a different keystore without touching the build script:

```properties
# ~/.gradle/gradle.properties or -P on the command line
devcheckKeystore=/absolute/path/to/release.jks
devcheckStorePassword=...
devcheckKeyAlias=...
devcheckKeyPassword=...
```

Or store the same four values as repository secrets and export them in
`.github/workflows/build.yml`. If `devcheckKeystore` is set, the committed CI
keystore is ignored. Delete `devcheck-ci.jks` afterwards — the release build then
requires your own key to be present.
