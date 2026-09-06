# Personal Video Downloader — Android
## وثيقة المتطلبات والتصميم المعماري (v1.0)

> **الحالة:** استخدام شخصي — لا يُنشر على Google Play
> **المنصة:** Android 8.0+ (API 26+)
> **التاريخ:** سبتمبر 2026

---

## 0. الخلاصة التنفيذية (اقرأ هذا أولًا)

**القرار المعماري الحاسم:** لا تكتب مستخرِجات (extractors) بنفسك لأي مصدر. المعركة مع YouTube وInstagram وTikTok خاسرة سلفًا لأن كل واحد منها يغيّر بنيته الداخلية شهريًا. الطريقة الوحيدة الصامدة هي تضمين **yt-dlp** داخل التطبيق كمحرّك تنفيذ، وجعل تطبيقك مجرد واجهة أنيقة فوقه.

**الحزمة الموصى بها:**

| الطبقة | الاختيار | السبب |
|---|---|---|
| المحرّك | `yt-dlp` + `ffmpeg` مضمّنان عبر مكتبة **youtubedl-android** (`io.github.junkfood02.youtubedl-android`, الإصدار **0.18.1**) | نفس المكتبة التي يعتمد عليها Seal وYTDLnis؛ مبنية على Kotlin ومنشورة على Maven Central |
| اللغة/الواجهة | **Kotlin + Jetpack Compose** (Material 3) | المكتبة الوحيدة الجادة للمحرّك مكتوبة بـ Kotlin، والواجهة بسيطة جدًا فلا يبرر Flutter عبء الجسر (platform channel) |
| المعمارية | MVVM + Repository + Coroutines/Flow | قياسي، مفهوم للوكلاء الذكية، سهل الاختبار |
| التحميل الخلفي | Foreground Service + Notification | التحميل قد يستغرق دقائق ويجب ألا يقتله النظام |
| الحفظ | MediaStore (`Downloads/VideoDownloader/`) | يعمل على Android 10+ بدون صلاحيات تخزين واسعة |
| التحديث الذاتي | `YoutubeDL.updateYoutubeDL()` عند تشغيل التطبيق | هذا هو ما يبقي التطبيق حيًا عندما يغيّر YouTube شيئًا |

**ملاحظة صريحة عن Flutter:** خبرتك الأقوى في Flutter، لكن هنا الخيار الصحيح هو Kotlin أصيل. كل ما ستفعله في Flutter هو كتابة جسر إلى نفس مكتبة Kotlin، فتربح لا شيء وتخسر تعقيدًا. إن أصررت على Flutter لاحقًا، القسم 9 يشرح كيف.

---

## 1. فكرة التطبيق

تطبيق Android شخصي لتحميل الوسائط من الروابط.

**رحلة المستخدم الأساسية:**

1. لصق رابط الفيديو (أو التقاطه تلقائيًا من الحافظة).
2. الضغط على **تحليل**.
3. التعرف على المصدر وعرض المعلومات (صورة مصغّرة، عنوان، مدة).
4. اختيار جودة الفيديو **أو** تحميل الصوت فقط MP3.
5. يبدأ التحميل في الخلفية مع إشعار يعرض التقدّم.
6. يُحفظ الملف في `Downloads/VideoDownloader/` ويظهر فورًا في معرض الملفات.

---

## 2. المتطلبات الوظيفية

### 2.1 إدخال الرابط
- `EditText` لرابط الفيديو مع دعم RTL.
- زر **لصق** يقرأ الحافظة.
- عند فتح التطبيق: إن كانت الحافظة تحتوي رابطًا صالحًا (http/https) يُعرض تلقائيًا في الحقل مع شريط صغير "تم اللصق من الحافظة".
- **مشاركة من تطبيقات أخرى:** يستقبل التطبيق `ACTION_SEND` بنوع `text/plain` — أي أن مشاركة رابط من YouTube أو TikTok مباشرة إلى التطبيق تعبّئ الحقل وتبدأ التحليل. هذه أهم ميزة من ناحية الاستخدام الفعلي؛ لصق الرابط يدويًا سيصبح الاستثناء لا القاعدة.

