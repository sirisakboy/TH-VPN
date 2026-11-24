package com.example.h25vpn.data

object PromoHubData {
    const val PORTAL_HOME_URL = "http://connect-vpn.net/link/"
    const val PORTAL_SIM_PRO_URL = "http://connect-vpn.net/link/SIM-PRO.html"
    const val PORTAL_APPS_URL = "http://connect-vpn.net/link/APPS-VPN.html"
    const val PORTAL_WIFI_SHARE_URL = "http://connect-vpn.net/link/wifi/index.html"

    val simPromos = listOf(
        SimPromoItem(
            id = "promo-ais-play",
            carrier = "AIS",
            title = "AIS Play + อีซี่ฟรีเน็ต (เน็ตไม่อั้น 30 วัน)",
            priceText = "63 บ. / เดือน (รวมภาษี)",
            speedText = "ความเร็วตามพื้นที่ 4G/5G ไม่จำกัดปริมาณ",
            ussdCode = "*777*885#",
            steps = listOf(
                "1. เปลี่ยนโปรหลักเป็น 'อีซี่ฟรีเน็ต' โทร *777 กด 1 -> กด 7 -> กด 1 (ซิมใหม่เปลี่ยนฟรี)",
                "2. เติมเงิน 70 บาท เพื่อเปิดโปรอีซี่ฟรีเน็ต (กันรั่วดูดเงิน)",
                "3. กดสมัครแพ็กเกจเสริม AIS Play โทร *777*885# (63 บาท รวมภาษี)",
                "4. เลือกโปรไฟล์ 'AIS Play' ในแอป TH-VPN แล้วกดเชื่อมต่อทันที!"
            ),
            note = "หากเป็นซิมเก่าอาจมีค่าเปลี่ยนโปรหลักประมาณ 30 บาท",
            tweakIdToApply = "ais-play-free",
            externalUrl = "http://connect-vpn.net/link/VPN/AIS.html"
        ),
        SimPromoItem(
            id = "promo-true-edu",
            carrier = "TrueMove",
            title = "True เรียนฟรี / VDO (แรงสุด แนะนำ!)",
            priceText = "53 บ. / 30 วัน",
            speedText = "ความเร็วสูงสุดไม่เกิน 10 Mbps ไม่ลดสปีด",
            ussdCode = "*900*1347#",
            steps = listOf(
                "1. เติมเงินให้พอกับค่าแพ็กเกจ (53 บาท รวมภาษี)",
                "2. กดรหัส *900*1347# แล้วโทรออก รอรับ SMS ยืนยัน",
                "3. เลือกโปรไฟล์ 'True เรียนฟรี / VDO' ในแอป TH-VPN แล้วกดเชื่อมต่อ"
            ),
            note = "เป็นโปรยอดฮิตของสายฟรีเน็ตทรู ความเร็วเสถียรดู YouTube 1080p สบาย",
            tweakIdToApply = "true-vdo-edu",
            externalUrl = "http://connect-vpn.net/link/VPN/TRUE.html"
        ),
        SimPromoItem(
            id = "promo-true-viber",
            carrier = "TrueMove",
            title = "True โปร Viber ไม่ลดสปีด (1 / 7 / 30 วัน)",
            priceText = "6 บ. (1วัน) | 21 บ. (7วัน) | 53 บ. (30วัน)",
            speedText = "ความเร็วไม่เกิน 4 Mbps ไม่อั้น",
            ussdCode = "*900*3809#",
            steps = listOf(
                "• ราย 1 วัน (6 บ.): กด *900*3912#",
                "• ราย 7 วัน (21 บ.): กด *900*3913#",
                "• ราย 30 วัน (53 บ.): กด *900*3809#",
                "หลังได้ SMS ยืนยัน เลือกโปรไฟล์ 'TrueMove โปร Viber' ในแอปแล้วเชื่อมต่อ"
            ),
            tweakIdToApply = "true-viber",
            externalUrl = "http://connect-vpn.net/link/VPN/TRUE.html"
        ),
        SimPromoItem(
            id = "promo-true-twitter",
            carrier = "TrueMove",
            title = "True โปร Twitter / X ไม่อั้น (1 / 7 / 30 วัน)",
            priceText = "6 บ. (1วัน) | 21 บ. (7วัน) | 53 บ. (30วัน)",
            speedText = "ความเร็วไม่เกิน 4 Mbps ไม่อั้น",
            ussdCode = "*900*3509#",
            steps = listOf(
                "• ราย 1 วัน (6 บ.): กด *900*3507#",
                "• ราย 7 วัน (21 บ.): กด *900*3508#",
                "• ราย 30 วัน (53 บ.): กด *900*3509#",
                "หลังได้ SMS ยืนยัน เลือกโปรไฟล์ 'TrueMove โปร Twitter' ในแอปแล้วเชื่อมต่อ"
            ),
            tweakIdToApply = "true-twitter",
            externalUrl = "http://connect-vpn.net/link/VPN/TRUE.html"
        ),
        SimPromoItem(
            id = "promo-true-nopro",
            carrier = "TrueMove",
            title = "True โนโปร (ซิมเปล่า ฟรีวันละ 1 GB)",
            priceText = "ฟรี 0 บาท (ไม่ต้องสมัครโปร)",
            speedText = "ฟรีโควตาประมาณ 1 GB / วัน",
            steps = listOf(
                "1. ใช้ซิมทรูเติมเงินที่มีวันใช้งานเหลือ แต่ห้ามมีเงินค้างในซิม (ป้องกันเน็ตรั่ว)",
                "2. เลือกโปรไฟล์ 'True โนโปร' ในแอป TH-VPN แล้วกดเชื่อมต่อ"
            ),
            tweakIdToApply = "true-nopro",
            externalUrl = "http://connect-vpn.net/link/VPN/TRUE.html"
        ),
        SimPromoItem(
            id = "promo-dtac-gaming",
            carrier = "DTAC",
            title = "DTAC โปรตามใจ Gaming (ไม่อั้น 30 / 90 วัน)",
            priceText = "40 บ. (30วัน) | 61 บ. (90วัน)",
            speedText = "ความเร็วเต็มสปีดสายเกมมิ่ง ไม่จำกัดดาต้า",
            steps = listOf(
                "1. เปิดแอป dtac บนมือถือ -> ไปที่เมนู 'โปรตามใจ'",
                "2. เลือกหมวดหมู่ Gaming (FreeFire / RoV / PUBG)",
                "3. เลือกระยะเวลา 30 วัน (40 บาท) หรือ 90 วัน (61 บาท) แล้วกดยืนยัน",
                "4. เลือกโปรไฟล์ 'DTAC โปรตามใจ Gaming' ในแอป TH-VPN แล้วกดเชื่อมต่อ"
            ),
            note = "แนะนำให้ปิดเน็ตจากระบบ *104*72# ตอนไม่ได้ต่อ VPN หากมีเงินเหลือในซิม",
            tweakIdToApply = "dtac-gaming",
            externalUrl = "http://connect-vpn.net/link/VPN/DTAC.html"
        ),
        SimPromoItem(
            id = "promo-dtac-nopro",
            carrier = "DTAC",
            title = "DTAC Nopro (ซิมดีแทคเปล่า ไม่ต้องมีโปร)",
            priceText = "ฟรี 0 บาท (เงินในซิม 0 บาท)",
            speedText = "เชื่อมต่อฟรีสำหรับซิมไม่มีโปร",
            steps = listOf(
                "1. ใช้ซิมดีแทคเติมเงินที่ไม่มีเงินในซิม (0 บาท)",
                "2. เลือกโปรไฟล์ 'DTAC Nopro' ในแอป TH-VPN แล้วเชื่อมต่อกับโหนด WS พอร์ต 80/443"
            ),
            tweakIdToApply = "dtac-nopro",
            externalUrl = "http://connect-vpn.net/link/VPN/DTAC.html"
        ),
        SimPromoItem(
            id = "promo-my-nt",
            carrier = "My NT",
            title = "My by NT Nopro (ซิมมาย กสท./NT เพียวๆ)",
            priceText = "ฟรี 0 บาท (ห้ามมีโปรเสริม)",
            speedText = "เชื่อมต่อฟรีเฉพาะซิม My by NT",
            steps = listOf(
                "1. ใช้ซิม My by NT ที่มีวันใช้งานและไม่มีแพ็กเกจเน็ตเสริม (มีโปรเสริมอาจจะต่อไม่ติด)",
                "2. เลือกโปรไฟล์ 'My by NT Nopro' ในแอป TH-VPN แล้วกดเชื่อมต่อ"
            ),
            tweakIdToApply = "my-nt-nopro",
            externalUrl = "http://connect-vpn.net/link/VPN/MY.html"
        )
    )

