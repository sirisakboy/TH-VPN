# Private connection reports and Google Drive

## What is implemented

Settings → รายงานการเชื่อมต่อ: explicit opt-in for local diagnostics (off by default), separate Google authorization for automatic upload, queue preview, stop-upload control, and local queue deletion. A private `TH-VPN Connection Reports` folder is created in the Google account selected on the phone, using only `drive.file` scope. Nothing is made public. The Arena Drive connector is for reading reports later; its credentials are NOT copied into the APK and do not authorize the Android app.

Each report contains a random report ID, UTC timestamp, Android API, event, threshold, measured Mbps when available, and allowlisted tunnel metadata (protocol, port, network, TLS mode, TCP ping). An installation-salted SHA-256 route fingerprint lets a developer correlate attempts without receiving credentials. It cannot reconstruct a working tunnel. No raw logs, server name, host/IP, path, SNI, UUID, VPN password, raw configuration URI, Google account address, browsing history, packet captures or OAuth tokens are included. Routes cannot be imported from these reports; if a specific working config is required, the owner must separately export/share it from its source app.

Immutable JSON files are queued in app-private no-backup storage, capped at 200 pending reports. Oldest pending reports are evicted at the cap. Successful uploads are removed locally. WorkManager schedules connected-network uploads with backoff and a 15-minute periodic fallback; Android can defer execution. Internet validation is ultimately the success of the HTTPS Drive request, not merely network presence. Turning off capture also disables uploads. Turning off uploads cancels queued work, but cannot retract an already in-flight request or files already stored in Drive. Local deletion does not delete cloud reports. Delete cloud files in Drive. The folder is private until the user changes its sharing settings.

## Required Google Cloud setup — not configured by this repository

1. Use a Google Cloud project you control. Enable **Google Drive API**.
2. Configure the OAuth consent screen, request `https://www.googleapis.com/auth/drive.file`, and add test users while the app is in testing.
3. Register an **Android OAuth client** for package `com.aistudio.h25vpn.bxtkp` with the SHA-1 of the actual APK signing certificate. No client secret belongs in an Android app or in chat.
4. **Use a stable signing key.** CI now restores permanent signing only from `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` Actions secrets. Without them, branch builds use an ephemeral development key, tagged releases fail, and automatic publication is disabled. A regenerated certificate changes SHA-1 and breaks OAuth bindings across builds. The owner must provision permanent signing before relying on OAuth across releases. Never commit signing keys.
5. Install the correctly signed APK. Enable local reports, then tap Connect Google Drive and authorize the account on the phone. Authentication failure (often developer error/code 10 for mismatched package/SHA-1) does not lose pending reports.
6. Connect/scan, then inspect `TH-VPN Connection Reports` in Drive. Tell the assistant the folder name in chat; with the Drive connector enabled it can search/read the uploaded JSON files. This is not unattended monitoring by the assistant.

The app uses Play Services sign-in/GoogleAuthUtil (legacy APIs) for Android OAuth; devices without Google Play Services need a future alternative authorization flow. Tokens exist only transiently in memory; Play Services manages account authorization. Extra authorization prompts are handled interactively. A backend, service-account key, public upload endpoint, or hardcoded user token is not used.

## Testing an existing VPN from another app

Stop TH-VPN, start the other VPN app, return to Settings and tap the existing-VPN test. This does NOT start Android VpnService. It explicitly chooses an accessible `TRANSPORT_VPN` network and requests the same 1 MiB HTTPS speed sample used by route scanning. It does not fall back to direct mobile/Wi-Fi internet. If Android/the other VPN excludes TH-VPN or does not expose an accessible VPN network, the test can fail. A failed test is not proof the other VPN cannot work for its own included apps.

External-probe reports deliberately have no route fingerprint/protocol because those internals are unknown. This cannot read another app's private config, decrypt its tunnel, discover its credentials, prove a free carrier entitlement, or run a second VPN simultaneously. Endpoint blocking can also cause a usable VPN to fail the test.

## Verification

CI runs existing route tests plus report-schema privacy tests and builds the release APK. Phone/OAuth integration is still required: deny sign-in, enable/disable consent, offline queue then reconnect, kill/relaunch app, revoked scope/expired token, upload retry, full queue eviction, remote file visibility via Arena, and testing with a separate VPN app. Cloud uploads have not been verified until those tests are run with the owner's OAuth project and signing certificate.

Firebase configuration is optional for the local VPN. Without `app/google-services.json`, the Google Services plugin is not applied and the app does not gate access on Firebase sign-in. Drive authorization remains a separate flow requiring real Android OAuth setup. See `review-completion.md` for the current review and setup checklist.