### 2.2 التحليل
- يستدعي `yt-dlp --dump-single-json <url>` ويحوّل الناتج إلى `MediaInfo`.
- يعرض: الصورة المصغّرة، العنوان، المدة، اسم المصدر، اسم القناة/الحساب.
- زمن الاستجابة المتوقع: 2–8 ثوانٍ. يجب عرض مؤشر تحميل وإلغاء أي تحليل سابق إذا غيّر المستخدم الرابط.

### 2.3 خيارات التحميل
- **قائمة الجودات:** 360p / 480p / 720p / 1080p (وتظهر 1440p/2160p فقط إن كانت متاحة فعلًا). كل خيار يعرض الحجم التقريبي.
- **الصوت فقط:** زر واحد "تحميل MP3" (192 kbps افتراضيًا).
- تُبنى القائمة من الصيغ (formats) الحقيقية التي أعادها yt-dlp، لا من قائمة ثابتة.

### 2.4 التحميل
- يعمل داخل Foreground Service بإشعار مستمر يعرض النسبة والسرعة.
- يمكن إلغاؤه من الإشعار.
- عند الاكتمال: إشعار "اكتمل — اضغط للفتح" يفتح الملف بالمشغّل الافتراضي.
- دعم تحميلات متعددة متسلسلة (طابور بسيط، واحد في كل مرة).

### 2.5 السجل (اختياري في v1، مُوصى به)
- شاشة ثانية تعرض التحميلات المكتملة: صورة، عنوان، حجم، تاريخ، زر فتح/حذف.
- تخزين محلي عبر Room.

---

## 3. المتطلبات غير الوظيفية

| المتطلب | القيمة |
|---|---|
| حجم APK | ~60–80 MB (بسبب Python + ffmpeg المضمّنين — مقبول لتطبيق شخصي). استخدم `abiFilters` لـ `arm64-v8a` فقط لتقليصه إلى النصف |
| الحد الأدنى للنظام | Android 8.0 (API 26) |
| الشبكة | يجب أن يعمل على Wi-Fi وبيانات الجوال بلا تمييز |
| الخصوصية | لا خوادم خارجية، لا تحليلات، لا حسابات. كل شيء على الجهاز |
| اللغة | عربي أولًا (RTL كامل) مع إنجليزية |
| الاستقرار | عند فشل التحليل، يُعرض سبب الفشل النصي من yt-dlp مع زر "تحديث المحرّك وإعادة المحاولة" |

---

## 4. واجهة المستخدم

### 4.1 الشاشة الرئيسية

```text
┌──────────────────────────────────────┐
│        Video Downloader         ⋮    │
├──────────────────────────────────────┤
│  [ الصق رابط الفيديو هنا........ ]   │
│  [ لصق ]              [ تحليل ]      │
├──────────────────────────────────────┤
│  ┌────────┐  عنوان الفيديو           │
│  │ صورة   │  YouTube · 04:32          │
│  └────────┘  اسم القناة              │
├──────────────────────────────────────┤
│  جودة الفيديو                        │
│  ○ 360p   MP4   ~12 MB               │
│  ○ 480p   MP4   ~21 MB               │
│  ● 720p   MP4   ~45 MB               │
│  ○ 1080p  MP4   ~95 MB               │
│              [ تحميل الفيديو ]       │
├──────────────────────────────────────┤
│  🎵 الصوت فقط                        │
│              [ تحميل MP3 ]           │
└──────────────────────────────────────┘
```

### 4.2 حالات الشاشة (UI State)

```kotlin
sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Analyzing : HomeUiState
    data class Ready(val info: MediaInfo, val formats: List<FormatOption>) : HomeUiState
    data class Error(val message: String, val canRetryAfterUpdate: Boolean) : HomeUiState
}
```

### 4.3 الإشعارات
- قناة `downloads` (أهمية LOW حتى لا يصدر صوتًا مع كل تحديث نسبة).
- إشعار التقدّم: عنوان الفيديو + شريط تقدّم + السرعة + زر إلغاء.
- إشعار الاكتمال: قناة منفصلة `completed` بأهمية DEFAULT.

---

## 5. المعمارية

### 5.1 الطبقات

