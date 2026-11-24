package com.example.h25vpn.data

object TweaksData {
    val defaultTweaks = listOf(
        CarrierTweak(
            id = "ais-play-free",
            name = "AIS Play (โปร 63บ./เดือน + อีซี่ฟรีเน็ต)",
            carrier = "AIS",
            description = "ทะลุโปร AIS Play *777*885# เน็ตไม่อั้นด้วย SNI aisplay.ais.co.th",
            promoHint = "ต้องสมัครอีซี่ฟรีเน็ต *777 แล้วสมัคร AIS Play *777*885# (63บ.)",
            ussdCode = "*777*885#",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "aisplay.ais.co.th",
            wsHostHeader = "aisplay.ais.co.th",
            customPayload = "GET / HTTP/1.1[crlf]Host: aisplay.ais.co.th[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "ais-speedtest-nopro",
            name = "AIS Speedtest / Free Host (NoPro/5G)",
            carrier = "AIS",
            description = "โหมด WS/HTTP สำหรับซิม AIS เช็คความเร็วและทะลุแบนด์วิดท์",
            promoHint = "เหมาะกับซิมเติมเงิน AIS / อีซี่ฟรีเน็ต",
            ussdCode = "*777",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "ais.speedtest.net",
            wsHostHeader = "ais.speedtest.net",
            customPayload = "GET / HTTP/1.1[crlf]Host: ais.speedtest.net[crlf]X-Online-Host: ais.speedtest.net[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "ais-learndi",
            name = "AIS LearnDi / การศึกษา & Disney+",
            carrier = "AIS",
            description = "SSL/TLS SNI สำหรับแพ็กเกจเรียนออนไลน์และสตรีมมิ่ง AIS",
            promoHint = "ใช้ได้กับแพ็กเสริม AIS Play / Disney+ / Edu",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "play.ais.co.th",
            wsHostHeader = "play.ais.co.th"
        ),
        CarrierTweak(
            id = "true-vdo-edu",
            name = "True เรียนฟรี / VDO (*900*1347#)",
            carrier = "TrueMove",
            description = "ทะลุโปรเรียนฟรี/VDO 53บ./30วัน วิ่งเต็มสปีด 10Mbps+",
            promoHint = "สมัครกด *900*1347# (53บ./30วัน)",
            ussdCode = "*900*1347#",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "www.trueplookpanya.com",
            wsHostHeader = "www.trueplookpanya.com",
            customPayload = "GET / HTTP/1.1[crlf]Host: www.trueplookpanya.com[crlf]X-Online-Host: dltv.ac.th[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "true-id",
            name = "TrueMove H ทรูไอดี (TrueID Unlimited)",
            carrier = "TrueMove",
            description = "เจาะแบนด์วิดท์ดูหนัง TrueID ฟรีเป็นเน็ตทั่วไป",
            promoHint = "ใช้กับซิมที่มีแพ็กเกจดู TrueID ฟรี",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "trueid.net",
            wsHostHeader = "cms.dmpcdn.com",
            customPayload = "GET / HTTP/1.1[crlf]Host: cms.dmpcdn.com[crlf]X-Online-Host: trueid.net[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "true-viber",
            name = "TrueMove โปร Viber (*900*3809#)",
            carrier = "TrueMove",
            description = "ทะลุโปร Viber 6บ./21บ./53บ. ไม่ลดสปีด",
            promoHint = "1วัน *900*3912# | 7วัน *900*3913# | 30วัน *900*3809#",
            ussdCode = "*900*3809#",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "www.viber.com",
            wsHostHeader = "www.viber.com",
            customPayload = "CONNECT [host_port] [protocol][crlf]Host: www.viber.com[crlf]X-Online-Host: www.viber.com[crlf]Connection: Keep-Alive[crlf][crlf]"
        ),
        CarrierTweak(
            id = "true-twitter",
            name = "TrueMove โปร Twitter / X (*900*3509#)",
            carrier = "TrueMove",
            description = "ทะลุโปร Twitter 6บ./21บ./53บ. ใช้งานได้ทุกแอป",
            promoHint = "1วัน *900*3507# | 7วัน *900*3508# | 30วัน *900*3509#",
            ussdCode = "*900*3509#",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "abs.twimg.com",
            wsHostHeader = "twitter.com",
            customPayload = "CONNECT [host_port] [protocol][crlf]Host: abs.twimg.com[crlf]X-Online-Host: twitter.com[crlf]Connection: Keep-Alive[crlf][crlf]"
        ),
        CarrierTweak(
            id = "true-nopro",
            name = "True โนโปร (ซิมเปล่า ฟรี 1GB/วัน)",
            carrier = "TrueMove",
            description = "ไม่ต้องสมัครโปรเสริม ซิมทรูเติมเงินไม่มีเงินในซิม",
            promoHint = "ซิมเปล่าห้ามมีเงินรั่ว (โควตาประมาณ 1GB/วัน)",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "iservice.truecorp.co.th",
            wsHostHeader = "iservice.truecorp.co.th",
            customPayload = "GET / HTTP/1.1[crlf]Host: iservice.truecorp.co.th[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "dtac-gaming",
            name = "DTAC โปรตามใจ Gaming (40บ./30วัน)",
            carrier = "DTAC",
            description = "ทะลุโปรเกมมิ่งดีแทค (RoV/FreeFire/PUBG) เล่นเน็ตได้ทุกเว็บ",
            promoHint = "สมัครผ่านแอป dtac: โปรตามใจ Gaming 40บ./30วัน หรือ 61บ./90วัน",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "dl.kgvn.garenanow.com",
            wsHostHeader = "cdn.garenanow.com",
            customPayload = "GET / HTTP/1.1[crlf]Host: dl.kgvn.garenanow.com[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "dtac-social",
            name = "DTAC Social / LINE / โซเชียลไม่อั้น",
            carrier = "DTAC",
            description = "SSL SNI ทะลุโปรโซเชียล LINE, Facebook, WhatsApp ค่ายดีแทค",
            promoHint = "ใช้กับซิมดีแทคที่มีโปรโซเชียลไม่อั้น",
            protocol = TunnelProtocol.SSH_SSL,
            bugHostSni = "obs.line-scdn.net",
            wsHostHeader = "line.me",
            customPayload = "CONNECT [host_port] [protocol][crlf]Host: obs.line-scdn.net[crlf]X-Online-Host: line.me[crlf]Connection: Keep-Alive[crlf][crlf]"
        ),
        CarrierTweak(
            id = "dtac-nopro",
            name = "DTAC Nopro (ซิมเปล่า ไม่ต้องมีโปร)",
            carrier = "DTAC",
            description = "ซิมดีแทคไม่ต้องสมัครโปร (ห้ามมีเงินในซิมป้องกันกินเงิน)",
            promoHint = "ใช้ซิมเติมเงินดีแทคเงิน 0 บาท",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "www.dtac.co.th",
            wsHostHeader = "app.dtac.co.th",
            customPayload = "GET / HTTP/1.1[crlf]Host: app.dtac.co.th[crlf]X-Online-Host: www.dtac.co.th[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "my-nt-nopro",
            name = "My by NT Nopro (ซิมมาย ไม่ต้องสมัครโปร)",
            carrier = "My NT",
            description = "ซิม My by NT เพียวๆ ไม่ต้องสมัครโปรเสริมใดๆ (มีโปรอาจไม่ติด)",
            promoHint = "ใช้ได้เฉพาะซิม My by NT แบบไม่มีโปรเสริม",
            protocol = TunnelProtocol.WEBSOCKET,
            bugHostSni = "www.mybynt.com",
            wsHostHeader = "www.mybynt.com",
            customPayload = "GET / HTTP/1.1[crlf]Host: www.mybynt.com[crlf]X-Online-Host: www.ntplc.co.th[crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]"
        ),
        CarrierTweak(
            id = "direct-no-tweak",
            name = "Direct / ใช้ค่าตรงตามเซิร์ฟเวอร์ (Wi-Fi / เน็ตปกติ)",
            carrier = "Direct",
            description = "ไม่แก้ไข SNI/Host ของเซิร์ฟเวอร์ เหมาะกับ Wi-Fi หรือเน็ตที่มีดาต้าปกติ",
            promoHint = "ใช้เชื่อมต่อ V2Ray Reality / OpenVPN ตรงๆ เพื่อมุด IP และความปลอดภัย",
            protocol = TunnelProtocol.V2RAY_VLESS,
            bugHostSni = "",
            isDirectNoTweak = true
        )
    )
}
