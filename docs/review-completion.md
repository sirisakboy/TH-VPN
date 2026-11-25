# Connection/diagnostic deep review — 2026-11-24

Reviewed the latest main snapshot `adc83bc` and integrated it on the current work branch before fixes.

## Findings and corrections

| Finding | Correction |
| --- | --- |
| Mandatory Google Services task failed when no Firebase project configuration existed | Apply the plugin only when `app/google-services.json` exists. Default build runs local VPN mode; do not manufacture Google API keys. Firebase-enabled builds retain sign-in, with a guest option. |
| Scan measured one device/proxy download and scaled that number by each node's TCP latency | Remove the estimation implementation. Start each actual candidate tunnel sequentially; run a complete HTTPS download through that tunnel; only keep a route strictly above 1 Mbps. No direct-internet fallback. |
| Successful native startup was treated as a verified connection | During automatic scanning, delay CONNECTED until the speed probe passes. Bound OpenVPN readiness waiting and ignore stale callbacks by engine generation. |
| Same names/shared hosts caused distinct routes/accounts to be dropped | Use the complete transport/security/credential identity for deduplication and stable IDs; names only group candidate routes. Preserve URL-decoded WebSocket paths. |
| Latest raw diagnostic report included Firebase email/UID, host and logs | Remove that report/UI/model and use the single allowlisted `ReportPayload` path, off by default, with separate Drive consent. Do not import or upload the legacy raw report file. Local clear also removes that legacy file. |
| Upload worker and UI existed but were not wired into the app and lacked dependencies | Initialize reports, record connection/scan outcomes, expose controls, restore WorkManager/Google Auth dependencies, serialize uploads, retry online using backoff and periodic fallback. Atomic local report commits avoid partially written queue files. |
| Fixed certificate fingerprints implied verified OAuth binding for arbitrary builds | Read optional expected fingerprints from build environment; absence is not a verification success. Certificate comparison is not proof that Google Cloud is configured. |
| Signing could use a base64 private-key file in the source tree | Restore permanent signing only from Actions secrets. Ignore private-key/config files. Development artifacts warn about ephemeral signing. Tagged releases fail without permanent signing; automatic release publishing requires it. |
| Build workflow did not execute tests | Run debug unit tests before release assembly and capture compiler/test failures as annotations. |

## Automated coverage

- Route identity, same-name grouping, distinct transports/credentials/path/SNI, strict 1 Mbps boundaries.
- Sequential retry, first verified winner, slow-route rejection, finite exhaustion, cancellation propagation.
- Subscription parsing preserves routes and decodes encoded WebSocket paths once; HTML errors are not subscriptions.
- Report schema excludes credentials, server names/hosts/paths, account identifiers, arbitrary logs and tokens; salted fingerprints differ between routes/installations.

See the latest branch CI for the actual test/build result; these checks do not prove physical-device or carrier connectivity.

## Owner-controlled setup that cannot be completed from the source repository

1. Google Cloud: enable Drive API, configure OAuth consent/test users and register Android package `com.aistudio.h25vpn.bxtkp` with the real permanent APK certificate SHA-1. Authorize on the phone. Firebase sign-in is separate and optional.
2. Actions secrets for permanent signing: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Provision via GitHub's secure settings, not chat or tracked files. The agent's GitHub integration does not have permission to enumerate repository secrets, so their current presence is not claimed verified.
3. Optional Firebase: store the actual JSON text in `GOOGLE_SERVICES_JSON` Actions secret, or place the configuration locally in `app/google-services.json`. CI does not print its content. No file means local-only Firebase mode, not a fake configured app. Drive OAuth can be configured independently of Firebase.
4. Optional certificate comparison fields: build environment `OAUTH_SIGNING_SHA1` and `OAUTH_SIGNING_SHA256` (public fingerprints).
5. Device acceptance: bad route then good route, insufficient bandwidth, cancel/reconnect, SSH/OpenVPN lifecycle, external VPN included/excluded-app behavior, offline report queue then online upload, token/consent revocation, and checking the private Drive folder from the connected Arena account.

No VPN protocol/config can guarantee free carrier data, revive an expired account, or extract another app's private tunnel configuration. Public subscription availability and open TCP ports are not proof of successful VPN authentication.