```text
┌─────────────────────────────────────────────┐
│  UI (Compose)                               │
│  HomeScreen · HistoryScreen · Notifications │
├─────────────────────────────────────────────┤
│  ViewModel                                  │
│  HomeViewModel · HistoryViewModel           │
├─────────────────────────────────────────────┤
│  Domain                                     │
│  SourceDetector · FormatSelector · Models   │
├─────────────────────────────────────────────┤
│  Data                                       │
│  MediaRepository · DownloadRepository       │
│  YtDlpEngine (wrapper) · MediaStoreSaver    │
│  Room (history)                             │
├─────────────────────────────────────────────┤
│  Infra                                      │
│  DownloadService (Foreground) · Updater     │
│  youtubedl-android (yt-dlp + ffmpeg)        │
└─────────────────────────────────────────────┘
```

### 5.2 SourceDetector — دوره الحقيقي

`SourceDetector` طبقة **عرض وتخصيص**، لا طبقة استخراج. yt-dlp هو من يتعرّف على المصدر فعليًا ويستخرج منه. دور `SourceDetector`:

1. عرض اسم/أيقونة المصدر للمستخدم قبل وأثناء التحليل.
2. تحديد خيارات yt-dlp الخاصة بكل مصدر (مثلًا Instagram قد يحتاج cookies، TikTok يُفضّل بلا علامة مائية).
3. رفض الروابط غير الصالحة مبكرًا قبل استدعاء المحرّك.

```kotlin
enum class Source(val label: String) {
    YOUTUBE("YouTube"), INSTAGRAM("Instagram"), TIKTOK("TikTok"),
    TWITTER("X / Twitter"), FACEBOOK("Facebook"),
    DIRECT("رابط مباشر"), OTHER("مصدر آخر"), UNKNOWN("غير معروف")
}

object SourceDetector {
    private val rules = listOf(
        Regex("""(youtube\.com|youtu\.be|youtube-nocookie\.com)""") to Source.YOUTUBE,
        Regex("""instagram\.com""") to Source.INSTAGRAM,
        Regex("""(tiktok\.com|vm\.tiktok\.com)""") to Source.TIKTOK,
        Regex("""(twitter\.com|x\.com)""") to Source.TWITTER,
        Regex("""(facebook\.com|fb\.watch)""") to Source.FACEBOOK,
        Regex("""\.(mp4|m4a|mp3|webm|mkv|mov)(\?.*)?$""", RegexOption.IGNORE_CASE) to Source.DIRECT,
    )

    fun detect(url: String): Source {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return Source.UNKNOWN
        if (u.scheme !in setOf("http", "https")) return Source.UNKNOWN
        val host = u.host?.lowercase() ?: return Source.UNKNOWN
        return rules.firstOrNull { (re, _) -> re.containsMatchIn(url) }?.second ?: Source.OTHER
    }
}
```

> `OTHER` تعني: رابط صالح لا نعرف اسمه، لكن نمرّره لـ yt-dlp على أي حال — فهو يدعم أكثر من ألف موقع. لا تحجب المستخدم بسبب قائمتك القصيرة.

### 5.3 YtDlpEngine — الغلاف حول المحرّك

```kotlin
class YtDlpEngine(private val app: Application) {

    suspend fun init() = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
    }

    suspend fun analyze(url: String): MediaInfo = withContext(Dispatchers.IO) {
        val info = YoutubeDL.getInstance().getInfo(url)   // يستدعي --dump-single-json داخليًا
        info.toMediaInfo()
    }

    suspend fun download(
        url: String,
        selection: DownloadSelection,      // Video(height) أو AudioMp3
        outputDir: File,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val req = YoutubeDLRequest(url).apply {
            addOption("-o", "${outputDir.absolutePath}/%(title).100B [%(id)s].%(ext)s")
            addOption("--no-mtime")
            addOption("--restrict-filenames")           // آمن لأسماء عربية طويلة/رموز
            when (selection) {
                is DownloadSelection.Video -> {
                    addOption("-f",
                        "bestvideo[height<=${selection.height}][ext=mp4]+bestaudio[ext=m4a]" +
                        "/best[height<=${selection.height}][ext=mp4]/best")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadSelection.AudioMp3 -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                    addOption("--embed-thumbnail")
                    addOption("--embed-metadata")
                }
            }
        }
        YoutubeDL.getInstance().execute(req, processId = url.hashCode().toString()) { p, eta, line ->
            onProgress(p, eta, line)
        }
        outputDir.listFiles()!!.maxByOrNull { it.lastModified() }!!
    }

    suspend fun update(): UpdateStatus = withContext(Dispatchers.IO) {
        YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
    }
}
```

