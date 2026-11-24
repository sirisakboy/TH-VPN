# Proguard rules for TH-VPN
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.annotation.**
-dontwarn org.spongycastle.**
-dontwarn net.i2p.crypto.**
-dontwarn com.trilead.ssh2.**

-keep class net.openvpn.openvpn.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.trilead.ssh2.** { *; }
