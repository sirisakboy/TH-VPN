# 🛡️ TH-VPN — ฟรีเน็ต & ทะลุโปรเสริมค่ายมือถือไทย (Real Multi-Engine Edition)

**TH-VPN** ได้รับการยกเครื่องใหม่ทั้งหมดจากแอปตัวอย่าง (Mockup) ให้เป็นแอป VPN ใช้งานจริง 100% ที่เน้นสำหรับ **สายฟรีอินเทอร์เน็ต (NoPro / ซิมเปล่า)** และ **สายทะลุโปรเสริมค่ายมือถือในประเทศไทย (AIS, TrueMove H, DTAC, My by NT)** พร้อมระบบดึงเซิร์ฟเวอร์ฟรีอัตโนมัติในตัวโดยที่ผู้ใช้ไม่จำเป็นต้องมี VPS ส่วนตัว

---

## ✨ จุดเด่นและระบบที่อัปเกรดให้ใช้งานได้จริง

### 1. 🚀 รองรับโปรโตคอลแห่งอนาคต (Real Multi-Protocol Engine)
- **Xray / V2Ray Core (`libv2ray.aar` + `libgojni.so`)**: รองรับ `VLESS`, `VMess`, `Trojan`, `Shadowsocks`, `WebSocket (WS)`, `gRPC`, `TLS SNI` และ `REALITY` (uTLS Chrome Fingerprint) เต็มรูปแบบ
- **Auto Bug-Host / SNI Injector**: เมื่อผู้ใช้เลือกโปรไฟล์ค่ายมือถือไทย ระบบจะเขียนทับ `Host` Header (WS/HTTP) และ `TLS serverName (SNI)` เข้าไปในโหนดเซิร์ฟเวอร์อัตโนมัติเพื่อเจาะทะลุแพ็กเกจซิม
- **SSH2 + SSL/TLS SNI & HTTP Payload Injector (`SshInjectorEngine`)**: รองรับคำสั่ง Payload เต็มรูปแบบ (`[host_port]`, `[crlf]`, `[sni]`, `Upgrade: websocket`) พร้อม `DynamicPortForwarder` SOCKS5
- **OpenVPN 3 Native C++ (`libovpncli.so` + `OpenVpnEngine`)**: เชื่อมต่อไฟล์ `.ovpn` (TCP/UDP) ของจริงผ่าน JNI SWIG (`ClientAPI_OpenVPNClient`)
- **Native `badvpn-tun2socks` + `pdnsd`**: ส่ง File Descriptor ของ Android `VpnService` เข้าสู่ `tun2socks` ผ่าน Unix Domain Socket (`SCM_RIGHTS`) ของจริง

### 2. 🌐 ไม่ต้องมีเซิร์ฟเวอร์ส่วนตัว — ดึงโหนดฟรีอัตโนมัติ (Cloud Free Auto-Sync)
- มาพร้อมโหนดเริ่มต้น **Thailand Free-Net WS (Port 80)** และปุ่ม **ดึงโหนดฟรี (Cloud Sync)** ที่ดึงเซิร์ฟเวอร์ฟรีอัปเดตสดจาก:
  - Public V2Ray Feeds (โซน **Thailand, Singapore, Japan, Hong Kong**)
  - **VPNGate** OpenVPN Public Relay Servers
- รองรับการวางลิงก์ `vless://`, `vmess://`, `trojan://`, `ss://`, `ssh://`, ลิงก์ **Subscription URL** หรือนำเข้าไฟล์ `.ovpn` / `.txt` ได้ทันที

### 3. 📱 ศูนย์รวมโปรเสริม & เน็ตฟรีค่ายมือถือไทย (`connect-vpn.net/link/` Hub)
รวมคู่มือสมัครโปรซิมสุดคุ้ม กดโทรออกรหัส **USSD** ได้จากในแอป และกดปุ่ม **"ใช้โปรไฟล์นี้"** เพื่อตั้งค่า Bug Host ทันที:
- **AIS**: โปร AIS Play 63 บ./เดือน (`*777*885#`) คู่โปรหลักอีซี่ฟรีเน็ต (`*777`), AIS Speedtest Free Host, AIS LearnDi
- **TrueMove H**: โปรเรียนฟรี / VDO 10Mbps (`*900*1347#`), โปร Viber (`*900*3809#`), โปร Twitter/X (`*900*3509#`), TrueID, และ **True โนโปร** (ซิมเปล่าฟรี 1GB/วัน)
- **DTAC**: โปรตามใจ Gaming (40 บ./30 วัน หรือ 61 บ./90 วัน), DTAC Social/LINE, และ **DTAC Nopro** (ซิมเปล่าเงิน 0 บาท)
- **My by NT**: **My by NT Nopro** (ซิมมายเพียวๆ ไม่ต้องสมัครโปรเสริม)
- **ตัวเช็ค Bug Host ในตัว**: ทดสอบโดเมน SNI บนซิมมือถือจริงว่าเปิดพอร์ต 80 / 443 และตอบสนอง HTTP หรือไม่
- **คลังลิงก์ไฟล์ค่าย**: เชื่อมต่อลิงก์ดาวน์โหลดไฟล์ `.ehi` / `.npvs` จาก `http://connect-vpn.net/link/` เผื่อต้องการเปิดคู่กับแอปภายนอก

### 4. 📊 ระบบวัดผลและเครื่องมือเครือข่ายของจริง (Zero Mockups)
- **Real Traffic Telemetry**: อ่านความเร็ว Download/Upload จาก `Xray queryStats` และ `TrafficStats.getUidRxBytes/TxBytes` ของจริง
- **Real TCP Ping & Real Cloudflare SpeedTest**: วัดค่าปิงผ่าน TCP Socket จริง และทดสอบสปีดดาวน์โหลด/อัปโหลดผ่าน Cloudflare Edge จริง
- **Real Split Tunneling**: ดึงรายชื่อแอปจริงจากเครื่อง (`PackageManager`) เพื่อให้แอปธนาคาร (K PLUS, SCB, เป๋าตัง ฯลฯ) ข้ามการมุด VPN ได้จริง (`addDisallowedApplication`)
- **Real Wi-Fi Hotspot Proxy Share**: เปิด HTTP / HTTPS `CONNECT` Proxy Server จริงบนพอร์ต `8080` เพื่อแชร์เน็ต VPN ให้เครื่องอื่นผ่านฮอตสปอตโดยไม่ต้องรูท

---

## 📦 วิธีดาวน์โหลดไฟล์ APK ไปติดตั้ง
ทุกครั้งที่มีการ Push โค้ด GitHub Actions (`.github/workflows/build-apk.yml`) จะทำการคอมไพล์ไฟล์ `TH-VPN-*.apk` ให้โดยอัตโนมัติ สามารถเข้าไปดาวน์โหลดได้ที่แท็บ **Actions** หรือ **Releases** ของ Repository นี้
