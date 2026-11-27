#!/bin/bash
# Customer build test: offline first start must show only the bundled car's colors and install one with a tap.
PKG=com.el7ajb.carlauncher; mkdir -p qa; R=qa/report.txt
log(){ echo "$*" | tee -a $R; }
sh(){ adb shell "$@" 2>/dev/null | tr -d '\r'; }
shot(){ adb exec-out screencap -p > qa/$1.png; log "  [shot] $1"; }
APK=$(ls WoWDRIVE-*.apk | head -1)
log "$APK $(stat -c %s "$APK") bytes"
adb root >/dev/null; sleep 4; adb wait-for-device
sh svc power stayon true; sh wm size 1024x600; sh wm density 160
sh svc wifi disable; sh svc data disable; log "internet off"
adb install -r -g "$APK" >/dev/null 2>&1 && log "installed"
sh cmd role add-role-holder android.app.role.HOME $PKG >/dev/null
sh input keyevent KEYCODE_HOME; sleep 9; shot 1_first_start
sh uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml qa/ui.xml >/dev/null 2>&1
log "  texts: $(grep -o 'text="[^"]*"' qa/ui.xml | sed 's/text=//' | tr '\n' ' ' | cut -c1-300)"
# pick the 2nd color dot (black) then Start
xy=$(python3 - <<'PY'
import re
s=open('qa/ui.xml').read()
nodes=re.findall(r'<node [^>]*>',s)
start=None
for n in nodes:
    if 'text="Start"' in n:
        b=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]+)"',n).group(1)))); start=((b[0]+b[2])//2,(b[1]+b[3])//2)
print(f"{start[0]} {start[1]}" if start else "")
PY
)
log "  Start at: $xy"
# color dots sit in one row above Start: tap the second one
if [ -n "$xy" ]; then sh input tap 418 469; sleep 2; shot 2_color_black; sh input tap $xy; fi
sleep 6
log "  cars on screen: $(sh ls /data/data/$PKG/files/cars | tr '\n' ' ')"
log "  frames: $(sh ls /data/data/$PKG/files/cars/*/spin 2>/dev/null | wc -l)"
shot 3_after_start
sh input keyevent KEYCODE_HOME; sleep 6; shot 4_home
sh input tap 512 282; sleep 4; shot 5_showroom
sh input keyevent KEYCODE_BACK; sleep 2
log "== change color later, still offline"
sh am start -f 0x10008000 -n $PKG/.PanelActivity --es screen picker >/dev/null; sleep 4; shot 6_picker
sh uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml qa/ui.xml >/dev/null 2>&1
b=$(grep -o 'text="Car colors"[^>]*bounds="[^"]*"' qa/ui.xml | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
set -- $b; [ -n "$4" ] && { sh input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); log "  tapped Car colors"; }
sleep 4; sh input tap 605 469; sleep 2; shot 7_red; sh input tap 512 543; sleep 6
log "  cars on screen now: $(sh ls /data/data/$PKG/files/cars | tr '\n' ' ')"
sh input keyevent KEYCODE_HOME; sleep 6; shot 8_home_red
log "== unit set to China time, car in Nazareth"
for k in 1 2 3; do adb emu geo fix 35.30 32.70 >/dev/null 2>&1; sleep 1; done
sh cmd alarm set-timezone Asia/Shanghai >/dev/null; sh service call alarm 3 s16 Asia/Shanghai >/dev/null
sleep 2; log "  system zone: $(sh getprop persist.sys.timezone), system time: $(sh date +%H:%M), Israel time: $(TZ=Asia/Jerusalem date +%H:%M)"
sh am force-stop $PKG; sh input keyevent KEYCODE_HOME; sleep 7; shot 9_home_china_zone
sh uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml qa/ui.xml >/dev/null 2>&1
log "  Drive clock shows: $(grep -o 'text="[0-9][0-9]:[0-9][0-9]"' qa/ui.xml | head -1)"
sh am start -f 0x10008000 -n $PKG/.PanelActivity --es screen settings >/dev/null; sleep 4; shot 10_settings_zone_warning
c=$(sh logcat -d -b crash | grep -A15 FATAL | head -40); [ -n "$c" ] && { log "CRASH"; echo "$c" >> $R; } || log "  no crashes"
