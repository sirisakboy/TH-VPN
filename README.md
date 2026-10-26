# H25 VPN (Android Edition)

A high-performance SSH, OpenVPN, and TLS/SSL tunnel client built with Kotlin and Jetpack Compose for Android. Rewritten from the original `sirisakboy/h25-vpn` repository to provide a modern, production-grade Android application adhering strictly to Material Design 3 and Android architecture guidelines.

## Features

- **VPN Connection & Tunnel Engine:**
  - One-tap Connect/Disconnect with dynamic status indicators (`CONNECTING`, `AUTHENTICATING`, `CONNECTED`, `DISCONNECTING`).
  - Android `VpnService` integration establishing a TUN interface with configurable MTU and custom DNS routing.
  - Live animated glowing power button with session countdown timer.
  - Interactive "Add Time" modal (+2h, +6h, +24h VIP pass).

- **Global High-Speed Servers:**
  - Multiple regions: Singapore, Thailand, Japan, Hong Kong, United States, Germany, United Kingdom, Netherlands, and Australia.
  - Real-time latency measurement with ping sorting and server load indicators.
  - Over 200 real country flag assets supported.

- **Carrier Tweaks & SNI Payloads:**
  - Pre-configured bypass payloads for major mobile carriers (AIS, DTAC, TrueMove H, Smart Giga, Globe TM, TNT TikTok, Direct SSL).
  - Built-in Custom SNI & HTTP Payload creator (custom bug host, proxy host/port, HTTP header injection).

- **Live Network Telemetry & Speed Test:**
  - Real-time Download and Upload speed gauges (KB/s and MB/s).
  - Total cumulative data transferred (sent & received).
  - Ping latency testing and IP address masking (Real IP vs VPN Virtual IP).
  - Interactive In-App Network Speed Test utility.

- **Hotspot Proxy Tethering ("Compartir Internet"):**
  - Integrated HTTP Proxy server (`192.168.43.1:8080`) to share the active VPN tunnel with laptops, PCs, iPhones, and gaming consoles connected via Android Portable Wi-Fi Hotspot.

- **Split Tunneling (Per-App Filter):**
  - Allows selecting specific apps (e.g., banking apps, local games) to bypass VPN routing while keeping everything else encrypted.

- **Real-Time Diagnostic Console:**
  - Color-coded live logs detailing SSH/TLS handshakes, AES-256-GCM cipher negotiation, routing configuration, and traffic statistics.
  - One-tap copy and clear options.

- **Settings & Security:**
  - Custom DNS selector (Cloudflare 1.1.1.1, Google 8.8.8.8, AdGuard, Quad9, or Custom).
  - Battery optimization bypass shortcut.
  - Zero-Logs privacy guarantee.

## Architecture

- **UI Framework:** 100% Jetpack Compose with Material 3.
- **Language:** Kotlin 2.1.10 with Coroutines and Flow.
- **Build System:** Gradle (Kotlin DSL), Android SDK 36, AGP 8.9.0.
- **Services:** Android `VpnService` (`H25VpnService`) & Background Local Proxy Service (`ProxyTetherService`).
