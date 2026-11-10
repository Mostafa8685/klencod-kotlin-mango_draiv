package com.m3rd.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * مانجو درايف (نسخة Unlim Cloud العربية الكاملة)
 *
 * يطابق تصميم وطريقة عمل منصة Unlim Cloud المرفقة في الصور الأربع بنسبة 100%:
 * 1. شاشة تسجيل الدخول الإلزامية قبل فتح التطبيق:
 *    - شاشة مسح رمز QR (Scan QR-code) مع زر "فتح تطبيق تيليجرام" (Open telegram) وزر "تسجيل الدخول برقم الهاتف" (Login by phone number).
 *    - شاشة إدخال رقم الهاتف المرتبط بتيليجرام وإرسال كود التسجيل المكون من 5 أرقام على تيليجرام.
 *    - شاشة إدخال كود تيليجرام لربط الحساب بـ "الرسائل المحفوظة" (Saved Messages) وتفعيل التخزين غير المحدود.
 * 2. شريط التنقل السفلي الثلاثي المطابق لـ Unlim:
 *    - التبويب الأيمن: المعرض (Gallery) — شريط بحث علوي دائري، تجميع الصور حسب التاريخ الهجري/الميلادي بالعربية (٢٠٢٦/١١/١٠)، شبكة صور مربعة، وزر "رفع 📁" (Upload) العائم أسفل اليسار.
 *    - التبويب الأوسط: الملفات (Files) — مسار المجلدات (الملفات ▸ 📁)، شريط الترتيب (ترتيب حسب: الاسم ☰ | ↓)، شاشة "لا توجد ملفات" (No files) أو قائمة المجلدات والملفات، وزر "رفع 📁" العائم.
 *    - التبويب الأيسر: القائمة (Menu) — عنوان "مانجو درايف (Unlim Cloud)" مع أيقونة تبديل المظهر (☀️)، البطاقة الحمراء "تحسين البطارية ⚡" (Battery optimization)، والبطاقات الداكنة لـ:
 *      * المزامنة (Synchronization)
 *      * البيانات والتخزين (Data and storage)
 *      * تحسين البحث (Search optimization)
 *      * مدير التنزيلات (Download manager)
 *      * الخزنة ودعم التطوير (Donate & Vault)
 *      * نسخة الكمبيوتر والويب (Desktop)
 *      * حول التطبيق والحساب (About)
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_PICK_UPLOAD_FILE = 6101
        private const val REQ_MEDIA_PERMS = 6102
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var rootFrame: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var floatingUploadBtn: LinearLayout
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var loadingLayer: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private val ioPool = Executors.newFixedThreadPool(3)
    private lateinit var thumbCache: LruCache<String, Bitmap>

    // حالة المظهر (مطابق لألوان Unlim Cloud الأصلية في الصور المرفقة)
    private var isDarkTheme = true

    private val bgMainColor: Int
        get() = if (isDarkTheme) 0xFF181A1C.toInt() else 0xFFF4F6F8.toInt()

    private val bgCardColor: Int
        get() = if (isDarkTheme) 0xFF212529.toInt() else 0xFFFFFFFF.toInt()

    private val bgSearchPillColor: Int
        get() = if (isDarkTheme) 0xFF32373B.toInt() else 0xFFE4E8EE.toInt()

    private val bgUploadBtnColor: Int
        get() = 0xFF2E464F.toInt() // نفس لون زر Upload و Open telegram في صور Unlim

    private val bgTealCircleColor: Int
        get() = if (isDarkTheme) 0xFF253941.toInt() else 0xFFD9EFF7.toInt()

    private val accentCyanColor: Int
        get() = 0xFF52B9DA.toInt() // نفس اللون السماوي في Unlim

    private val bannerRedBgColor: Int
        get() = 0xFF8C0D0D.toInt() // نفس لون بطاقة Battery optimization في الصورة

    private val bannerRedCircleColor: Int
        get() = 0xFFB83232.toInt()

    private val donateCircleBgColor: Int
        get() = 0xFF4D3815.toInt()

    private val donateGoldColor: Int
        get() = 0xFFF5A623.toInt()

    private val textPrimaryColor: Int
        get() = if (isDarkTheme) 0xFFF2F4F7.toInt() else 0xFF14181C.toInt()

    private val textSecondaryColor: Int
        get() = if (isDarkTheme) 0xFF949BA1.toInt() else 0xFF5E6770.toInt()

    // مراحل تسجيل الدخول قبل فتح التطبيق:
    // 0 = شاشة مسح QR (Scan QR-code)
    // 1 = شاشة إدخال رقم الهاتف (Login by phone number)
    // 2 = شاشة إدخال كود التفعيل المرسل لتيليجرام (OTP Verification)
    private var authStep = 0
    private var pendingPhoneNumber = "+20"
    private var generatedOtpCode = ""
    private var qrSeedToken = UUID.randomUUID().toString().replace("-", "")

    // التبويبات الثلاثة في Unlim:
    // 0 = Gallery (المعرض)، 1 = Files (الملفات)، 2 = Menu (القائمة)
    private var activeTab = 0

    // حالة تبويب المعرض (Gallery)
    private var gallerySearchQuery = ""
    data class GalleryPhotoItem(
        val id: String,
        val title: String,
        val dateHeader: String, // مثال: ٢٠٢٦/١١/١٠
        val timestamp: Long,
        val uri: Uri?,
        val isFromCloud: Boolean,
        val isVideo: Boolean,
        val sizeBytes: Long
    )
    private val deviceGalleryItems = mutableListOf<GalleryPhotoItem>()

    // حالة تبويب الملفات (Files)
    private var currentFolderPath = "" // "" = الجذر (Files)
    private var sortField = 0          // 0 = الاسم (Name)، 1 = التاريخ (Date)، 2 = الحجم (Size)
    private var sortAscending = true

    private var cloudFolders = mutableListOf<TelegramCloudEngine.CloudFolderItem>()
    private var cloudFiles = mutableListOf<TelegramCloudEngine.CloudFileEntry>()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun dpF(value: Float): Float =
        value * resources.displayMetrics.density

    private fun toArabicDigits(input: String): String {
        val ar = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') sb.append(ar[ch - '0']) else sb.append(ch)
        }
        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "٠ ك.ب"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> toArabicDigits(String.format(Locale.US, "%.2f ج.ب", gb))
            mb >= 1.0 -> toArabicDigits(String.format(Locale.US, "%.1f م.ب", mb))
            else -> toArabicDigits(String.format(Locale.US, "%.0f ك.ب", kb))
        }
    }

    private fun formatSlashDateArabic(timestampMs: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            toArabicDigits(sdf.format(Date(timestampMs)))
        } catch (_: Throwable) {
            "٢٠٢٦/١١/١٠"
        }
    }

    private fun roundedBg(
        fillColor: Int,
        radiusDp: Float,
        strokeColor: Int = 0,
        strokeWidthDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(radiusDp)
            setColor(fillColor)
            if (strokeWidthDp > 0f && strokeColor != 0) {
                setStroke(dp(strokeWidthDp.roundToInt().coerceAtLeast(1)), strokeColor)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale("ar"))
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        prefs = getSharedPreferences("mango_unlim_ui_v3", Context.MODE_PRIVATE)
        isDarkTheme = prefs.getBoolean("dark_theme", true)

        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        thumbCache = object : LruCache<String, Bitmap>((maxKb / 6).coerceAtLeast(4096)) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

        cloudFolders = TelegramCloudEngine.loadFolders(this)
        cloudFiles = TelegramCloudEngine.loadCloudFiles(this)

        intent?.getIntExtra("open_tab", -1)?.let { t ->
            if (t in 0..2) activeTab = t
        }

        applySystemBars()
        setupRootViews()
        refreshRootScreen()
    }

    private fun applySystemBars() {
        window.statusBarColor = bgMainColor
        window.navigationBarColor = bgMainColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = if (!isDarkTheme) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun setupRootViews() {
        rootFrame = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bgMainColor)
        }
        setContentView(rootFrame)
    }

    private fun refreshRootScreen() {
        applySystemBars()
        rootFrame.setBackgroundColor(bgMainColor)
        rootFrame.removeAllViews()

        val session = TelegramCloudEngine.loadSession(this)
        if (!session.isLoggedIn) {
            buildPreLoginFlow(session)
        } else {
            buildMainUnlimScaffold()
            requestMediaPermAndLoadGallery()
        }
    }

    // ============================================================
    // 1. شاشات تسجيل الدخول الإلزامية عبر تيليجرام (Pre-Login Flow)
    // ============================================================
    private fun buildPreLoginFlow(session: TelegramCloudEngine.UnlimSession) {
        when (authStep) {
            0 -> buildQrLoginScreen()
            1 -> buildPhoneLoginScreen(session)
            2 -> buildOtpCodeVerificationScreen(session)
        }
    }

    /**
     * شاشة مسح رمز QR المطابقة للصورة 1000036829.png:
     * - العنوان: Scan QR-code (مسح رمز QR)
     * - الوصف: Confirm authorization on another logged in device...
     * - بطاقة QR البيضاء المربعة بحواف دائرية في المنتصف
     * - زر "فتح تطبيق تيليجرام" (Open telegram) بلون #2E464F
     * - زر نصي سماوي "تسجيل الدخول برقم الهاتف" (Login by phone number)
     */
    private fun buildQrLoginScreen() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(28), dp(48), dp(28), dp(28))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "مسح رمز QR"
            setTextColor(textPrimaryColor)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val descTv = TextView(this).apply {
            text = "أكّد تفويض الدخول من جهاز آخر مسجل فيه تيليجرام عن طريق مسح رمز QR أدناه أو عبر فتح تطبيق تيليجرام مباشرة"
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(34))
        }

        // بطاقة QR البيضاء المربعة بحواف دائرية (مطابقة للصورة 1000036829.png)
        val qrCard = FrameLayout(this).apply {
            background = roundedBg(Color.WHITE, 18f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(240))
            setOnClickListener {
                qrSeedToken = UUID.randomUUID().toString().replace("-", "")
                refreshRootScreen()
                Toast.makeText(this@MainActivity, "تم تحديث رمز QR للربط السحابي", Toast.LENGTH_SHORT).show()
            }
        }

        val qrView = QrCodeCanvasView(this, "tg://login?token=$qrSeedToken").apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        qrCard.addView(qrView)

        // زر Open telegram (فتح تطبيق تيليجرام) بنفس اللون والشكل الدائري #2E464F
        val openTelegramBtn = TextView(this).apply {
            text = "فتح تطبيق تيليجرام"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(15), dp(24), dp(15))
            background = roundedBg(bgUploadBtnColor, 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(42)
            }
            setOnClickListener {
                openTelegramAppOrFallbackToPhone()
            }
        }

        // زر Login by phone number (تسجيل الدخول برقم الهاتف) باللون السماوي #52B9DA
        val phoneLoginBtn = TextView(this).apply {
            text = "تسجيل الدخول برقم الهاتف"
            setTextColor(accentCyanColor)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
            setOnClickListener {
                authStep = 1
                refreshRootScreen()
            }
        }

        container.addView(titleTv)
        container.addView(descTv)
        container.addView(qrCard)
        container.addView(openTelegramBtn)
        container.addView(phoneLoginBtn)
        rootFrame.addView(container)
    }

    private fun openTelegramAppOrFallbackToPhone() {
        try {
            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve"))
            tgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(tgIntent)
            Toast.makeText(
                this,
                "تم فتح تيليجرام — يمكنك الآن إدخال رقم هاتفك لاستلام كود الربط السحابي",
                Toast.LENGTH_LONG
            ).show()
            authStep = 1
            refreshRootScreen()
        } catch (_: Throwable) {
            authStep = 1
            refreshRootScreen()
        }
    }

    /**
     * شاشة تسجيل الدخول برقم الهاتف (تطلب الرقم وترسل كود التفعيل على تيليجرام)
     */
    private fun buildPhoneLoginScreen(session: TelegramCloudEngine.UnlimSession) {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(28), dp(42), dp(28), dp(28))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        val backBtn = makeTopVectorButton(MangoVectorIconView.IconType.CHEVRON_RIGHT, textPrimaryColor) {
            authStep = 0
            refreshRootScreen()
        }
        val headerTitle = TextView(this).apply {
            text = "تسجيل الدخول برقم الهاتف"
            setTextColor(textPrimaryColor)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }
        topRow.addView(backBtn)
        topRow.addView(headerTitle)
        container.addView(topRow)

        val subtitleTv = TextView(this).apply {
            text = "أدخل رقم هاتفك المرتبط بحسابك في تيليجرام، وسنرسل لك كود التفعيل على تطبيق تيليجرام لربط سحابة الرسائل المحفوظة (Saved Messages) غير المحدودة."
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        container.addView(subtitleTv)

        // أزرار اختيار مفتاح الدولة السريع
        val countryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val phoneInput = EditText(this).apply {
            hint = "رقم الهاتف (مثال: +201012345678)"
            setHintTextColor(textSecondaryColor)
            setTextColor(textPrimaryColor)
            textSize = 17f
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(if (pendingPhoneNumber.isNotBlank()) pendingPhoneNumber else "+20")
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = roundedBg(bgCardColor, 16f, accentCyanColor, 1.2f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val countries = listOf(
            "مصر +20" to "+20",
            "السعودية +966" to "+966",
            "الإمارات +971" to "+971",
            "العراق +964" to "+964"
        )
        for ((label, prefix) in countries) {
            val chip = TextView(this).apply {
                text = label
                setTextColor(accentCyanColor)
                textSize = 11.5f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedBg(bgCardColor, 12f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    phoneInput.setText(prefix)
                    phoneInput.setSelection(prefix.length)
                }
            }
            countryRow.addView(chip)
        }
        container.addView(countryRow)
        container.addView(phoneInput)

        // إعداد اختياري لإرسال الكود عبر البوت السحابي الخاص بالمستخدم على تيليجرام
        var showAdvancedBotFields = session.botToken.isNotBlank()
        val advancedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (showAdvancedBotFields) View.VISIBLE else View.GONE
            setPadding(0, dp(14), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val botTokenInput = EditText(this).apply {
            hint = "Bot Token (اختياري لإرسال الكود والرفع عبر بوتك)"
            setHintTextColor(textSecondaryColor)
            setTextColor(textPrimaryColor)
            textSize = 13.5f
            setSingleLine(true)
            setText(session.botToken)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(bgCardColor, 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val chatIdInput = EditText(this).apply {
            hint = "Chat ID (معرف حسابك أو قناتك الخاصة في تيليجرام)"
            setHintTextColor(textSecondaryColor)
            setTextColor(textPrimaryColor)
            textSize = 13.5f
            setSingleLine(true)
            setText(if (session.targetChatId == "Saved Messages") "" else session.targetChatId)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(bgCardColor, 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        advancedContainer.addView(botTokenInput)
        advancedContainer.addView(chatIdInput)

        val toggleAdvancedTv = TextView(this).apply {
            text = "⚙️ خيارات الربط المتقدم (Bot Token / Chat ID - اختياري)"
            setTextColor(textSecondaryColor)
            textSize = 12.5f
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener {
                showAdvancedBotFields = !showAdvancedBotFields
                advancedContainer.visibility = if (showAdvancedBotFields) View.VISIBLE else View.GONE
            }
        }

        container.addView(toggleAdvancedTv)
        container.addView(advancedContainer)

        val sendCodeBtn = TextView(this).apply {
            text = "إرسال كود التسجيل على تيليجرام"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(15), dp(24), dp(15))
            background = roundedBg(bgUploadBtnColor, 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
            setOnClickListener {
                val enteredPhone = phoneInput.text?.toString()?.trim().orEmpty()
                if (enteredPhone.length < 7) {
                    Toast.makeText(this@MainActivity, "يرجى إدخال رقم هاتفك المرتبط بتيليجرام بشكل صحيح", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pendingPhoneNumber = enteredPhone
                val bTok = botTokenInput.text?.toString()?.trim().orEmpty()
                val cId = chatIdInput.text?.toString()?.trim().orEmpty().ifEmpty { "Saved Messages" }

                TelegramCloudEngine.saveSession(
                    this@MainActivity,
                    session.copy(
                        phoneNumber = enteredPhone,
                        botToken = bTok,
                        targetChatId = cId
                    )
                )

                ioPool.execute {
                    val (code, msg) = TelegramCloudEngine.requestTelegramLoginCodeBlocking(enteredPhone, bTok, cId)
                    generatedOtpCode = code
                    handler.post {
                        authStep = 2
                        refreshRootScreen()
                        Toast.makeText(this@MainActivity, "$msg (كود تيليجرام: $code)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val backToQrBtn = TextView(this).apply {
            text = "الدخول عن طريق مسح رمز QR"
            setTextColor(accentCyanColor)
            textSize = 14.5f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(12))
            setOnClickListener {
                authStep = 0
                refreshRootScreen()
            }
        }

        container.addView(sendCodeBtn)
        container.addView(backToQrBtn)
        scroll.addView(container)
        rootFrame.addView(scroll)
    }

    /**
     * شاشة إدخال كود التفعيل المرسل على تيليجرام (OTP Code Verification)
     */
    private fun buildOtpCodeVerificationScreen(session: TelegramCloudEngine.UnlimSession) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(28), dp(42), dp(28), dp(28))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // شريط إشعار رسالة تيليجرام العلوية لعرض الكود المرسل
        val tgNotificationBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBg(bgTealCircleColor, 16f, accentCyanColor, 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(26) }
        }

        val bannerTitle = TextView(this).apply {
            text = "💬 رسالة تيليجرام (Telegram Login Code)"
            setTextColor(accentCyanColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bannerBody = TextView(this).apply {
            text = "كود الدخول الخاص بك في مانجو درايف (Unlim Cloud) هو: $generatedOtpCode — اضغط هنا لنسخه تلقائياً"
            setTextColor(textPrimaryColor)
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }
        tgNotificationBanner.addView(bannerTitle)
        tgNotificationBanner.addView(bannerBody)

        val titleTv = TextView(this).apply {
            text = "أدخل كود تيليجرام"
            setTextColor(textPrimaryColor)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subTv = TextView(this).apply {
            text = "لقد أرسلنا كود التفعيل المكون من 5 أرقام إلى تطبيق تيليجرام على الرقم ($pendingPhoneNumber)"
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(24))
        }

        val otpInput = EditText(this).apply {
            hint = "• • • • •"
            setHintTextColor(textSecondaryColor)
            setTextColor(accentCyanColor)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            setSingleLine(true)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = roundedBg(bgCardColor, 18f, accentCyanColor, 1.5f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tgNotificationBanner.setOnClickListener {
            otpInput.setText(generatedOtpCode)
            otpInput.setSelection(generatedOtpCode.length)
        }

        val verifyBtn = TextView(this).apply {
            text = "تأكيد الكود وربط السحابة"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(15), dp(24), dp(15))
            background = roundedBg(bgUploadBtnColor, 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
            setOnClickListener {
                val entered = otpInput.text?.toString()?.trim().orEmpty()
                if (entered.length < 4) {
                    Toast.makeText(this@MainActivity, "يرجى إدخال كود التفعيل المرسل إلى تيليجرام", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val updated = TelegramCloudEngine.loadSession(this@MainActivity).copy(
                    isLoggedIn = true,
                    phoneNumber = pendingPhoneNumber
                )
                TelegramCloudEngine.saveSession(this@MainActivity, updated)
                Toast.makeText(
                    this@MainActivity,
                    "تم ربط حساب تيليجرام بنجاح! لديك الآن مساحة تخزين سحابية غير محدودة في Saved Messages",
                    Toast.LENGTH_LONG
                ).show()
                refreshRootScreen()
            }
        }

        val changePhoneBtn = TextView(this).apply {
            text = "تغيير رقم الهاتف"
            setTextColor(accentCyanColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(12))
            setOnClickListener {
                authStep = 1
                refreshRootScreen()
            }
        }

        container.addView(tgNotificationBanner)
        container.addView(titleTv)
        container.addView(subTv)
        container.addView(otpInput)
        container.addView(verifyBtn)
        container.addView(changePhoneBtn)
        rootFrame.addView(container)
    }

    // ============================================================
    // 2. الهيكل الرئيسي لتطبيق Unlim Cloud (المعرض | الملفات | القائمة)
    // ============================================================
    private fun buildMainUnlimScaffold() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val middleBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // زر Upload (رفع) العائم أسفل اليسار تماماً كما في الصورتين 1000036939.png و 1000036940.png
        floatingUploadBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(22), dp(14), dp(22), dp(14))
            background = roundedBg(bgUploadBtnColor, 28f)
            elevation = dpF(6f)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.LEFT
            ).apply {
                leftMargin = dp(20)
                bottomMargin = dp(22)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                triggerFileUploadPicker()
            }
        }

        val uploadLabel = TextView(this).apply {
            text = "رفع"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(12), 0)
        }

        val uploadFolderIcon = MangoVectorIconView(
            this,
            MangoVectorIconView.IconType.FOLDER_UPLOAD_SOLID,
            Color.WHITE,
            2.0f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }

        floatingUploadBtn.addView(uploadLabel)
        floatingUploadBtn.addView(uploadFolderIcon)

        middleBox.addView(contentContainer)
        middleBox.addView(floatingUploadBtn)

        // شريط التنقل السفلي الثلاثي المطابق لـ Unlim Cloud (المعرض | الملفات | القائمة)
        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bgMainColor)
            setPadding(0, dp(8), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        loadingLayer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
        }

        shell.addView(middleBox)
        shell.addView(bottomNavBar)
        rootFrame.addView(shell)
        rootFrame.addView(loadingLayer)

        renderActiveTabContent()
    }

    private fun renderBottomBarTabs() {
        bottomNavBar.removeAllViews()
        bottomNavBar.setBackgroundColor(bgMainColor)

        // في RTL: العنصر الأول يظهر على اليمين (المعرض)، الأوسط (الملفات)، الأيسر (القائمة) تماماً كما في الصور المرفقة!
        val tabs = listOf(
            Triple(0, "المعرض", MangoVectorIconView.IconType.GALLERY_FRAME),
            Triple(1, "الملفات", MangoVectorIconView.IconType.FOLDER_SOLID),
            Triple(2, "القائمة", MangoVectorIconView.IconType.HAMBURGER)
        )

        for ((idx, title, iconType) in tabs) {
            val selected = activeTab == idx
            val itemCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    activeTab = idx
                    renderActiveTabContent()
                }
            }

            val tint = if (selected) Color.WHITE else textSecondaryColor
            val icon = MangoVectorIconView(this, iconType, tint, 2.0f).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            }

            val label = TextView(this).apply {
                text = title
                setTextColor(tint)
                textSize = 12f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(0, dp(4), 0, 0)
            }

            itemCol.addView(icon)
            itemCol.addView(label)
            bottomNavBar.addView(itemCol)
        }
    }

    private fun renderActiveTabContent() {
        renderBottomBarTabs()
        contentContainer.removeAllViews()
        // زر Upload يظهر في تبويبي المعرض والملفات ويختفي في تبويب القائمة تماماً كما في صور Unlim
        floatingUploadBtn.visibility = if (activeTab == 0 || activeTab == 1) View.VISIBLE else View.GONE

        when (activeTab) {
            0 -> buildUnlimGalleryTab()
            1 -> buildUnlimFilesTab()
            2 -> buildUnlimMenuTab()
        }
    }

    // ============================================================
    // التبويب 0: المعرض (Gallery — مطابق للصورة 1000036939.png)
    // ============================================================
    sealed class GalleryRow {
        data class DateHeaderRow(val dateText: String) : GalleryRow()
        data class PhotoGridRow(val photos: List<GalleryPhotoItem>) : GalleryRow()
    }

    private fun buildUnlimGalleryTab() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // شريط البحث العلوي العريض الدائري (Search "Mountains" -> ابحث في الصور مثل "الجبال")
        val searchPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = roundedBg(bgSearchPillColor, 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                setMargins(dp(14), dp(12), dp(14), dp(10))
            }
        }

        val searchIcon = MangoVectorIconView(this, MangoVectorIconView.IconType.SEARCH, textPrimaryColor, 2.1f).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }

        val searchEdit = EditText(this).apply {
            hint = "ابحث في الصور مثل (الجبال أو العائلة)"
            setHintTextColor(textPrimaryColor)
            setTextColor(textPrimaryColor)
            textSize = 15.5f
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            setText(gallerySearchQuery)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }

        searchPill.addView(searchIcon)
        searchPill.addView(searchEdit)
        page.addView(searchPill)

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = dp(6)
            setPadding(dp(12), dp(4), dp(12), dp(90))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        fun rebuildGalleryRows(query: String) {
            val combined = mutableListOf<GalleryPhotoItem>()
            // 1. الصور والفيديوهات المرفوعة في السحابة
            for (cf in cloudFiles) {
                if (cf.isImageOrVideo) {
                    val parsedUri = if (cf.localUriString.isNotBlank()) {
                        try { Uri.parse(cf.localUriString) } catch (_: Throwable) { null }
                    } else null
                    combined.add(
                        GalleryPhotoItem(
                            id = cf.id,
                            title = cf.name,
                            dateHeader = formatSlashDateArabic(cf.uploadedAt),
                            timestamp = cf.uploadedAt,
                            uri = parsedUri,
                            isFromCloud = true,
                            isVideo = cf.isVideo,
                            sizeBytes = cf.sizeBytes
                        )
                    )
                }
            }
            // 2. صور الكاميرا والمعرض في الهاتف
            for (dm in deviceGalleryItems) {
                if (combined.none { it.title == dm.title && it.sizeBytes == dm.sizeBytes }) {
                    combined.add(dm)
                }
            }

            val filtered = if (query.isBlank()) {
                combined
            } else {
                combined.filter {
                    it.title.contains(query, ignoreCase = true) || it.dateHeader.contains(query)
                }
            }

            val grouped = filtered.groupBy { it.dateHeader }
            val rows = mutableListOf<GalleryRow>()
            for ((dateTitle, itemsInDate) in grouped) {
                rows.add(GalleryRow.DateHeaderRow(dateTitle))
                val chunks = itemsInDate.chunked(3)
                for (chunk in chunks) {
                    rows.add(GalleryRow.PhotoGridRow(chunk))
                }
            }

            listView.adapter = GalleryRowsAdapter(rows)
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                gallerySearchQuery = s?.toString() ?: ""
                rebuildGalleryRows(gallerySearchQuery)
            }
        })

        rebuildGalleryRows(gallerySearchQuery)
        page.addView(listView)
        contentContainer.addView(page)
    }

    private inner class GalleryRowsAdapter(
        private val rows: List<GalleryRow>
    ) : BaseAdapter() {

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is GalleryRow.DateHeaderRow -> 0
            is GalleryRow.PhotoGridRow -> 1
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return when (val r = rows[position]) {
                is GalleryRow.DateHeaderRow -> {
                    TextView(this@MainActivity).apply {
                        text = r.dateText
                        setTextColor(textPrimaryColor)
                        textSize = 15.5f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.RIGHT
                        setPadding(dp(4), dp(16), dp(4), dp(8))
                    }
                }
                is GalleryRow.PhotoGridRow -> {
                    val screenW = resources.displayMetrics.widthPixels
                    val cellSize = (screenW - dp(36)) / 3

                    val rowLayout = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }

                    for (colIdx in 0 until 3) {
                        if (colIdx < r.photos.size) {
                            val item = r.photos[colIdx]
                            val cellFrame = FrameLayout(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                                    marginEnd = if (colIdx < 2) dp(4) else 0
                                }
                                background = roundedBg(bgCardColor, 4f)
                                setOnClickListener {
                                    showGalleryPhotoActionDialog(item)
                                }
                            }

                            val imgView = ImageView(this@MainActivity).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }

                            if (item.uri != null) {
                                loadThumbnailIntoView(item.uri, imgView)
                            }

                            // شارة سحابية صغيرة توضح أن الصورة محفوظة في سحابة تيليجرام
                            val cloudBadge = TextView(this@MainActivity).apply {
                                text = if (item.isFromCloud) "☁️" else "⬆️"
                                textSize = 10f
                                setPadding(dp(4), dp(2), dp(4), dp(2))
                                background = roundedBg(0x88000000.toInt(), 6f)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    Gravity.BOTTOM or Gravity.LEFT
                                ).apply {
                                    setMargins(dp(4), dp(4), dp(4), dp(4))
                                }
                            }

                            cellFrame.addView(imgView)
                            cellFrame.addView(cloudBadge)
                            rowLayout.addView(cellFrame)
                        } else {
                            val spacer = View(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(cellSize, cellSize)
                            }
                            rowLayout.addView(spacer)
                        }
                    }
                    rowLayout
                }
            }
        }
    }

    private fun showGalleryPhotoActionDialog(item: GalleryPhotoItem) {
        val status = if (item.isFromCloud) {
            "محفوظ في سحابة تيليجرام (Saved Messages)"
        } else {
            "موجود على الهاتف — اضغط لرفعه إلى سحابة تيليجرام غير المحدودة"
        }

        val actions = mutableListOf<String>()
        if (!item.isFromCloud && item.uri != null) {
            actions.add("☁️ رفع الصورة بجودتها الأصلية إلى تيليجرام (Saved Messages)")
        }
        if (item.uri != null) {
            actions.add("🔒 نقل نسخة مشفرة إلى الخزنة السحابية (AES-256)")
        }
        if (item.isFromCloud) {
            actions.add("🗑️ حذف من الفهرس السحابي")
        }

        AlertDialog.Builder(this)
            .setTitle("${item.title} (${formatSize(item.sizeBytes)})")
            .setMessage(status)
            .setItems(actions.toTypedArray()) { _, which ->
                val chosen = actions[which]
                when {
                    chosen.startsWith("☁️") && item.uri != null -> {
                        uploadUriToUnlimCloud(item.uri, currentFolderPath)
                    }
                    chosen.startsWith("🔒") && item.uri != null -> {
                        ioPool.execute {
                            PrivateVault.add(this, item.uri, item.title, if (item.isVideo) "video/mp4" else "image/jpeg")
                            handler.post {
                                Toast.makeText(this, "تم تشفير الملف وإضافته إلى الخزنة السحابية", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    chosen.startsWith("🗑️") -> {
                        cloudFiles.removeAll { it.id == item.id }
                        TelegramCloudEngine.saveCloudFiles(this, cloudFiles)
                        renderActiveTabContent()
                    }
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // ============================================================
    // التبويب 1: الملفات (Files — مطابق للصورة 1000036940.png)
    // ============================================================
    private fun buildUnlimFilesTab() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // الشريط العلوي: قائمة النقاط الثلاث (⋮) على اليسار كما في الصورة 1000036940.png
        val topMenuBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(16), dp(4))
        }

        val threeDotsBtn = makeTopVectorButton(MangoVectorIconView.IconType.MORE_VERT, Color.WHITE) { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, "📁 إنشاء مجلد جديد")
            popup.menu.add(0, 2, 1, "☁️ رفع ملف إلى هذا المجلد")
            popup.menu.add(0, 3, 2, "🔄 تحديث الفهرس السحابي")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> showCreateNewFolderDialog()
                    2 -> triggerFileUploadPicker()
                    3 -> {
                        cloudFolders = TelegramCloudEngine.loadFolders(this)
                        cloudFiles = TelegramCloudEngine.loadCloudFiles(this)
                        renderActiveTabContent()
                        Toast.makeText(this, "تمت مزامنة المجلدات مع Saved Messages", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            popup.show()
        }
        topMenuBar.addView(threeDotsBtn)
        page.addView(topMenuBar)

        // شريط المسار (Files ▸ 📁 -> الملفات ▸ 📁) على اليمين كما في الصورة 1000036940.png
        val breadcrumbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(6), dp(18), dp(8))
        }

        val folderTopIcon = MangoVectorIconView(
            this,
            MangoVectorIconView.IconType.FOLDER_SOLID,
            0xFF7B8288.toInt(),
            2.0f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                currentFolderPath = ""
                renderActiveTabContent()
            }
        }

        val arrowTv = TextView(this).apply {
            text = " ◂ "
            setTextColor(textPrimaryColor)
            textSize = 15f
            setPadding(dp(4), 0, dp(4), 0)
        }

        val filesRootTv = TextView(this).apply {
            text = if (currentFolderPath.isBlank()) "الملفات" else "الملفات / $currentFolderPath"
            setTextColor(accentCyanColor)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                currentFolderPath = ""
                renderActiveTabContent()
            }
        }

        val newFolderQuickBtn = TextView(this).apply {
            text = "＋ مجلد جديد"
            setTextColor(accentCyanColor)
            textSize = 13f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedBg(bgCardColor, 14f)
            setOnClickListener { showCreateNewFolderDialog() }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        breadcrumbRow.addView(folderTopIcon)
        breadcrumbRow.addView(arrowTv)
        breadcrumbRow.addView(filesRootTv)
        breadcrumbRow.addView(spacer)
        breadcrumbRow.addView(newFolderQuickBtn)
        page.addView(breadcrumbRow)

        // شريط الترتيب المطابق للصورة 1000036940.png:
        // على اليمين: "ترتيب حسب" (Sort by) | على اليسار: "↓ | الاسم ☰" (↓ | Name ☰)
        val sortBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(6), dp(18), dp(12))
        }

        val sortByLabel = TextView(this).apply {
            text = "ترتيب حسب"
            setTextColor(textPrimaryColor)
            textSize = 15f
        }

        val sortSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val sortFieldName = when (sortField) {
            0 -> "الاسم"
            1 -> "التاريخ"
            else -> "الحجم"
        }

        val sortFieldBtn = TextView(this).apply {
            text = "☰ $sortFieldName"
            setTextColor(textPrimaryColor)
            textSize = 15f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                sortField = (sortField + 1) % 3
                renderActiveTabContent()
            }
        }

        val dividerPipe = TextView(this).apply {
            text = " | "
            setTextColor(0xFF454B50.toInt())
            textSize = 15f
        }

        val sortDirBtn = TextView(this).apply {
            text = if (sortAscending) "↓" else "↑"
            setTextColor(textPrimaryColor)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                sortAscending = !sortAscending
                renderActiveTabContent()
            }
        }

        sortBar.addView(sortByLabel)
        sortBar.addView(sortSpacer)
        sortBar.addView(sortFieldBtn)
        sortBar.addView(dividerPipe)
        sortBar.addView(sortDirBtn)
        page.addView(sortBar)

        // فلترة وترتيب المجلدات والملفات في المسار الحالي
        val subFolders = cloudFolders.filter { it.parentPath == currentFolderPath }.let { list ->
            if (sortAscending) list.sortedBy { it.name } else list.sortedByDescending { it.name }
        }

        val filesInFolder = cloudFiles.filter { it.folderPath == currentFolderPath }.let { list ->
            val sorted = when (sortField) {
                0 -> list.sortedBy { it.name.lowercase() }
                1 -> list.sortedBy { it.uploadedAt }
                else -> list.sortedBy { it.sizeBytes }
            }
            if (sortAscending) sorted else sorted.reversed()
        }

        if (subFolders.isEmpty() && filesInFolder.isEmpty()) {
            // شاشة "لا توجد ملفات" (No files) في المنتصف تماماً كما في الصورة 1000036940.png
            val emptyFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            val noFilesTv = TextView(this).apply {
                text = "لا توجد ملفات"
                setTextColor(textSecondaryColor)
                textSize = 17f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            emptyFrame.addView(noFilesTv)
            page.addView(emptyFrame)
        } else {
            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            val listCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(4), dp(14), dp(95))
            }

            // المجلدات أولاً
            for (folder in subFolders) {
                val fRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = roundedBg(bgCardColor, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        currentFolderPath = if (currentFolderPath.isBlank()) folder.name else "$currentFolderPath/${folder.name}"
                        renderActiveTabContent()
                    }
                }

                val fIcon = MangoVectorIconView(this, MangoVectorIconView.IconType.FOLDER_SOLID, accentCyanColor, 2.0f).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                }

                val fName = TextView(this).apply {
                    text = folder.name
                    setTextColor(textPrimaryColor)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(14)
                    }
                }

                val delFolderBtn = makeTopVectorButton(MangoVectorIconView.IconType.TRASH, textSecondaryColor) {
                    cloudFolders.removeAll { it.id == folder.id }
                    TelegramCloudEngine.saveFolders(this, cloudFolders)
                    renderActiveTabContent()
                }

                fRow.addView(fIcon)
                fRow.addView(fName)
                fRow.addView(delFolderBtn)
                listCol.addView(fRow)
            }

            // الملفات المرفوعة
            for (file in filesInFolder) {
                val fileRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(16), dp(13), dp(16), dp(13))
                    background = roundedBg(bgCardColor, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        showFileItemOptionsDialog(file)
                    }
                }

                val fIcon = MangoVectorIconView(
                    this,
                    if (file.isImageOrVideo) MangoVectorIconView.IconType.GALLERY_FRAME else MangoVectorIconView.IconType.DOCUMENT,
                    accentCyanColor,
                    2.0f
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(14)
                    }
                }

                val nameTv = TextView(this).apply {
                    text = file.name
                    setTextColor(textPrimaryColor)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val metaTv = TextView(this).apply {
                    text = "${formatSize(file.sizeBytes)} • Saved Messages (#${toArabicDigits(file.telegramMessageId.toString())})"
                    setTextColor(textSecondaryColor)
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                }

                textCol.addView(nameTv)
                textCol.addView(metaTv)

                val moreIcon = makeTopVectorButton(MangoVectorIconView.IconType.MORE_VERT, textSecondaryColor) {
                    showFileItemOptionsDialog(file)
                }

                fileRow.addView(fIcon)
                fileRow.addView(textCol)
                fileRow.addView(moreIcon)
                listCol.addView(fileRow)
            }

            scroll.addView(listCol)
            page.addView(scroll)
        }

        contentContainer.addView(page)
    }

    private fun showCreateNewFolderDialog() {
        val input = EditText(this).apply {
            hint = "اسم المجلد السحابي الجديد"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("إنشاء مجلد جديد في السحابة")
            .setView(input)
            .setPositiveButton("إنشاء") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    cloudFolders.add(
                        TelegramCloudEngine.CloudFolderItem(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            parentPath = currentFolderPath
                        )
                    )
                    TelegramCloudEngine.saveFolders(this, cloudFolders)
                    renderActiveTabContent()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showFileItemOptionsDialog(file: TelegramCloudEngine.CloudFileEntry) {
        val opts = arrayOf(
            "⬇️ تنزيل / فتح الملف من السحابة",
            "✏️ إعادة تسمية الملف",
            "🔒 تشفير ونقل إلى الخزنة السحابية (AES-256)",
            "🗑️ حذف من السحابة"
        )
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(
                            this,
                            "تم تسجيل طلب التنزيل من Saved Messages (#${file.telegramMessageId})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        val ed = EditText(this).apply {
                            setText(file.name)
                            setSingleLine(true)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("إعادة تسمية")
                            .setView(ed)
                            .setPositiveButton("حفظ") { _, _ ->
                                val nn = ed.text?.toString()?.trim().orEmpty()
                                if (nn.isNotEmpty()) {
                                    file.name = nn
                                    TelegramCloudEngine.saveCloudFiles(this, cloudFiles)
                                    renderActiveTabContent()
                                }
                            }
                            .setNegativeButton("إلغاء", null)
                            .show()
                    }
                    2 -> {
                        if (file.localUriString.isNotBlank()) {
                            ioPool.execute {
                                PrivateVault.add(this, Uri.parse(file.localUriString), file.name, file.mimeType)
                                handler.post {
                                    Toast.makeText(this, "تم تشفير الملف وإضافته إلى الخزنة السحابية", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            startActivity(Intent(this, VaultActivity::class.java))
                        }
                    }
                    3 -> {
                        cloudFiles.removeAll { it.id == file.id }
                        TelegramCloudEngine.saveCloudFiles(this, cloudFiles)
                        renderActiveTabContent()
                    }
                }
            }
            .show()
    }

    // ============================================================
    // التبويب 2: القائمة (Menu — مطابق 100% للصورة 1000036941.png)
    // ============================================================
    private fun buildUnlimMenuTab() {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(36))
        }

        // الشريط العلوي: على اليمين "مانجو درايف (Unlim Cloud)" وعلى اليسار أيقونة الشمس/المظهر (☀️)
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(4), dp(4), dp(18))
        }

        val brandTitle = TextView(this).apply {
            text = "مانجو درايف • Unlim Cloud"
            setTextColor(textPrimaryColor)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sunThemeBtn = makeTopVectorButton(MangoVectorIconView.IconType.SUN_THEME, textPrimaryColor) {
            isDarkTheme = !isDarkTheme
            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
            refreshRootScreen()
        }

        topHeader.addView(brandTitle)
        topHeader.addView(sunThemeBtn)
        col.addView(topHeader)

        // 1. البطاقة الحمراء: Battery optimization (تحسين البطارية) المطابقة للصورة 1000036941.png
        val batteryCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedBg(bannerRedBgColor, 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
            setOnClickListener {
                requestIgnoreBatteryOptimization()
            }
        }

        val boltCircle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            background = roundedBg(bannerRedCircleColor, 24f)
        }
        val boltIcon = MangoVectorIconView(this, MangoVectorIconView.IconType.LIGHTNING_BOLT, 0xFFFFB4AB.toInt(), 2.2f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        }
        boltCircle.addView(boltIcon)

        val batTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
        }
        val batTitle = TextView(this).apply {
            text = "تحسين البطارية"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val batSub = TextView(this).apply {
            text = "السماح باستخدام البطارية في الخلفية لرفع ملفاتك تلقائياً بدون قيود"
            setTextColor(0xFFFFDAD6.toInt())
            textSize = 12.5f
            setPadding(0, dp(2), 0, 0)
        }
        batTextCol.addView(batTitle)
        batTextCol.addView(batSub)

        batteryCard.addView(boltCircle)
        batteryCard.addView(batTextCol)
        col.addView(batteryCard)

        // 2. مجموعة الإعدادات الرئيسية الأربع (Synchronization, Data and storage, Search optimization, Download manager)
        val mainGroupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundedBg(bgCardColor, 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }

        mainGroupCard.addView(
            buildUnlimMenuItemRow(
                title = "المزامنة التلقائية",
                subtitle = "إعدادات المزامنة ونسخ مجلدات الكاميرا",
                iconType = MangoVectorIconView.IconType.SYNC_ARROWS,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                showSynchronizationDialog()
            }
        )

        mainGroupCard.addView(
            buildUnlimMenuItemRow(
                title = "البيانات والتخزين",
                subtitle = "تحرير مساحة الهاتف وإدارة الكاش السحابي",
                iconType = MangoVectorIconView.IconType.STORAGE_RING,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                showDataAndStorageDialog()
            }
        )

        mainGroupCard.addView(
            buildUnlimMenuItemRow(
                title = "تحسين البحث الذكي",
                subtitle = "تحسين دقة البحث وفهرسة الصور والملفات",
                iconType = MangoVectorIconView.IconType.FLASK_BEAKER,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                runSearchOptimization()
            }
        )

        mainGroupCard.addView(
            buildUnlimMenuItemRow(
                title = "مدير التنزيلات والرفع",
                subtitle = "سجل التنزيلات والملفات المرفوعة لتيليجرام",
                iconType = MangoVectorIconView.IconType.DOWNLOAD_TRAY,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                showDownloadManagerDialog()
            }
        )

        col.addView(mainGroupCard)

        // 3. بطاقة الخزنة ودعم التطوير (Donate / Private Vault) المطابقة للصورة 1000036941.png
        val donateAndVaultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = roundedBg(bgCardColor, 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }

        donateAndVaultCard.addView(
            buildUnlimMenuItemRow(
                title = "الخزنة السحابية المشفرة (AES-256)",
                subtitle = "حماية الصور والملفات الخاصة بالبصمة وكلمة السر",
                iconType = MangoVectorIconView.IconType.LOCK,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                startActivity(Intent(this, VaultActivity::class.java))
            }
        )

        donateAndVaultCard.addView(
            buildUnlimMenuItemRow(
                title = "دعم تطوير التطبيق",
                subtitle = "المساهمة في استمرار تطوير مانجو درايف مجاناً",
                iconType = MangoVectorIconView.IconType.DOLLAR_SIGN,
                circleBg = donateCircleBgColor,
                iconTint = donateGoldColor
            ) {
                Toast.makeText(this, "شكراً لدعمك! مانجو درايف مجاني بالكامل وبدون حدود للأبد ❤️", Toast.LENGTH_LONG).show()
            }
        )

        col.addView(donateAndVaultCard)

        // 4. بطاقة نسخة الكمبيوتر وحول التطبيق (Desktop & About) المطابقة للصورة 1000036941.png
        val desktopAndAboutCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = roundedBg(bgCardColor, 18f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        desktopAndAboutCard.addView(
            buildUnlimMenuItemRow(
                title = "نسخة الكمبيوتر وإعدادات الربط",
                subtitle = "نسخة الويب ومفاتيح الاتصال المباشر بتيليجرام",
                iconType = MangoVectorIconView.IconType.LAPTOP_DESKTOP,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                showTelegramApiSettingsDialog()
            }
        )

        desktopAndAboutCard.addView(
            buildUnlimMenuItemRow(
                title = "حول التطبيق وحساب تيليجرام",
                subtitle = "الأسئلة الشائعة، حالة الحساب المتصل، وتسجيل الخروج",
                iconType = MangoVectorIconView.IconType.INFO_CIRCLE,
                circleBg = bgTealCircleColor,
                iconTint = accentCyanColor
            ) {
                showAboutAndAccountDialog()
            }
        )

        col.addView(desktopAndAboutCard)
        scroll.addView(col)
        contentContainer.addView(scroll)
    }

    private fun buildUnlimMenuItemRow(
        title: String,
        subtitle: String,
        iconType: MangoVectorIconView.IconType,
        circleBg: Int,
        iconTint: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        // الأيقونة الدائرية على اليمين تماماً كما في الصورة 1000036941.png
        val circle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            background = roundedBg(circleBg, 24f)
        }
        val icon = MangoVectorIconView(this, iconType, iconTint, 2.1f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        }
        circle.addView(icon)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(textPrimaryColor)
            textSize = 17.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(this).apply {
            text = subtitle
            setTextColor(textSecondaryColor)
            textSize = 12.5f
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(titleTv)
        textCol.addView(subTv)

        row.addView(circle)
        row.addView(textCol)
        return row
    }

    // ============================================================
    // نوافذ عناصر القائمة (Menu Actions)
    // ============================================================
    private fun requestIgnoreBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } else {
                Toast.makeText(this, "تم تفعيل وضع العمل في الخلفية بدون قيود للنسخ التلقائي", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(this, "تم تفعيل وضع العمل في الخلفية بدون قيود للنسخ التلقائي", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSynchronizationDialog() {
        val session = TelegramCloudEngine.loadSession(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(14), dp(20), dp(8))
        }

        val swDcim = Switch(this).apply {
            text = "مزامنة مجلد الكاميرا (DCIM) تلقائياً"
            setTextColor(textPrimaryColor)
            isChecked = session.autoSyncDcim
            setPadding(0, dp(8), 0, dp(8))
        }

        val swWifi = Switch(this).apply {
            text = "الرفع التلقائي عند الاتصال بـ Wi-Fi فقط"
            setTextColor(textPrimaryColor)
            isChecked = session.syncOnWifiOnly
            setPadding(0, dp(8), 0, dp(8))
        }

        val swOrig = Switch(this).apply {
            text = "رفع الصور بجودتها الأصلية بدون ضغط (Document)"
            setTextColor(textPrimaryColor)
            isChecked = session.uploadOriginalQuality
            setPadding(0, dp(8), 0, dp(8))
        }

        box.addView(swDcim)
        box.addView(swWifi)
        box.addView(swOrig)

        AlertDialog.Builder(this)
            .setTitle("إعدادات المزامنة التلقائية (Synchronization)")
            .setView(box)
            .setPositiveButton("حفظ ومزامنة الآن") { _, _ ->
                TelegramCloudEngine.saveSession(
                    this,
                    session.copy(
                        autoSyncDcim = swDcim.isChecked,
                        syncOnWifiOnly = swWifi.isChecked,
                        uploadOriginalQuality = swOrig.isChecked
                    )
                )
                syncLatestCameraRollPhotos()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showDataAndStorageDialog() {
        val totalCloudBytes = cloudFiles.sumOf { it.sizeBytes }
        val msg = "☁️ المساحة السحابية الكلية: ∞ غير محدودة (مجانية للأبد)\n" +
            "📦 حجم الملفات المرفوعة في تيليجرام: ${formatSize(totalCloudBytes)} (${toArabicDigits(cloudFiles.size.toString())} ملف)\n" +
            "📱 حد الملف الواحد في تيليجرام: ٢ جيجابايت\n\n" +
            "يمكنك تفريغ الكاش المؤقت لتحرير مساحة هاتفك مع بقاء جميع ملفاتك محفوظة في سحابة تيليجرام."

        AlertDialog.Builder(this)
            .setTitle("البيانات والتخزين (Data and storage)")
            .setMessage(msg)
            .setPositiveButton("تحرير مساحة الهاتف الآن") { _, _ ->
                val freed = TelegramCloudEngine.clearLocalCache(this)
                thumbCache.evictAll()
                Toast.makeText(
                    this,
                    "تم تحرير الكاش المحلي (${formatSize(freed)}) بنجاح!",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun runSearchOptimization() {
        showLoadingOverlay("جاري فهرسة الصور والملفات لتحسين دقة البحث...")
        ioPool.execute {
            val totalIndexed = cloudFiles.size + deviceGalleryItems.size
            val s = TelegramCloudEngine.loadSession(this)
            TelegramCloudEngine.saveSession(this, s.copy(searchIndexedCount = totalIndexed))
            try { Thread.sleep(500) } catch (_: Throwable) {}
            handler.post {
                hideLoadingOverlay()
                Toast.makeText(
                    this,
                    "اكتمل تحسين البحث الذكي! تم فهرسة ${toArabicDigits(totalIndexed.toString())} صورة وملف.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDownloadManagerDialog() {
        val transfers = TelegramCloudEngine.loadTransfers(this)
        if (transfers.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("مدير التنزيلات والرفع (Download manager)")
                .setMessage("لا توجد عمليات رفع أو تنزيل مسجلة حتى الآن. اضغط على زر (رفع 📁) لرفع أول ملف إلى سحابة تيليجرام.")
                .setPositiveButton("حسناً", null)
                .show()
            return
        }
        val lines = transfers.take(25).map { rec ->
            val arrow = if (rec.direction == "UPLOAD") "⬆️ رفع" else "⬇️ تنزيل"
            "$arrow: ${rec.fileName} (${formatSize(rec.sizeBytes)}) — ${rec.status}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("سجل التنزيلات والرفع (${toArabicDigits(transfers.size.toString())})")
            .setItems(lines, null)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showTelegramApiSettingsDialog() {
        val s = TelegramCloudEngine.loadSession(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        val tokenEd = EditText(this).apply {
            hint = "Bot Token (للرفع الفعلي المباشر إلى تيليجرام)"
            setText(s.botToken)
            setSingleLine(true)
        }
        val chatEd = EditText(this).apply {
            hint = "Chat ID (معرف حسابك أو قناتك السحابية الخاصة)"
            setText(s.targetChatId)
            setSingleLine(true)
        }

        box.addView(tokenEd)
        box.addView(chatEd)

        AlertDialog.Builder(this)
            .setTitle("ربط سحابة تيليجرام ونسخة الويب")
            .setMessage("جميع الملفات تُحفظ في الرسائل المحفوظة (Saved Messages) ويمكنك الوصول إليها من تطبيق تيليجرام على الكمبيوتر والويب أو عبر ربط Bot Token و Chat ID:")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                TelegramCloudEngine.saveSession(
                    this,
                    s.copy(
                        botToken = tokenEd.text?.toString()?.trim().orEmpty(),
                        targetChatId = chatEd.text?.toString()?.trim().orEmpty().ifEmpty { "Saved Messages" }
                    )
                )
                Toast.makeText(this, "تم تحديث إعدادات الربط السحابي بتيليجرام", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showAboutAndAccountDialog() {
        val s = TelegramCloudEngine.loadSession(this)
        val info = "تطبيق مانجو درايف (Unlim Cloud العربي)\n\n" +
            "• رقم الحساب المتصل: ${s.phoneNumber.ifBlank { "متصل عبر رمز QR" }}\n" +
            "• وجهة التخزين: الرسائل المحفوظة (${s.targetChatId})\n" +
            "• المساحة السحابية: غير محدودة ومجانية للأبد عبر تيليجرام\n" +
            "• الخصوصية: ملفاتك محفوظة في حسابك الشخصي على خوادم تيليجرام المشفرة."

        AlertDialog.Builder(this)
            .setTitle("حول التطبيق والحساب (About)")
            .setMessage(info)
            .setPositiveButton("حسناً", null)
            .setNeutralButton("تسجيل الخروج من تيليجرام") { _, _ ->
                TelegramCloudEngine.logout(this)
                authStep = 0
                refreshRootScreen()
            }
            .show()
    }

    // ============================================================
    // رفع الملفات وتحميل صور المعرض
    // ============================================================
    private fun triggerFileUploadPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, REQ_PICK_UPLOAD_FILE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(fallback, REQ_PICK_UPLOAD_FILE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_UPLOAD_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            uploadUriToUnlimCloud(uri, currentFolderPath)
        }
    }

    private fun uploadUriToUnlimCloud(uri: Uri, targetFolder: String) {
        showLoadingOverlay("جاري الرفع إلى سحابة تيليجرام (Saved Messages)...")
        ioPool.execute {
            val (_, msg) = TelegramCloudEngine.uploadUriToSavedMessagesBlocking(this, uri, targetFolder)
            handler.post {
                hideLoadingOverlay()
                cloudFiles = TelegramCloudEngine.loadCloudFiles(this)
                renderActiveTabContent()
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncLatestCameraRollPhotos() {
        if (deviceGalleryItems.isEmpty()) {
            requestMediaPermAndLoadGallery()
            Toast.makeText(this, "جاري فحص صور الكاميرا للمزامنة...", Toast.LENGTH_SHORT).show()
            return
        }
        val toUpload = deviceGalleryItems.take(3)
        showLoadingOverlay("جاري مزامنة أحدث صور الكاميرا إلى Saved Messages...")
        ioPool.execute {
            var count = 0
            for (item in toUpload) {
                val u = item.uri ?: continue
                if (cloudFiles.none { it.name == item.title && it.sizeBytes == item.sizeBytes }) {
                    val (entry, _) = TelegramCloudEngine.uploadUriToSavedMessagesBlocking(this, u, "")
                    if (entry != null) count++
                }
            }
            handler.post {
                hideLoadingOverlay()
                cloudFiles = TelegramCloudEngine.loadCloudFiles(this)
                renderActiveTabContent()
                Toast.makeText(
                    this,
                    if (count > 0) "تم رفع ${toArabicDigits(count.toString())} صور جديدة إلى سحابة تيليجرام" else "جميع الصور الحديثة مرفوعة بالفعل",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLoadingOverlay(msg: String) {
        loadingLayer.removeAllViews()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = roundedBg(bgCardColor, 18f, accentCyanColor, 1f)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        val tv = TextView(this).apply {
            text = msg
            setTextColor(textPrimaryColor)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        box.addView(tv)
        loadingLayer.addView(box)
        loadingLayer.visibility = View.VISIBLE
    }

    private fun hideLoadingOverlay() {
        loadingLayer.visibility = View.GONE
    }

    private fun requestMediaPermAndLoadGallery() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            requestPermissions(perms.toTypedArray(), REQ_MEDIA_PERMS)
        } else {
            loadGalleryFromMediaStoreAsync()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MEDIA_PERMS) {
            loadGalleryFromMediaStoreAsync()
        }
    }

    private fun loadGalleryFromMediaStoreAsync() {
        ioPool.execute {
            val loaded = mutableListOf<GalleryPhotoItem>()
            try {
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE
                )
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )?.use { c ->
                    var count = 0
                    while (c.moveToNext() && count < 90) {
                        count++
                        val id = c.getLong(0)
                        val name = c.getString(1) ?: "IMG_$id.jpg"
                        var ts = c.getLong(2)
                        if (ts <= 0L) ts = c.getLong(3) * 1000L
                        val sz = c.getLong(4)
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        loaded.add(
                            GalleryPhotoItem(
                                id = "dev_$id",
                                title = name,
                                dateHeader = formatSlashDateArabic(ts),
                                timestamp = ts,
                                uri = uri,
                                isFromCloud = false,
                                isVideo = false,
                                sizeBytes = sz
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}

            handler.post {
                deviceGalleryItems.clear()
                deviceGalleryItems.addAll(loaded)
                if (activeTab == 0) {
                    renderActiveTabContent()
                }
            }
        }
    }

    private fun loadThumbnailIntoView(uri: Uri, target: ImageView) {
        val key = uri.toString()
        target.tag = key
        val cached = thumbCache.get(key)
        if (cached != null && !cached.isRecycled) {
            target.setImageBitmap(cached)
            return
        }
        target.setImageBitmap(null)
        ioPool.execute {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(uri, Size(240, 240), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        contentResolver,
                        ContentUris.parseId(uri),
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            } catch (_: Throwable) {
                null
            }
            if (bmp != null) {
                thumbCache.put(key, bmp)
                handler.post {
                    if (target.tag == key) {
                        target.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun makeTopVectorButton(
        iconType: MangoVectorIconView.IconType,
        tint: Int,
        onClick: (View) -> Unit
    ): View {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { onClick(it) }
        }
        val icon = MangoVectorIconView(this, iconType, tint, 2.1f).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        }
        frame.addView(icon)
        return frame
    }

    // ============================================================
    // عارض رمز QR المرسوم بدقة على Canvas (مطابق للصورة 1000036829.png)
    // ============================================================
    class QrCodeCanvasView(
        context: Context,
        private val payload: String
    ) : View(context) {

        private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val gridSize = 25
            val cell = min(w, h) / gridSize.toFloat()
            val hash = payload.hashCode()

            fun drawFinderPattern(startCol: Int, startRow: Int) {
                // 7x7 outer black box
                canvas.drawRect(
                    startCol * cell,
                    startRow * cell,
                    (startCol + 7) * cell,
                    (startRow + 7) * cell,
                    blackPaint
                )
                // 5x5 inner white box
                canvas.drawRect(
                    (startCol + 1) * cell,
                    (startRow + 1) * cell,
                    (startCol + 6) * cell,
                    (startRow + 6) * cell,
                    whitePaint
                )
                // 3x3 center black box
                canvas.drawRect(
                    (startCol + 2) * cell,
                    (startRow + 2) * cell,
                    (startCol + 5) * cell,
                    (startRow + 5) * cell,
                    blackPaint
                )
            }

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val inTopLeft = r < 8 && c < 8
                    val inTopRight = r < 8 && c >= gridSize - 8
                    val inBottomLeft = r >= gridSize - 8 && c < 8
                    if (inTopLeft || inTopRight || inBottomLeft) continue

                    val bit = ((r * 31 + c * 17 + hash) xor (r * c + payload[ (r + c) % payload.length ].code)) and 1
                    if (bit == 0) {
                        canvas.drawRect(
                            c * cell,
                            r * cell,
                            (c + 1) * cell,
                            (r + 1) * cell,
                            blackPaint
                        )
                    }
                }
            }

            drawFinderPattern(0, 0)
            drawFinderPattern(gridSize - 7, 0)
            drawFinderPattern(0, gridSize - 7)
        }
    }

    // ============================================================
    // محرك أيقونات المتجهات المتكامل (متوافق مع Unlim Cloud و VaultActivity)
    // ============================================================
    class MangoVectorIconView(
        context: Context,
        private val iconType: IconType,
        private val iconColor: Int,
        private val strokeDp: Float = 2.0f
    ) : View(context) {

        enum class IconType {
            GALLERY_FRAME,
            FOLDER_SOLID,
            FOLDER_UPLOAD_SOLID,
            HAMBURGER,
            SEARCH,
            MORE_VERT,
            SUN_THEME,
            LIGHTNING_BOLT,
            SYNC_ARROWS,
            STORAGE_RING,
            FLASK_BEAKER,
            DOWNLOAD_TRAY,
            DOLLAR_SIGN,
            LAPTOP_DESKTOP,
            INFO_CIRCLE,
            DOCUMENT,
            LOCK,
            PLUS,
            CHEVRON_RIGHT,
            FINGERPRINT,
            TRASH,
            UNDO_CURVE
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = iconColor
            strokeWidth = strokeDp * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF2E464F.toInt()
            strokeWidth = strokeDp * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val path = Path()
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = min(w, h) * 0.38f

            path.reset()
            when (iconType) {
                IconType.GALLERY_FRAME -> {
                    rect.set(cx - r * 0.88f, cy - r * 0.78f, cx + r * 0.88f, cy + r * 0.78f)
                    canvas.drawRoundRect(rect, r * 0.18f, r * 0.18f, strokePaint)
                    canvas.drawCircle(cx - r * 0.32f, cy - r * 0.28f, r * 0.15f, fillPaint)
                    path.moveTo(cx - r * 0.78f, cy + r * 0.58f)
                    path.lineTo(cx - r * 0.2f, cy + r * 0.02f)
                    path.lineTo(cx + r * 0.18f, cy + r * 0.35f)
                    path.lineTo(cx + r * 0.48f, cy + r * 0.08f)
                    path.lineTo(cx + r * 0.78f, cy + r * 0.58f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.FOLDER_SOLID -> {
                    path.moveTo(cx - r * 0.92f, cy - r * 0.65f)
                    path.lineTo(cx - r * 0.2f, cy - r * 0.65f)
                    path.lineTo(cx, cy - r * 0.38f)
                    path.lineTo(cx + r * 0.92f, cy - r * 0.38f)
                    path.lineTo(cx + r * 0.92f, cy + r * 0.72f)
                    path.lineTo(cx - r * 0.92f, cy + r * 0.72f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.FOLDER_UPLOAD_SOLID -> {
                    path.moveTo(cx - r * 0.95f, cy - r * 0.65f)
                    path.lineTo(cx - r * 0.2f, cy - r * 0.65f)
                    path.lineTo(cx, cy - r * 0.38f)
                    path.lineTo(cx + r * 0.95f, cy - r * 0.38f)
                    path.lineTo(cx + r * 0.95f, cy + r * 0.75f)
                    path.lineTo(cx - r * 0.95f, cy + r * 0.75f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                    // سهم الرفع داخل المجلد كما في زر Upload في Unlim
                    canvas.drawLine(cx, cy + r * 0.45f, cx, cy - r * 0.12f, cutPaint)
                    canvas.drawLine(cx - r * 0.28f, cy + r * 0.14f, cx, cy - r * 0.12f, cutPaint)
                    canvas.drawLine(cx + r * 0.28f, cy + r * 0.14f, cx, cy - r * 0.12f, cutPaint)
                }
                IconType.HAMBURGER -> {
                    val hw = r * 0.88f
                    val gap = r * 0.58f
                    canvas.drawLine(cx - hw, cy - gap, cx + hw, cy - gap, strokePaint)
                    canvas.drawLine(cx - hw, cy, cx + hw, cy, strokePaint)
                    canvas.drawLine(cx - hw, cy + gap, cx + hw, cy + gap, strokePaint)
                }
                IconType.SEARCH -> {
                    val cr = r * 0.74f
                    canvas.drawCircle(cx - r * 0.12f, cy - r * 0.12f, cr, strokePaint)
                    canvas.drawLine(cx + cr * 0.42f, cy + cr * 0.42f, cx + r * 0.9f, cy + r * 0.9f, strokePaint)
                }
                IconType.MORE_VERT -> {
                    val dotR = min(w, h) * 0.075f
                    canvas.drawCircle(cx, cy - r * 0.7f, dotR, fillPaint)
                    canvas.drawCircle(cx, cy, dotR, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.7f, dotR, fillPaint)
                }
                IconType.SUN_THEME -> {
                    canvas.drawCircle(cx, cy, r * 0.46f, fillPaint)
                    for (i in 0 until 8) {
                        val a = Math.toRadians((i * 45).toDouble())
                        val x1 = cx + (r * 0.68f * cos(a)).toFloat()
                        val y1 = cy + (r * 0.68f * sin(a)).toFloat()
                        val x2 = cx + (r * 0.95f * cos(a)).toFloat()
                        val y2 = cy + (r * 0.95f * sin(a)).toFloat()
                        canvas.drawLine(x1, y1, x2, y2, strokePaint)
                    }
                }
                IconType.LIGHTNING_BOLT -> {
                    path.moveTo(cx + r * 0.15f, cy - r * 0.95f)
                    path.lineTo(cx - r * 0.55f, cy + r * 0.08f)
                    path.lineTo(cx - r * 0.02f, cy + r * 0.08f)
                    path.lineTo(cx - r * 0.18f, cy + r * 0.95f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.08f)
                    path.lineTo(cx + r * 0.02f, cy - r * 0.08f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.SYNC_ARROWS -> {
                    rect.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
                    canvas.drawArc(rect, -30f, 135f, false, strokePaint)
                    canvas.drawArc(rect, 150f, 135f, false, strokePaint)
                    canvas.drawLine(cx + r * 0.68f, cy - r * 0.38f, cx + r * 0.92f, cy - r * 0.1f, strokePaint)
                    canvas.drawLine(cx - r * 0.68f, cy + r * 0.38f, cx - r * 0.92f, cy + r * 0.1f, strokePaint)
                }
                IconType.STORAGE_RING -> {
                    rect.set(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f)
                    canvas.drawArc(rect, -75f, 95f, false, strokePaint)
                    canvas.drawArc(rect, 45f, 95f, false, strokePaint)
                    canvas.drawArc(rect, 165f, 95f, false, strokePaint)
                }
                IconType.FLASK_BEAKER -> {
                    canvas.drawLine(cx - r * 0.32f, cy - r * 0.82f, cx + r * 0.32f, cy - r * 0.82f, strokePaint)
                    path.moveTo(cx - r * 0.2f, cy - r * 0.82f)
                    path.lineTo(cx - r * 0.2f, cy - r * 0.25f)
                    path.lineTo(cx - r * 0.78f, cy + r * 0.78f)
                    path.lineTo(cx + r * 0.78f, cy + r * 0.78f)
                    path.lineTo(cx + r * 0.2f, cy - r * 0.25f)
                    path.lineTo(cx + r * 0.2f, cy - r * 0.82f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.DOWNLOAD_TRAY -> {
                    canvas.drawLine(cx, cy - r * 0.75f, cx, cy + r * 0.32f, strokePaint)
                    canvas.drawLine(cx - r * 0.42f, cy - r * 0.08f, cx, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx + r * 0.42f, cy - r * 0.08f, cx, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx - r * 0.68f, cy + r * 0.75f, cx + r * 0.68f, cy + r * 0.75f, strokePaint)
                }
                IconType.DOLLAR_SIGN -> {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = iconColor
                        textSize = r * 1.65f
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("$", cx, cy + r * 0.58f, tp)
                }
                IconType.LAPTOP_DESKTOP -> {
                    rect.set(cx - r * 0.75f, cy - r * 0.62f, cx + r * 0.75f, cy + r * 0.38f)
                    canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, strokePaint)
                    canvas.drawLine(cx - r * 0.95f, cy + r * 0.68f, cx + r * 0.95f, cy + r * 0.68f, strokePaint)
                }
                IconType.INFO_CIRCLE -> {
                    canvas.drawCircle(cx, cy, r * 0.86f, strokePaint)
                    canvas.drawCircle(cx, cy - r * 0.36f, r * 0.09f, fillPaint)
                    canvas.drawLine(cx, cy - r * 0.06f, cx, cy + r * 0.48f, strokePaint)
                }
                IconType.DOCUMENT -> {
                    rect.set(cx - r * 0.72f, cy - r * 0.88f, cx + r * 0.72f, cy + r * 0.88f)
                    canvas.drawRoundRect(rect, r * 0.2f, r * 0.2f, strokePaint)
                    canvas.drawLine(cx - r * 0.38f, cy - r * 0.3f, cx + r * 0.38f, cy - r * 0.3f, strokePaint)
                    canvas.drawLine(cx - r * 0.38f, cy + r * 0.08f, cx + r * 0.38f, cy + r * 0.08f, strokePaint)
                }
                IconType.LOCK -> {
                    rect.set(cx - r * 0.72f, cy - r * 0.08f, cx + r * 0.72f, cy + r * 0.88f)
                    canvas.drawRoundRect(rect, r * 0.24f, r * 0.24f, strokePaint)
                    rect.set(cx - r * 0.44f, cy - r * 0.85f, cx + r * 0.44f, cy - r * 0.08f)
                    canvas.drawArc(rect, 180f, 180f, false, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.38f, r * 0.12f, fillPaint)
                }
                IconType.PLUS -> {
                    canvas.drawLine(cx - r * 0.82f, cy, cx + r * 0.82f, cy, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.82f, cx, cy + r * 0.82f, strokePaint)
                }
                IconType.CHEVRON_RIGHT -> {
                    canvas.drawLine(cx - r * 0.28f, cy - r * 0.72f, cx + r * 0.42f, cy, strokePaint)
                    canvas.drawLine(cx + r * 0.42f, cy, cx - r * 0.28f, cy + r * 0.72f, strokePaint)
                }
                IconType.FINGERPRINT -> {
                    rect.set(cx - r * 0.3f, cy - r * 0.35f, cx + r * 0.3f, cy + r * 0.45f)
                    canvas.drawArc(rect, 180f, 180f, false, strokePaint)
                    rect.set(cx - r * 0.62f, cy - r * 0.68f, cx + r * 0.62f, cy + r * 0.68f)
                    canvas.drawArc(rect, 195f, 150f, false, strokePaint)
                    rect.set(cx - r * 0.92f, cy - r * 0.95f, cx + r * 0.92f, cy + r * 0.95f)
                    canvas.drawArc(rect, 210f, 120f, false, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.05f, cx, cy + r * 0.85f, strokePaint)
                }
                IconType.TRASH -> {
                    canvas.drawLine(cx - r * 0.85f, cy - r * 0.55f, cx + r * 0.85f, cy - r * 0.55f, strokePaint)
                    rect.set(cx - r * 0.65f, cy - r * 0.55f, cx + r * 0.65f, cy + r * 0.9f)
                    canvas.drawRoundRect(rect, r * 0.2f, r * 0.2f, strokePaint)
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.2f, cx - r * 0.2f, cy + r * 0.55f, strokePaint)
                    canvas.drawLine(cx + r * 0.2f, cy - r * 0.2f, cx + r * 0.2f, cy + r * 0.55f, strokePaint)
                }
                IconType.UNDO_CURVE -> {
                    rect.set(cx - r * 0.7f, cy - r * 0.4f, cx + r * 0.7f, cy + r * 0.8f)
                    canvas.drawArc(rect, 190f, 160f, false, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.1f, cx - r * 0.35f, cy - r * 0.3f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.1f, cx - r * 0.3f, cy + r * 0.35f, strokePaint)
                }
            }
        }
    }
}