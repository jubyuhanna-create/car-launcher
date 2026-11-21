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
