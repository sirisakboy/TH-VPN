# Automatic profile route scan

## Behaviour

Enabled by default; Settings → ค้นหาเส้นทางอัตโนมัติ. The threshold is **strictly greater than 1 Mbps**, i.e. 125,000 bytes/s (not 1 MB/s).

On Connect, take a snapshot of the selected profile and all profiles with the same trimmed, case-insensitive display name. Keep the selected route first. Do not mix unrelated names or change the selected carrier tweak. Different protocols, paths, credentials, Host, SNI, Reality keys, ports and other transport settings remain distinct routes; exact duplicate routes are tried once.

For each candidate, tear down the previous engine, start the candidate, and fetch a 1 MiB HTTPS response through its local Xray HTTP proxy, SSH SOCKS proxy, or an explicitly selected Android VPN network for OpenVPN. Never retry the probe directly. Disable redirects/caching/compression; retain normal certificate validation. HTTP errors, wrong response size, short responses and timeouts fail verification. Measure monotonic elapsed time including HTTP setup. Only publish CONNECTED and persist the winning route after the complete sample exceeds the threshold.

The probe reads at most 1 MiB of response body per candidate (TLS/protocol overhead is additional). Connect timeout: 4 seconds; read timeout: 3 seconds; total read-loop deadline: 12 seconds (an in-flight blocking read can add up to the read timeout). OpenVPN startup waits at most 35 seconds. Underlying native/SSH engine startup has its own timeouts. Failure or insufficient speed advances to the next candidate; exhaustion ends in ERROR and asks for refreshed profiles. There is no endless retry loop. Disconnect cancels the scan and the active HTTP request. Scan progress, measured Mbps and final failure are in AUTO-ROUTE logs and the foreground notification.

Turning the setting off applies to the next Connect and restores the existing single-route behaviour. This is initial connection verification, not continuous background speed monitoring or a speed guarantee. A blocked Cloudflare speed endpoint can reject an otherwise usable route. Real carrier-SIM and physical-device tests are still required.

## Public subscriptions

`PublicConfigSources.kt` lists reviewed public GitHub feeds from SoliSpirit/v2ray-configs, SoliSpirit/SolVPN and ShatakVPN/ConfigForge-V2Ray, with jsDelivr fallback on fetch or parse failure. These are **general community VPN subscriptions**, not verified AIS/True/DTAC/NT promo accounts. ConfigForge regional files overlapped during inspection, so their paths are not treated as proof of physical server location. Repository/file availability does not prove a working VPN session or carrier entitlement.

Do not embed downloaded credentials in documentation or commit feed snapshots. Refresh fetches current upstream contents. Arbitrary locked `.ehi`/`.npvs` files cannot be parsed or renewed by this app; the legacy hub action now opens the publisher page instead of an old direct Drive download. Public VPN operators can observe connection metadata and unencrypted application traffic. A VPN does not itself supply mobile data or guarantee free access.

## Checks

`./gradlew testDebugUnitTest assembleDebug` (JDK 17 and Android SDK required).

Unit tests cover distinct routing/security identity, exact duplicate removal, same-name grouping across transports, unrelated-name exclusion and strict 1 Mbps boundaries. Device acceptance tests still needed: bad first route/good second route, all fail, slow sample, endpoint blocked, Disconnect mid-probe, reconnect during scan, OpenVPN callback timeout, SSH authentication failure, and the setting disabled.
