package com.el7ajb.carlauncher

import java.util.Locale

enum class Lang(val code: String, val rtl: Boolean, val label: String) {
    AR("ar", true, "العربية"),
    HE("he", true, "עברית"),
    EN("en", false, "English");

    val locale: Locale
        get() = when (this) {
            AR -> Locale.forLanguageTag("ar-u-nu-latn")
            HE -> Locale("iw")
            EN -> Locale.ENGLISH
        }
}

object S {
    // key -> (arabic, hebrew, english)
    private val t: Map<String, Array<String>> = mapOf(
        "chooseCar" to arrayOf("اختار سيارتك", "בחר את הרכב שלך", "Choose your car"),
        "chooseSub" to arrayOf("الواجهة كلها بتتظبط على سيارتك ولونها", "הממשק כולו מותאם לרכב ולצבע שלך", "The whole interface adapts to your car and its color"),
        "addCar" to arrayOf("+ أضف سيارة", "+ הוסף רכב", "+ Add car"),
        "start" to arrayOf("ابدأ", "התחל", "Start"),
        "nowPlaying" to arrayOf("بتسمع هلأ", "מתנגן עכשיו", "Now playing"),
        "nothingPlaying" to arrayOf("ولا إشي شغّال", "שום דבר לא מתנגן", "Nothing playing"),
        "tapMusic" to arrayOf("اكبس عشان تفتح الموسيقى", "הקש כדי לפתוח מוזיקה", "Tap to open music"),
        "navigation" to arrayOf("ملاحة", "ניווט", "Navigation"),
        "whereTo" to arrayOf("وين رايح؟", "לאן נוסעים?", "Where to?"),
        "apps" to arrayOf("التطبيقات", "אפליקציות", "Apps"),
        "settings" to arrayOf("الإعدادات", "הגדרות", "Settings"),
        "language" to arrayOf("اللغة", "שפה", "Language"),
        "carAndColor" to arrayOf("السيارة واللون", "רכב וצבע", "Car and color"),
        "change" to arrayOf("تغيير", "שינוי", "Change"),
        "notifAccess" to arrayOf("الموسيقى والإشعارات", "מוזיקה והתראות", "Music and notifications"),
        "notifAccessSub" to arrayOf("لازم تسمح للتطبيق يقرأ الإشعارات عشان تبين الأغنية وواتساب وWaze", "יש לאשר גישה להתראות כדי להציג שירים, וואטסאפ ו-Waze", "Allow notification access to show songs, WhatsApp and Waze"),
        "granted" to arrayOf("مفعّل", "פעיל", "Enabled"),
        "grant" to arrayOf("فعّل", "הפעל", "Enable"),
        "location" to arrayOf("السرعة من GPS", "מהירות מ-GPS", "GPS speed"),
        "locationSub" to arrayOf("بيعرض سرعة السيارة الحقيقية", "מציג את מהירות הרכב האמיתית", "Shows the real speed of the car"),
        "defaultLauncher" to arrayOf("الواجهة الأساسية", "מסך הבית הראשי", "Default home screen"),
        "defaultLauncherSub" to arrayOf("خلي Drive هو اللي يفتح لما تكبس زر البيت", "הגדר את Drive כמסך הבית", "Make Drive open when you press Home"),
        "open" to arrayOf("افتح", "פתח", "Open"),
        "androidSettings" to arrayOf("إعدادات أندرويد", "הגדרות אנדרואיד", "Android settings"),
        "carRemoved" to arrayOf("انحذفت السيارة", "הרכב נמחק", "Car removed"),
        "carAdded" to arrayOf("انضافت السيارة", "הרכב נוסף", "Car added"),
        "invalidPack" to arrayOf("الملف مش ملف سيارة صالح", "הקובץ אינו קובץ רכב תקין", "This is not a valid car file"),
        "kmh" to arrayOf("كم/س", "קמ\"ש", "KM/H"),
        "back" to arrayOf("رجوع", "חזרה", "Back"),
        "notInstalled" to arrayOf("التطبيق مش منزّل", "האפליקציה לא מותקנת", "App is not installed"),
        "white" to arrayOf("أبيض", "לבן", "White"),
        "black" to arrayOf("أسود", "שחור", "Black"),
        "graphite" to arrayOf("رمادي جرافيت", "אפור גרפיט", "Graphite grey"),
        "silver" to arrayOf("فضي", "כסוף", "Silver"),
        "red" to arrayOf("أحمر", "אדום", "Red"),
        "blue" to arrayOf("أزرق", "כחול", "Blue"),
        "loading" to arrayOf("لحظة...", "רגע...", "One moment..."),
        "version" to arrayOf("الإصدار", "גרסה", "Version"),
        "music" to arrayOf("موسيقى", "מוזיקה", "Music"),
        "phone" to arrayOf("هاتف", "טלפון", "Phone"),
        "maps" to arrayOf("ملاحة", "ניווט", "Navigate"),
        "background" to arrayOf("لون الخلفية", "צבע רקע", "Background color"),
        "backgroundSub" to arrayOf("الإضاءة حول السيارة وكل الشاشة", "התאורה סביב הרכב וכל המסך", "The light around the car and the whole screen"),
        "themeCar" to arrayOf("لون السيارة", "צבע הרכב", "Car color"),
        "themeMusic" to arrayOf("حسب الأغنية", "לפי השיר", "Follows the song"),
        "themeMidnight" to arrayOf("ليلي", "לילה", "Midnight"),
        "themeOcean" to arrayOf("محيط", "אוקיינוס", "Ocean"),
        "themeEmerald" to arrayOf("زمرّد", "אמרלד", "Emerald"),
        "themeCrimson" to arrayOf("أحمر ناري", "ארגמן", "Crimson"),
        "themeAmber" to arrayOf("كهرماني", "ענבר", "Amber"),
        "themeViolet" to arrayOf("بنفسجي", "סגול", "Violet"),
        "themeIce" to arrayOf("جليدي", "קרח", "Ice"),
        "yourName" to arrayOf("اسمك", "השם שלך", "Your name"),
        "yourNameSub" to arrayOf("بيطلع بالترحيب لما تشغّل السيارة", "מוצג בברכה כשמתניעים", "Shown in the welcome when you start the car"),
        "gMorning" to arrayOf("صباح الخير", "בוקר טוב", "Good morning"),
        "gNoon" to arrayOf("نهارك سعيد", "צהריים טובים", "Good afternoon"),
        "gEvening" to arrayOf("مساء الخير", "ערב טוב", "Good evening"),
        "gNight" to arrayOf("سهرة سعيدة", "לילה טוב", "Good night"),
        "t_phone" to arrayOf("الهاتف والبلوتوث", "טלפון ובלוטות'", "Phone & Bluetooth"),
        "t_btmusic" to arrayOf("موسيقى البلوتوث", "מוזיקת בלוטות'", "BT Music"),
        "t_radio" to arrayOf("راديو", "רדיו", "Radio"),
        "t_music" to arrayOf("موسيقى", "מוזיקה", "Music"),
        "t_video" to arrayOf("فيديو", "וידאו", "Video"),
        "t_nav" to arrayOf("ملاحة", "ניווט", "Navigation"),
        "t_youtube" to arrayOf("يوتيوب", "יוטיוב", "YouTube"),
        "t_zlink" to arrayOf("CarPlay / Auto", "CarPlay / Auto", "CarPlay / Auto"),
        "t_camera" to arrayOf("الكاميرا", "מצלמה", "Camera"),
        "t_carset" to arrayOf("إعدادات السيارة", "הגדרות רכב", "Car settings"),
        "t_dsp" to arrayOf("الصوت", "סאונד", "Sound"),
        "t_fan" to arrayOf("المكيف", "מזגן", "Climate"),
        "t_files" to arrayOf("الملفات", "קבצים", "Files"),
        "t_apps" to arrayOf("كل التطبيقات", "כל האפליקציות", "All apps"),
        "bluetooth" to arrayOf("البلوتوث والهاتف", "בלוטות' וטלפון", "Bluetooth and phone"),
        "bluetoothSub" to arrayOf("وصّل تلفونك بالشاشة", "חבר את הטלפון למסך", "Connect your phone to the screen"),
        "swipeHint" to arrayOf("اسحب لتشوف كل الاختصارات", "החלק לכל הקיצורים", "Swipe for all shortcuts"),
        "perf" to arrayOf("الأداء", "ביצועים", "Performance"),
        "perfSub" to arrayOf("إذا الشاشة بتعلّق اختار سلس", "אם המסך נתקע בחר חלק", "If the screen lags choose Smooth"),
        "perfAuto" to arrayOf("تلقائي", "אוטומטי", "Auto"),
        "perfLite" to arrayOf("سلس", "חלק", "Smooth"),
        "perfFull" to arrayOf("كل التأثيرات", "כל האפקטים", "All effects"),
        "autoOpen" to arrayOf("افتح Drive أول ما تضوي الشاشة", "פתח את Drive כשהמסך נדלק", "Open Drive when the screen turns on"),
        "autoOpenSub" to arrayOf("بيحتاج إذن العرض فوق التطبيقات", "דורש הרשאת הצגה מעל אפליקציות", "Needs the display over other apps permission"),
        "on" to arrayOf("شغّال", "פעיל", "On"),
        "off" to arrayOf("مطفي", "כבוי", "Off"),
        "guard" to arrayOf("افتح Drive دايمًا بدل الشاشة الأصلية", "פתח תמיד את Drive במקום המסך המקורי", "Always open Drive instead of the original screen"),
        "guardSub" to arrayOf("بإعدادات إمكانية الوصول (Accessibility) فعّل \"Drive\"", "בהגדרות נגישות (Accessibility) הפעל את \"Drive\"", "In Accessibility settings turn on \"Drive\""),
        "finishSetup" to arrayOf("خطوة أخيرة: فعّل هدول عشان Drive يفتح دايمًا وكل إشي يشتغل", "שלב אחרון: הפעל את אלה כדי ש-Drive ייפתח תמיד והכל יעבוד", "Last step: turn these on so Drive always opens and everything works"),
        "done" to arrayOf("خلصت", "סיימתי", "Done"),
        "trip" to arrayOf("الرحلة", "נסיעה", "Trip"),
        "km" to arrayOf("كم", "ק\"מ", "km"),
        "min" to arrayOf("د", "דק'", "min"),
        "hr" to arrayOf("س", "שע'", "h"),
        "top" to arrayOf("أعلى", "מקס'", "top"),
        "beat" to arrayOf("الإضاءة بترقص مع الموسيقى", "התאורה רוקדת עם המוזיקה", "Lights dance with the music"),
        "beatSub" to arrayOf("بيحتاج إذن الصوت عشان يسمع الإيقاع، وما بيسجّل ولا إشي", "דורש הרשאת אודיו כדי לשמוע את הקצב, לא מקליט כלום", "Needs audio permission to hear the beat; nothing is recorded"),
        "home" to arrayOf("الرئيسية", "בית", "Home"),
    )

    fun get(key: String, lang: Lang): String {
        val a = t[key] ?: return key
        return when (lang) {
            Lang.AR -> a[0]
            Lang.HE -> a[1]
            Lang.EN -> a[2]
        }
    }
}