    val carrierConfigFiles = listOf(
        CarrierConfigLink(
            id = "cfg-ais-play",
            carrier = "AIS",
            title = "AIS Play (NPV Tunnel)",
            subtitle = "ไฟล์ .npvs จาก connect-vpn.net/link/VPN/AIS.html",
            appRequired = "NapsternetV / NPV Tunnel",
            platform = "Android & iOS",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1jxkqh7PtwyOZ_T83cLC5kYUSirSAwey9",
            pageUrl = "http://connect-vpn.net/link/VPN/AIS.html",
            matchingTweakId = "ais-play-free"
        ),
        CarrierConfigLink(
            id = "cfg-dtac-gaming",
            carrier = "DTAC",
            title = "DTAC Gaming (Android .ehi)",
            subtitle = "ไฟล์ .ehi สำหรับโปรเกมมิ่งดีแทค",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=17mlQRFDUF7em7bUxo6OuGzuql488fmmb",
            pageUrl = "http://connect-vpn.net/link/VPN/DTAC.html",
            matchingTweakId = "dtac-gaming"
        ),
        CarrierConfigLink(
            id = "cfg-dtac-nopro",
            carrier = "DTAC",
            title = "DTAC Nopro (.npvs)",
            subtitle = "ไฟล์ .npvs สำหรับซิมดีแทคโนโปร",
            appRequired = "NPV Tunnel",
            platform = "Android & iOS",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1PehM1_DGvONSuzFpX_Tt8_ohjemxIlRX",
            pageUrl = "http://connect-vpn.net/link/VPN/IOS.html",
            matchingTweakId = "dtac-nopro"
        ),
        CarrierConfigLink(
            id = "cfg-true-vdo",
            carrier = "TrueMove",
            title = "True VDO / เรียนฟรี (.ehi)",
            subtitle = "ไฟล์ .ehi สำหรับโปร *900*1347#",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=108UjV0MS-MjY_RJ2K7n_FS_kpU-KdoJi",
            pageUrl = "http://connect-vpn.net/link/VPN/TRUE.html",
            matchingTweakId = "true-vdo-edu"
        ),
        CarrierConfigLink(
            id = "cfg-true-viber",
            carrier = "TrueMove",
            title = "True Viber (.ehi)",
            subtitle = "ไฟล์ .ehi สำหรับโปร Viber *900*3809#",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1ahIYDuGcSmKNl0dZi4Fx7B37ZtSXkORF",
            pageUrl = "http://connect-vpn.net/link/VPN/TRUE.html",
            matchingTweakId = "true-viber"
        ),
        CarrierConfigLink(
            id = "cfg-true-id",
            carrier = "TrueMove",
            title = "TrueID (.ehi)",
            subtitle = "ไฟล์ .ehi สำหรับโปรทรูไอดี",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1FHUgYhkIX0o2XMB1ze3Xf-CALjnnf7qf",
            pageUrl = "http://connect-vpn.net/link/VPN/TRUE.html",
            matchingTweakId = "true-id"
        ),
        CarrierConfigLink(
            id = "cfg-true-twitter",
            carrier = "TrueMove",
            title = "True Twitter (.ehi)",
            subtitle = "ไฟล์ .ehi สำหรับโปร Twitter *900*3509#",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1cuFe1crj6s4-s1aShTeohP7NM2qO8SK8",
            pageUrl = "http://connect-vpn.net/link/VPN/TRUE.html",
            matchingTweakId = "true-twitter"
        ),
        CarrierConfigLink(
            id = "cfg-my-nt",
            carrier = "My NT",
            title = "My by NT Nopro (.ehi)",
            subtitle = "ไฟล์ .ehi สำหรับซิมมายไม่ต้องสมัครโปร",
            appRequired = "HTTP Injector",
            platform = "Android",
            downloadUrl = "https://drive.google.com/uc?export=download&id=1k5zx2c7HuKiWpyq4z_YgQuojMYvD0Ibd",
            pageUrl = "http://connect-vpn.net/link/VPN/MY.html",
            matchingTweakId = "my-nt-nopro"
        )
    )
}
