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
if [ -n "$xy" ]; then y=${xy#* }; sh input tap 445 $((y - 95)); sleep 2; shot 2_color_black; sh input tap $xy; fi
sleep 6
log "  cars on screen: $(sh ls /data/data/$PKG/files/cars | tr '\n' ' ')"
log "  frames: $(sh ls /data/data/$PKG/files/cars/*/spin 2>/dev/null | wc -l)"
shot 3_after_start
sh input keyevent KEYCODE_HOME; sleep 6; shot 4_home
sh input tap 512 282; sleep 4; shot 5_showroom
sh input keyevent KEYCODE_BACK; sleep 2
c=$(sh logcat -d -b crash | grep -A15 FATAL | head -40); [ -n "$c" ] && { log "CRASH"; echo "$c" >> $R; } || log "  no crashes"