**نقطة مهمة:** الـ `-f` أعلاه ليست تجميلًا. YouTube يقدّم الجودات فوق 720p كمسارين منفصلين (فيديو بلا صوت + صوت)، ولهذا ffmpeg ضروري للدمج. بدونه ستحصل على فيديو صامت.

### 5.4 تدفّق التحميل الكامل

```text
User taps "تحميل"
   ↓
HomeViewModel.startDownload(selection)
   ↓
DownloadService.enqueue(job)          ← Foreground Service يبدأ فورًا
   ↓
YtDlpEngine.download(..., cacheDir)   ← يكتب إلى مجلد مؤقت خاص بالتطبيق
   ↓ onProgress → Notification.update()
   ↓
MediaStoreSaver.moveToDownloads(file) ← ينقل الملف إلى Downloads/VideoDownloader عبر MediaStore
   ↓
Room.insert(HistoryEntry)
   ↓
Notification "اكتمل" + Toast
```

لماذا التحميل إلى `cacheDir` أولًا ثم النقل؟ لأن yt-dlp/ffmpeg بحاجة لمسار ملف حقيقي، بينما MediaStore على Android 10+ يعمل عبر `Uri` لا مسارات. الحل القياسي: حمّل إلى مجلدك الخاص، ثم انسخ عبر `contentResolver.openOutputStream(uri)`.

### 5.5 حفظ الملف عبر MediaStore

```kotlin
class MediaStoreSaver(private val resolver: ContentResolver) {
    fun saveToDownloads(src: File, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoDownloader")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        src.delete()
        return uri
    }
}
```

لا حاجة لـ `WRITE_EXTERNAL_STORAGE` على Android 10+. على Android 8–9 أضف الصلاحية مع `maxSdkVersion="28"`.

---

## 6. التحديث الذاتي للمحرّك — الميزة التي تحدّد عمر التطبيق

هذه أهم فقرة في الوثيقة من الناحية العملية.

**المشكلة:** YouTube تحديدًا يغيّر آليات التوقيع والحماية باستمرار، ويصدر فريق yt-dlp إصلاحًا خلال أيام. تطبيق مبني بنسخة yt-dlp ثابتة سيتعطّل حتمًا خلال أسابيع.

**الحل:** المكتبة تدعم تحديث ثنائي yt-dlp وحده في وقت التشغيل (بدون إعادة بناء APK):

- عند بدء التطبيق: إن مرّ أكثر من 7 أيام على آخر تحديث → `updateYoutubeDL()` في الخلفية بصمت.
- عند فشل التحليل: أظهر زر **"تحديث المحرّك وإعادة المحاولة"** — يحل ~80% من الأعطال المفاجئة.
- في الإعدادات: زر تحديث يدوي + عرض إصدار yt-dlp الحالي.
- استخدم قناة `STABLE`؛ `NIGHTLY` فقط إن كان الإصدار المستقر معطّلًا لأيام.

---

## 7. القيود والمخاطر

| المخاطرة | التأثير | التخفيف |
|---|---|---|
| YouTube يطلب تحققًا ("Sign in to confirm you're not a bot") | فشل التحليل | التحديث الذاتي؛ وإن استمر: دعم استيراد ملف cookies من المتصفح (`--cookies`) كخيار متقدم |
| Instagram يحجب بدون تسجيل دخول | كثير من روابط Instagram تفشل | نفس حل cookies؛ اعتبره "أفضل جهد" لا التزامًا |
| حجم APK كبير | ~70 MB | `abiFilters("arm64-v8a")` — كل الأجهزة الحديثة 64-bit |
| النظام يقتل التحميل الطويل | ملف ناقص | Foreground Service + `--continue` في yt-dlp لاستئناف الملفات الجزئية |
| أسماء ملفات عربية طويلة | فشل الحفظ | `--restrict-filenames` أو قصّ العنوان إلى 100 بايت (`%(title).100B`) |
| **شروط الاستخدام** | YouTube وأمثاله تحظر التحميل في شروطها | التطبيق شخصي ولا يُوزّع — أنت تتحمّل هذا الاعتبار بوعي. لا تضف مشاركة الملفات أو تجاوز محتوى مدفوع |

---

## 8. خطة التنفيذ (مقترحة للتنفيذ بالوكلاء الذكية)

كل مرحلة ينتج عنها تطبيق يعمل ويمكن تجربته.

