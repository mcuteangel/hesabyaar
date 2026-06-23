# حساب‌یار (Hesabyar) 💰

> **اپلیکیشن مدیریت مالی شخصی هوشمند برای فارسی‌زبانان**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-1.6+-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Offline-First](https://img.shields.io/badge/Offline--First-32CD32?style=for-the-badge)](https://en.wikipedia.org/wiki/Offline-first)

---

## 📌 درباره حساب‌یار

**حساب‌یار** یک اپلیکیشن **مدیریت مالی شخصی** با تمرکز بر **کاربران فارسی‌زبان** است که با استفاده از **هوش مصنوعی** و **جریان کاری سنتی حسابداری**، مدیریت امور مالی را آسان‌تر و هوشمندتر می‌کند.

### 🎯 چشم‌انداز پروژه
> "کاربران باید زمان کمتری را صرف مدیریت امور مالی خود کنند و زمان بیشتری را به درک و تحلیل آن اختصاص دهند."

**جریان اصلی:**
`نیات کاربر → زبان طبیعی/صوت → درک هوش مصنوعی → رکوردهای مالی ساختاریافته → بینش‌ها و گزارش‌ها`

---

## ✨ ویژگی‌ها

### 🔹 ویژگی‌های اصلی

| ویژگی | توضیح | وضعیت |
|--------|--------|--------|
| **مدیریت تراکنش‌ها** | ثبت درآمد و هزینه با جزئیات کامل | ✅ پیاده‌سازی شده |
| **هوش مصنوعی** | تحلیل زبان طبیعی فارسی برای ثبت خودکار تراکنش‌ها | ✅ پیاده‌سازی شده |
| **پشتیبانی آفلاین** | کار کامل بدون نیاز به اینترنت | ✅ پیاده‌سازی شده |
| **تقویم شمسی** | نمایش تاریخ‌ها بر اساس تقویم جلالی | ✅ پیاده‌سازی شده |
| **RTL کامل** | پشتیبانی کامل از چیدمان راست‌به‌چپ | ✅ پیاده‌سازی شده |
| **چند ارائه‌دهنده AI** | پشتیبانی از Gemini، OpenRouter، OpenAI | ✅ پیاده‌سازی شده |
| **مدیریت وام و اقساط** | پیگیری وام‌ها و پرداخت‌های دوره‌ای | 🚧 در حال توسعه |
| **گزارش‌های مالی** | نمودارها و تحلیل‌های مالی پیشرفته | 🚧 در حال توسعه |
| **پشتیبان‌گیری** | ذخیره و بازیابی داده‌ها | 📋 برنامه‌ریزی شده |

### 🔹 اصول طراحی

- **📱 Persian-First UX**: تمام تجربه کاربری برای فارسی‌زبانان طراحی شده است
- **🔒 حریم خصوصی**: داده‌های مالی **هرگز** بدون رضایت کاربر آپلود نمی‌شوند
- **💾 مالکیت داده**: کاربر ۱۰۰% مالک داده‌های مالی خود است
- **⚡ دقت مالی**: استفاده از `Long` یا `BigDecimal` برای محاسبات (ممنوعیت `Float`/`Double`)

---

## 🚀 شروع سریع

### 📋 پیش‌نیازها

- [Android Studio](https://developer.android.com/studio) (نسخه ۲۰۲۳.۲ یا بالاتر)
- [Java JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- دستگاه اندروید با نسخه **Android 8.0 (API 26)** یا بالاتر

### 🛠️ نصب و راه‌اندازی

#### ۱. کلون کردن مخزن

```bash
git clone https://github.com/mcuteangel/hesanyaar.git
cd hesanyaar
```

#### ۲. باز کردن پروژه در Android Studio

1. Android Studio را باز کنید
2. روی **Open** کلیک کنید
3. پوشه پروژه `hesanyaar` را انتخاب کنید
4. اجازه دهید Android Studio نا سازگاری‌ها را اصلاح کند

#### ۳. تنظیم متغیرهای محیطی

فایل `.env` در ریشه پروژه ایجاد کنید:

```bash
# کپی کردن فایل نمونه
cp .env.example .env
```

سپس مقادیر زیر را پر کنید:

```env
# فایل .env
GEMINI_API_KEY=your_gemini_api_key_here
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

> ⚠️ **هشدار:** هرگز فایل `.env` یا کلیدهای API را در Git commit نکنید!

#### ۴. ایجاد Keystore برای امضا (اختیاری - برای Release)

اگر می‌خواهید نسخه Release بسازید، ابتدا یک keystore ایجاد کنید:

```bash
./gradlew generateKeystore
```

برای بررسی صحت تنظیمات امضا:

```bash
./gradlew checkSigningConfig
```

> 📌 **نکته:** برای اجرای debug، نیازی به تنظیم امضا نیست. فقط فایل `.env` با `GEMINI_API_KEY` کافی است.

#### ۵. اجرا کردن اپلیکیشن

- روی دکمه **Run** (▶️) در Android Studio کلیک کنید
- یا از خط فرمان:

```bash
./gradlew installDebug
```

---

## 🏗️ معماری پروژه

### 📐 سبک معماری

```
UI Layer (Jetpack Compose)
    ↓
ViewModel Layer (State Holder)
    ↓
Use Case Layer (Business Logic)
    ↓
Repository Layer (Data Access)
    ↓
Data Source Layer (Room Database / Network)
```

### 🗂️ ساختار پوشه‌ها

```
hesanyaar/
├── AGENTS.md              # مستندات معماری و اصول
├── README.md              # این فایل
├── .env.example           # نمونه فایل محیطی
├── .gitignore             # فایل‌های نادیده گرفته شده
├── build.gradle.kts       # تنظیمات اصلی Gradle
├── settings.gradle.kts    # تنظیمات پروژه
├── gradle.properties      # خواص Gradle
├── gradlew                # اسکریپت Gradle (Unix)
├── gradlew.bat            # اسکریپت Gradle (Windows)
├── metadata.json          # متادیتای پروژه
├── app/
│   ├── build.gradle.kts   # تنظیمات ماژول app
│   ├── proguard-rules.pro # قواعد ProGuard
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/io/github/mojri/hesabyar/
│       │   │   ├── MainActivity.kt      # اکتیویتی اصلی
│       │   │   ├── api/                  # لایه API و AI
│       │   │   │   ├── AiProvider.kt     # اینترفیس ارائه‌دهنده AI
│       │   │   │   ├── AiProviderConfig.kt
│       │   │   │   ├── GeminiProvider.kt
│       │   │   │   └── OpenRouterProvider.kt
│       │   │   ├── data/                 # لایه داده
│       │   │   │   ├── model/            # مدل‌های داده
│       │   │   │   ├── repository/       # Repositoryها
│       │   │   │   ├── datasource/       # منابع داده
│       │   │   │   └── database/         # پایگاه داده Room
│       │   │   └── ui/                   # لایه UI
│       │   │       ├── theme/           # تم و استایل‌ها
│       │   │       ├── screen/          # صفحه‌ها
│       │   │       ├── component/       # کامپوننت‌های قابل استفاده مجدد
│       │   │       └── viewmodel/        # ViewModelها
│       │   └── res/                      # منابع
│       │       ├── drawable/            # تصاویر
│       │       ├── layout/              # Layoutها (XML)
│       │       ├── values/              # مقادیر (strings, colors, etc.)
│       │       └── ...
│       └── test/                         # تست‌ها
│           └── ...
└── docs/                           # مستندات
    └── ...
```

### 🔧 فناوری‌های استفاده شده

| لایه | فناوری | نسخه |
|------|---------|-------|
| **زبان** | Kotlin | 1.9+ |
| **UI Framework** | Jetpack Compose | 1.6+ |
| **Architecture** | Clean Architecture + MVVM | - |
| **Dependency Injection** | Hilt | 2.48 |
| **Database** | Room | 2.6.1 |
| **Coroutines** | Kotlin Coroutines + Flow | 1.7.3 |
| **Networking** | Retrofit | 2.9.0 |
| **AI Providers** | Gemini, OpenRouter, OpenAI | - |
| **Build System** | Gradle | 8.3 |
| **Testing** | JUnit, MockK, Turbine | - |

---

## 🤖 سیستم هوش مصنوعی

### 🔹 ارائه‌دهندگان پشتیبانی شده

| ارائه‌دهنده | وضعیت | توضیح |
|-------------|--------|--------|
| **Gemini** | ✅ فعال | ارائه‌دهنده پیش‌فرض |
| **OpenRouter** | ✅ فعال | پشتیبانی از مدل‌های مختلف |
| **OpenAI** | 🚧 در حال توسعه | سازگاری با API OpenAI |
| **Local LLM** | 📋 برنامه‌ریزی شده | اجرای مدل‌ها روی دستگاه |

### 🔹 ویژگی‌های AI

- **تجزیه و تحلیل زبان طبیعی فارسی** برای ثبت خودکار تراکنش‌ها
- **تشخیص خودکار نوع تراکنش** (درآمد/هزینه)
- **پشتیبانی از ورودی صوتی** (در آینده)
- **حالت آفلاین** برای پارسر زبان طبیعی
- **کش مدل‌ها** برای بهبود عملکرد

### 🔹 تنظیمات AI

اپلیکیشن از **سیستم چند پیکربندی** پشتیبانی می‌کند:
- اضافه کردن، ویرایش، و حذف پیکربندی‌ها
- انتخاب پیکربندی فعال
- تغییر بین حالت **آنلاین/آفلاین**

---

## 📊 مدل‌های داده

### 🔹 Transaction (تراکنش)

```kotlin
data class Transaction(
    val id: Long,
    val title: String,          // عنوان تراکنش
    val description: String?,   // توضیحات
    val amount: Long,           // مبلغ (به ریال)
    val type: TransactionType, // درآمد یا هزینه
    val category: Category,    // دسته‌بندی
    val date: LocalDate,       // تاریخ (شمسی)
    val createdAt: Instant,    // زمان ایجاد
    val updatedAt: Instant?    // زمان آخرین آپدیت
)
```

### 🔹 Loan (وام)

```kotlin
data class Loan(
    val id: Long,
    val title: String,         // عنوان وام
    val amount: Long,          // مبلغ اصلی
    val interestRate: Long?,   // نرخ سود (درصد)
    val startDate: LocalDate,  // تاریخ شروع
    val endDate: LocalDate?,   // تاریخ سررسید
    val type: LoanType,        // دریافت/پرداخت
    val status: LoanStatus,    // فعال/بسته
    val notes: String?         // یادداشت‌ها
)
```

### 🔹 Installment (قسط)

```kotlin
data class Installment(
    val id: Long,
    val loanId: Long,          // مرجع به وام
    val amount: Long,          // مبلغ قسط
    val dueDate: LocalDate,    // تاریخ سررسید
    val status: InstallmentStatus, // پرداخت شده/نشده
    val paidAt: Instant?       // زمان پرداخت
)
```

---

## 🎨 طراحی و تجربه کاربری

### 🔹 اصول طراحی

- **Material Design 3** (Material You)
- **پشتیبانی کامل RTL** (راست‌به‌چپ)
- **Dark Mode** کامل
- **Dynamic Color** برای Android 12+
- **Accessibility** کامل (TalkBack، اندازه فونت قابل تغییر)

### 🔹 رنگ‌ها

| رنگ | کد | استفاده |
|------|-----|----------|
| Primary | `#625BFF` | دکمه‌ها و اکسان‌ها |
| Secondary | `#03DAC6` | عناصر ثانویه |
| Error | `#B00020` | خطاها |
| Success | `#388E3C` | موفقیت‌ها |

### 🔹 تایپوگرافی

| استایل | فونت | اندازه |
|--------|------|--------|
| Headline1 | Vazirmatn | 32sp |
| Headline2 | Vazirmatn | 28sp |
| Title | Vazirmatn | 24sp |
| Body | Vazirmatn | 16sp |
| Caption | Vazirmatn | 12sp |

---

## 🔐 امنیت و حریم خصوصی

### 🔹 اصول امنیتی

✅ **EncryptedSharedPreferences** برای ذخیره کلیدهای API
✅ **Android Keystore** برای مدیریت کلیدها
✅ **ممنوعیت hardcode کردن کلیدها** - تمام رمزها از فایل `.env` یا متغیرهای محیطی خوانده می‌شوند
✅ **امضای اپلیکیشن** با استفاده از Secrets Gradle Plugin
✅ **داده‌های مالی روی دستگاه کاربر** (بدون آپلود خودکار)
✅ **پشتیبان‌گیری محلی** با امکان بازیابی

### 🔹 تنظیم امضای اپلیکیشن

امضای اپلیکیشن از طریق فایل `.env` یا متغیرهای محیطی پیکربندی می‌شود:

1. فایل `.env` را از `.env.example` کپی کنید
2. مقادیر `KEYSTORE_PASSWORD`، `KEY_ALIAS`، و `KEY_PASSWORD` را تنظیم کنید
3. فایل keystore (`my-upload-key.jks`) را در ریشه پروژه قرار دهید

```bash
# ایجاد keystore جدید
./gradlew generateKeystore

# بررسی صحت تنظیمات
./gradlew checkSigningConfig
```

> ⚠️ فایل `.env`، `*.jks`، و `*.keystore` به صورت خودکار از Git نادیده گرفته می‌شوند.

### 🔹 سیاست حریم خصوصی

- **داده‌های مالی** هرگز بدون رضایت صریح کاربر آپلود نمی‌شوند
- **کلیدهای API** فقط روی دستگاه کاربر ذخیره می‌شوند
- **لاگ‌ها** حاوی اطلاعات حساس نیستند
- **پشتیبان‌ها** به صورت محلی و رمزنگاری شده ذخیره می‌شوند

---

## 🤝 مشارکت

ما از مشارکت‌ها استقبال می‌کنیم! لطفاً قبل از ارسال Pull Request، موارد زیر را رعایت کنید:

### 📋 راهنمای مشارکت

1. **Fork** کردن مخزن
2. ایجاد یک **Branch** جدید:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. اعمال تغییرات خود
4. **Test** کردن کد
5. **Commit** کردن تغییرات با پیام واضح:
   ```bash
   git commit -m "feat: add new feature"
   ```
6. **Push** کردن به Branch خود
7. ایجاد **Pull Request** به Branch `main`

### 🔹 قواعد کد

- رعایت **اصول SOLID**
- رعایت **Clean Code**
- استفاده از **Kotlin** به عنوان زبان اصلی
- **ممنوعیت** استفاده از `Double`/`Float` برای مقادیر مالی
- **ممنوعیت** حذف عملکرد Offline یا تقویم شمسی
- **ممنوعیت** ایجاد Migrationهای مخرب در Room

### 🔹 بررسی قبل از تغییر

قبل از اعمال هر تغییری، این سوال‌ها را از خود بپرسید:

1. ✅ آیا این تغییر **پشتیبانی آفلاین** را خراب می‌کند؟
2. ✅ آیا این تغییر **تقویم شمسی** را خراب می‌کند؟
3. ✅ آیا این تغییر **دقت مالی** را به خطر می‌اندازد؟
4. ✅ آیا این تغییر نیاز به **Migration پایگاه داده** دارد؟
5. ✅ آیا **سازگاری با پشتیبان‌های محلی** حفظ می‌شود؟

---

## 📜 لایسنس

این پروژه تحت **لایسنس MIT** منتشر شده است. برای جزئیات بیشتر، فایل [LICENSE](LICENSE) را مشاهده کنید.

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 📞 تماس

| نوع | اطلاعات |
|------|----------|
| **GitHub** | [@mcuteangel](https://github.com/mcuteangel) |
| **پروژه در GitHub** | [hesanyaar](https://github.com/mcuteangel/hesanyaar) |
| **اپلیکیشن در AI Studio** | [Hesabyar](https://ai.studio/apps/82916d23-0a55-49e7-9dde-426a0bd8f8fa) |

---

## 🙏 قدردانی

- از [Google AI Studio](https://ai.studio/) برای میزبانی اولیه پروژه
- از [Gemini](https://ai.google.dev/) برای ارائه خدمات هوش مصنوعی
- از تمام توسعه‌دهندگان و مشارکت‌کنندگان آینده

---

## 📅 تاریخچه نسخه‌ها

| نسخه | تاریخ | توضیحات |
|-------|--------|----------|
| 0.1.0 | 2026-06-22 | نسخه اولیه با معماری پایه و سیستم AI چند ارائه‌دهنده |

---

> **⭐ اگر این پروژه را مفید می‌دانید، لطفاً یک ستاره ⭐ به آن بدهید!**

> **🐛 برای گزارش باگ‌ها و پیشنهادات، یک Issue جدید ایجاد کنید.**

> **💬 برای سوال‌ها و بحث‌ها، به بخش Discussions مراجعه کنید.**

---

*ساخت با ❤️ برای جامعه فارسی‌زبان* 
