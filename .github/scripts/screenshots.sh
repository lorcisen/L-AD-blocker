#!/bin/bash
# Installs the APK on the emulator, drives the UI and saves screenshots to shots/.
set -x
PKG=se.lorcisen.adblocker
mkdir -p shots

shot() { sleep 2; adb exec-out screencap -p > "shots/$1.png"; }

# Taps the centre of the first view whose text or content-desc matches $1.
tap() {
  rm -f ui.xml
  for i in 1 2 3; do
    adb shell rm -f /sdcard/ui.xml
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1 && break
    sleep 2
  done
  [ -f ui.xml ] || { echo "TAP FAILED: $1"; return; }
  b=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1]
for n in ET.parse("ui.xml").iter("node"):
    if want in (n.get("text"), n.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  [ -n "$b" ] && adb shell input tap $b
}

adb install -r L-AD-blocker.apk
adb shell appops set $PKG ACTIVATE_VPN allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
# The emulator is in English; run the main flow with the app set to Swedish.
adb shell cmd locale set-app-locales $PKG --locales sv
adb shell am start -W -n $PKG/.MainActivity
sleep 5
shot 1-home-off

tap "Slå på eller av reklamblockering"
sleep 4
for d in googleads.g.doubleclick.net ads.unity3d.com www.google.com api.applovin.com www.svt.se \
         pagead2.googlesyndication.com graph.facebook.com app-measurement.com www.youtube.com; do
  adb shell ping -c 1 -W 1 $d >/dev/null 2>&1
done
shot 2-home-on
adb shell input swipe 540 1800 540 600 300
shot 3-home-scrolled

tap "Aktivitet"
shot 4-activity
tap "Blockerade"
shot 5-activity-blocked
tap "app-measurement.com"
shot 5b-allow-dialog
tap "Avbryt"

tap "Inställningar"
shot 6-settings
adb shell input swipe 540 1900 540 400 300
shot 7-settings-scrolled
adb shell input swipe 540 1900 540 400 300
adb shell input swipe 540 1900 540 400 300
shot 7b-settings-bottom
tap "YouTube, webbläsare och spel"
shot 8-help
tap "Klar"
tap "Tillåtna domäner"
shot 8b-edit-list
tap "Avbryt"

adb shell input keyevent KEYCODE_HOME
adb shell cmd statusbar expand-notifications
shot 9-notification
adb shell cmd statusbar collapse
adb shell am start -n $PKG/.MainActivity
tap "Slå på eller av reklamblockering"
shot 10-turned-off

tap "Inställningar"
adb shell input swipe 540 1900 540 400 300
adb shell input swipe 540 1900 540 400 300
tap "Appens språk"
shot 11-language-picker
tap "Español"
sleep 3
shot 12-settings-es
tap "Inicio"
shot 13-home-es
adb shell cmd locale set-app-locales $PKG --locales ja
adb shell am start -n $PKG/.MainActivity
shot 14-home-ja
adb shell cmd locale set-app-locales $PKG --locales ""
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity
sleep 4
shot 15-home-system-en

# Ad hiding: a stand-in feed app with the Facebook package name, and the accessibility service on.
adb install -r fakefeed.apk
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.AdHiderService
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell am start -W -n com.facebook.katana/.FeedActivity
sleep 3
shot 16-feed-top
adb shell input swipe 540 1700 540 1100 400
shot 17-feed-scrolled
adb shell input swipe 540 1700 540 700 400
shot 18-feed-scrolled-more
adb shell input keyevent KEYCODE_HOME
shot 19-launcher-after-feed
adb shell am start -n $PKG/.MainActivity
tap "Hem"
shot 20-home-apps-card

adb logcat -d | grep -E "AndroidRuntime|AdBlockVpn|Blocklists" > shots/logcat.txt
exit 0