### المرحلة 1 — الهيكل والمحرّك (يوم واحد)
- مشروع Kotlin + Compose، Material 3، RTL.
- إضافة `youtubedl-android` (library + ffmpeg) الإصدار 0.18.1.
- `YtDlpEngine.init()` عند بدء التطبيق مع شاشة splash قصيرة ("تهيئة المحرّك" تحدث مرة واحدة فقط وتستغرق ~10 ثوانٍ في أول تشغيل).
- **معيار النجاح:** زر واحد يحمّل رابط YouTube ثابت إلى `cacheDir` ويطبع المسار في Logcat.

### المرحلة 2 — التحليل والواجهة (يوم)
- `SourceDetector`، `analyze()`، `HomeUiState`، الشاشة الرئيسية كاملة.
- بناء قائمة الجودات من `formats` الحقيقية مع الحجم التقريبي.
- **معيار النجاح:** لصق رابط → رؤية الصورة والعنوان والجودات المتاحة فعلًا.

### المرحلة 3 — التحميل الخلفي والحفظ (يوم)
- `DownloadService` + إشعارات التقدّم + الإلغاء.
- `MediaStoreSaver`.
- MP3 مع صورة الغلاف والبيانات الوصفية.
- **معيار النجاح:** الملف يظهر في تطبيق "الملفات" ويُشغَّل، والإشعار يعمل مع التطبيق مغلق.

### المرحلة 4 — الصقل (نصف يوم)
- استقبال المشاركة (`ACTION_SEND`) والحافظة التلقائية.
- التحديث الذاتي للمحرّك + زر "تحديث وإعادة المحاولة".
- شاشة السجل (Room).
- `abiFilters` وتوقيع APK للتثبيت الشخصي.

### 8.1 ملف `build.gradle.kts` (مقتطف)

```kotlin
android {
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs { useLegacyPackaging = true } }   // مطلوب للمكتبة
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
    // Compose BOM + Material3 + Coil (للصور المصغّرة) + Room + Lifecycle
}
```

### 8.2 الصلاحيات

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                 android:maxSdkVersion="28" />
```

---

## 9. البدائل التي تم استبعادها (ولماذا)

| البديل | الحكم |
|---|---|
| **كتابة مستخرِجات خاصة لكل مصدر** | مرفوض. صيانة لا تنتهي، ويعطّل خلال أسابيع. |
| **خادم VPS يشغّل yt-dlp وتطبيق خفيف يستدعيه** | مغرٍ لأنه يجعل APK صغيرًا ويسهّل التحديث، لكن YouTube يحجب عناوين مراكز البيانات بشدة (يطالب بـ PO Token / cookies). التحميل من الجهاز نفسه بعنوان IP سكني أكثر نجاحًا. يبقى خيارًا إن أردت لاحقًا مشاركة الأداة مع آخرين. |
| **Flutter + جسر Kotlin** | يعمل، لكن كل المنطق الحقيقي سيكون في Kotlin على أي حال. يبرَّر فقط إن أردت مستقبلًا إصدار iOS — وهذا مستحيل هنا أصلًا لأن iOS لا يسمح بتنفيذ ثنائيات Python/ffmpeg مضمّنة بهذه الطريقة. |
| **Termux + سكربت** | يعمل للاستخدام الشخصي البحت، لكنه ليس "تطبيقًا"، ويفتقر إلى المشاركة والإشعارات. |
| **مكتبة NewPipeExtractor (Java خالصة، بلا Python)** | خيار جيد لتصغير APK إلى ~10 MB، لكنها تدعم YouTube وقلّة من المواقع فقط، ولا تدمج المسارات بدون ffmpeg. غير مناسبة لمتطلب "TikTok + Instagram + روابط مباشرة". |

---

## 10. أفكار للتوسّع بعد v1

- **قوائم التشغيل:** yt-dlp يدعمها أصلًا؛ يلزم فقط واجهة اختيار العناصر.
- **الترجمات:** `--write-subs --sub-langs ar,en` ودمجها في MP4.
- **اقتصاص مقطع زمني:** `--download-sections "*00:01:00-00:02:30"`.
- **صور بروفايل/ملفات Instagram:** يدعمها المحرّك.
- **ربط بمشروع Naqaa:** تحميل MP3 ثم فصل الأصوات (stem separation) في تدفّق واحد — تكامل طبيعي بين تطبيقين لديك.

---

*نهاية الوثيقة.*
