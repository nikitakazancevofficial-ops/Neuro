package com.kazancev.ai_chat_companion.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    Russian("ru", "Русский", "Russian"),
    English("en", "English", "English"),
    Ukrainian("uk", "Українська", "Ukrainian"),
    German("de", "Deutsch", "German"),
    Spanish("es", "Español", "Spanish"),
    French("fr", "Français", "French"),
    Italian("it", "Italiano", "Italian"),
    Portuguese("pt", "Português", "Portuguese"),
    Polish("pl", "Polski", "Polish"),
    Turkish("tr", "Türkçe", "Turkish"),
    Chinese("zh", "中文", "Chinese"),
    Japanese("ja", "日本語", "Japanese");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: Russian
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Russian }
val LocalSetAppLanguage = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }

private const val LANGUAGE_PREFS = "neuro_language"
private const val LANGUAGE_KEY = "selected_language"

fun loadAppLanguage(context: Context): AppLanguage =
    AppLanguage.fromCode(
        context.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
    )

private fun saveAppLanguage(context: Context, language: AppLanguage) {
    context.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LANGUAGE_KEY, language.code)
        .apply()
}

@Composable
fun NeuroLanguageHost(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var language by rememberSaveable { mutableStateOf(loadAppLanguage(context)) }
    val updateLanguage = remember(context) {
        { nextLanguage: AppLanguage ->
            saveAppLanguage(context, nextLanguage)
            language = nextLanguage
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalSetAppLanguage provides updateLanguage,
        content = content
    )
}

@Composable
fun tr(russian: String): String = LocalAppLanguage.current.translate(russian)

fun AppLanguage.translate(russian: String): String {
    if (this == AppLanguage.Russian) return russian
    val translations = when (this) {
        AppLanguage.Ukrainian -> UKRAINIAN_TRANSLATIONS
        else -> ENGLISH_TRANSLATIONS
    }
    return translations[russian] ?: russian
}

private val ENGLISH_TRANSLATIONS = mapOf(
    "С возвращением" to "Welcome back",
    "Войдите, чтобы продолжить общение" to "Sign in to continue chatting",
    "Пароль" to "Password",
    "Войти" to "Sign in",
    "Нет аккаунта? Создать" to "No account? Create one",
    "Создание аккаунта" to "Create account",
    "Один шаг до нового диалога" to "One step away from a new conversation",
    "Повторите пароль" to "Repeat password",
    "Зарегистрироваться" to "Sign up",
    "Уже есть аккаунт? Войти" to "Already have an account? Sign in",
    "Аккаунт создан" to "Account created",
    "Новый чат" to "New chat",
    "Нажмите +, чтобы начать новый чат" to "Tap + to start a new chat",
    "Чат" to "Chat",
    "Настроить Neuro" to "Customize Neuro",
    "Персонализация" to "Personalization",
    "Память" to "Memory",
    "Язык" to "Language",
    "Язык приложения и ответов Neuro" to "App and Neuro response language",
    "Выбранный язык применяется сразу ко всему интерфейсу и следующим ответам Neuro." to "The selected language is applied immediately to the interface and future Neuro responses.",
    "Приложения" to "Apps",
    "Учётная запись" to "Account",
    "Электронная почта" to "Email",
    "Подписка" to "Subscription",
    "Восстановить покупки" to "Restore purchases",
    "Перейти на Neuro Pro" to "Upgrade to Neuro Pro",
    "Тема" to "Theme",
    "Внешний вид" to "Appearance",
    "Системный" to "System",
    "Светлый" to "Light",
    "Тёмный" to "Dark",
    "Акцентный цвет" to "Accent color",
    "Фиолетовый" to "Purple",
    "Локальный AI" to "Local AI",
    "Локальное выполнение" to "Local execution",
    "LM Studio и генерация работают на вашем ПК" to "LM Studio and generation run on your PC",
    "Приватный режим" to "Private mode",
    "Данные остаются в вашей локальной сети" to "Data stays on your local network",
    "Включено" to "Enabled",
    "Локальные интеграции" to "Local integrations",
    "Подключения для вашего личного пространства" to "Connections for your personal workspace",
    "Подключённые сервисы" to "Connected services",
    "Neuro может получать доступ к выбранным вами локальным сервисам. Все подключения остаются под вашим контролем." to "Neuro can access the local services you choose. Every connection stays under your control.",
    "Добавить интеграцию" to "Add integration",
    "Локальная память" to "Local memory",
    "Настройки приложения" to "App settings",
    "Общие" to "General",
    "Уведомления" to "Notifications",
    "Голос" to "Voice",
    "Безопасность" to "Security",
    "Элементы управления данными" to "Data controls",
    "Родительский контроль" to "Parental controls",
    "Доверенный контакт" to "Trusted contact",
    "Помощь" to "Help",
    "Сообщить о проблеме\nв приложении" to "Report an app issue",
    "Справочный центр" to "Help center",
    "Сведения" to "About",
    "Выйти" to "Sign out",
    "раздел готовится" to "section is coming soon",
    "Спасибо за оценку" to "Thanks for the feedback",
    "Учту при следующих генерациях" to "I will keep that in mind for future generations",
    "Изображение сохранено в галерею" to "Image saved to gallery",
    "Не удалось сохранить изображение" to "Could not save the image",
    "Не удалось подготовить изображение" to "Could not prepare the image",
    "Сохранить" to "Save",
    "Назад" to "Back",
    "Закрыть" to "Close",
    "Базовый стиль и тон" to "Base style and tone",
    "Дружелюбный" to "Friendly",
    "Деловой" to "Professional",
    "Лаконичный" to "Concise",
    "Творческий" to "Creative",
    "Доброжелательность" to "Warmth",
    "Энтузиазм" to "Enthusiasm",
    "Заголовки и списки" to "Headings and lists",
    "Эмодзи" to "Emoji",
    "Более" to "More",
    "По умолчанию" to "Default",
    "Менее" to "Less",
    "Быстрые ответы" to "Fast answers",
    "Иногда Neuro может использовать общие знания, чтобы отвечать быстрее. Персонализация применяется к следующим ответам." to "Neuro may sometimes use general knowledge to respond faster. Personalization applies to future responses.",
    "Пользовательские инструкции" to "Custom instructions",
    "Ссылаться на историю чата" to "Reference chat history",
    "Позволяет Neuro учитывать недавние чаты в ответах." to "Allows Neuro to consider recent chats in responses.",
    "Ссылаться на сохранённую\nпамять" to "Reference saved\nmemory",
    "Сохранённая память" to "Saved memory",
    "Позволяет Neuro сохранять важные факты и использовать память при ответе." to "Allows Neuro to save important facts and use memory in responses.",
    "Здесь можно посмотреть и удалить факты, которые Neuro запомнил о вас." to "View and remove facts that Neuro remembers about you.",
    "Ваш псевдоним" to "Your nickname",
    "Ваша профессия" to "Your profession",
    "Больше о вас" to "More about you",
    "Имя" to "Name",
    "Инженер, студент, пр." to "Engineer, student, etc.",
    "Пока здесь пусто" to "Nothing here yet",
    "Включённые приложения" to "Enabled apps",
    "Просмотр приложений" to "Browse apps",
    "Neuro может получать доступ к информации из подключённых приложений. Ваши разрешения всегда соблюдаются." to "Neuro can access information from connected apps. Your permissions are always respected.",
    "Спросить Neuro" to "Ask Neuro",
    "Думаю" to "Thinking",
    "Думал" to "Thought for",
    "с" to "s",
    "Проверяю результат" to "Checking the result",
    "Не удалось создать изображение" to "Could not create the image",
    "Генерация не завершилась" to "Generation did not finish",
    "Интеллект" to "Intelligence",
    "Модель" to "Model",
    "Для повседневных чатов" to "For everyday chats",
    "Для решения сложных вопросов" to "For solving complex questions",
    "Уровень усилий Thinking" to "Thinking effort level",
    "Стандартный" to "Standard",
    "Расширенное" to "Extended",
    "Используется 5.5 Thinking" to "Using 5.5 Thinking",
    "Редактировать" to "Edit",
    "Поделиться" to "Share",
    "Скачать" to "Download",
    "Ещё" to "More",
    "Хороший ответ" to "Good response",
    "Плохой ответ" to "Bad response",
    "Нравится" to "Like",
    "Не нравится" to "Dislike",
    "Изображения" to "Images",
    "Библиотека" to "Library",
    "Недавнее" to "Recent",
    "Приветствие в чате" to "Chat welcome",
    "Инвестиции" to "Investments",
    "Поиск" to "Search",
    "Очистить" to "Clear",
    "Добавить" to "Add",
    "Отправить" to "Send",
    "Стоп" to "Stop",
    "Создать изображение" to "Create image",
    "Создать любое изображение" to "Create any image",
    "Глубокое исследование" to "Deep research",
    "Получить подробный отчёт" to "Get a detailed report",
    "Поиск в сети" to "Web search",
    "Искать актуальные новости и информацию" to "Find current news and information",
    "Режим агента" to "Agent mode",
    "Делает работу за вас" to "Works on tasks for you",
    "Добавить файлы" to "Add files",
    "Анализ или краткое изложение" to "Analyze or summarize",
    "Все фотографии" to "All photos"
)

private val UKRAINIAN_TRANSLATIONS = mapOf(
    "С возвращением" to "З поверненням",
    "Войдите, чтобы продолжить общение" to "Увійдіть, щоб продовжити спілкування",
    "Пароль" to "Пароль",
    "Войти" to "Увійти",
    "Нет аккаунта? Создать" to "Немає акаунта? Створити",
    "Создание аккаунта" to "Створення акаунта",
    "Один шаг до нового диалога" to "Один крок до нового діалогу",
    "Повторите пароль" to "Повторіть пароль",
    "Зарегистрироваться" to "Зареєструватися",
    "Уже есть аккаунт? Войти" to "Вже є акаунт? Увійти",
    "Аккаунт создан" to "Акаунт створено",
    "Новый чат" to "Новий чат",
    "Нажмите +, чтобы начать новый чат" to "Натисніть +, щоб почати новий чат",
    "Чат" to "Чат",
    "Настроить Neuro" to "Налаштувати Neuro",
    "Персонализация" to "Персоналізація",
    "Память" to "Пам'ять",
    "Язык" to "Мова",
    "Язык приложения и ответов Neuro" to "Мова застосунку та відповідей Neuro",
    "Выбранный язык применяется сразу ко всему интерфейсу и следующим ответам Neuro." to "Вибрана мова одразу застосовується до інтерфейсу та наступних відповідей Neuro.",
    "Приложения" to "Застосунки",
    "Учётная запись" to "Обліковий запис",
    "Электронная почта" to "Електронна пошта",
    "Подписка" to "Підписка",
    "Восстановить покупки" to "Відновити покупки",
    "Перейти на Neuro Pro" to "Перейти на Neuro Pro",
    "Тема" to "Тема",
    "Внешний вид" to "Вигляд",
    "Системный" to "Системний",
    "Светлый" to "Світлий",
    "Тёмный" to "Темний",
    "Акцентный цвет" to "Акцентний колір",
    "Фиолетовый" to "Фіолетовий",
    "Локальный AI" to "Локальний AI",
    "Локальное выполнение" to "Локальне виконання",
    "LM Studio и генерация работают на вашем ПК" to "LM Studio та генерація працюють на вашому ПК",
    "Приватный режим" to "Приватний режим",
    "Данные остаются в вашей локальной сети" to "Дані залишаються у вашій локальній мережі",
    "Включено" to "Увімкнено",
    "Локальные интеграции" to "Локальні інтеграції",
    "Подключения для вашего личного пространства" to "Підключення для вашого особистого простору",
    "Подключённые сервисы" to "Підключені сервіси",
    "Neuro может получать доступ к выбранным вами локальным сервисам. Все подключения остаются под вашим контролем." to "Neuro може отримувати доступ до вибраних вами локальних сервісів. Усі підключення залишаються під вашим контролем.",
    "Добавить интеграцию" to "Додати інтеграцію",
    "Локальная память" to "Локальна пам'ять",
    "Настройки приложения" to "Налаштування застосунку",
    "Общие" to "Загальні",
    "Уведомления" to "Сповіщення",
    "Голос" to "Голос",
    "Безопасность" to "Безпека",
    "Элементы управления данными" to "Керування даними",
    "Родительский контроль" to "Батьківський контроль",
    "Доверенный контакт" to "Довірений контакт",
    "Помощь" to "Допомога",
    "Сообщить о проблеме\nв приложении" to "Повідомити про проблему\nв застосунку",
    "Справочный центр" to "Довідковий центр",
    "Сведения" to "Відомості",
    "Выйти" to "Вийти",
    "раздел готовится" to "розділ готується",
    "Спасибо за оценку" to "Дякую за оцінку",
    "Учту при следующих генерациях" to "Врахую під час наступних генерацій",
    "Изображение сохранено в галерею" to "Зображення збережено до галереї",
    "Не удалось сохранить изображение" to "Не вдалося зберегти зображення",
    "Не удалось подготовить изображение" to "Не вдалося підготувати зображення",
    "Сохранить" to "Зберегти",
    "Назад" to "Назад",
    "Закрыть" to "Закрити",
    "Базовый стиль и тон" to "Базовий стиль і тон",
    "Дружелюбный" to "Дружній",
    "Деловой" to "Діловий",
    "Лаконичный" to "Лаконічний",
    "Творческий" to "Творчий",
    "Доброжелательность" to "Доброзичливість",
    "Энтузиазм" to "Ентузіазм",
    "Заголовки и списки" to "Заголовки та списки",
    "Эмодзи" to "Емодзі",
    "Более" to "Більше",
    "По умолчанию" to "Типово",
    "Менее" to "Менше",
    "Быстрые ответы" to "Швидкі відповіді",
    "Иногда Neuro может использовать общие знания, чтобы отвечать быстрее. Персонализация применяется к следующим ответам." to "Іноді Neuro може використовувати загальні знання, щоб відповідати швидше. Персоналізація застосовується до наступних відповідей.",
    "Пользовательские инструкции" to "Користувацькі інструкції",
    "Ссылаться на историю чата" to "Враховувати історію чату",
    "Позволяет Neuro учитывать недавние чаты в ответах." to "Дозволяє Neuro враховувати нещодавні чати у відповідях.",
    "Ссылаться на сохранённую\nпамять" to "Враховувати збережену\nпам'ять",
    "Сохранённая память" to "Збережена пам'ять",
    "Позволяет Neuro сохранять важные факты и использовать память при ответе." to "Дозволяє Neuro зберігати важливі факти та використовувати пам'ять у відповідях.",
    "Здесь можно посмотреть и удалить факты, которые Neuro запомнил о вас." to "Тут можна переглянути та видалити факти, які Neuro запам'ятав про вас.",
    "Ваш псевдоним" to "Ваш псевдонім",
    "Ваша профессия" to "Ваша професія",
    "Больше о вас" to "Більше про вас",
    "Имя" to "Ім'я",
    "Инженер, студент, пр." to "Інженер, студент тощо",
    "Пока здесь пусто" to "Поки що тут порожньо",
    "Включённые приложения" to "Увімкнені застосунки",
    "Просмотр приложений" to "Перегляд застосунків",
    "Neuro может получать доступ к информации из подключённых приложений. Ваши разрешения всегда соблюдаются." to "Neuro може отримувати доступ до інформації з підключених застосунків. Ваші дозволи завжди дотримуються.",
    "Спросить Neuro" to "Запитати Neuro",
    "Думаю" to "Думаю",
    "Думал" to "Думав",
    "с" to "с",
    "Проверяю результат" to "Перевіряю результат",
    "Не удалось создать изображение" to "Не вдалося створити зображення",
    "Генерация не завершилась" to "Генерацію не завершено",
    "Интеллект" to "Інтелект",
    "Модель" to "Модель",
    "Для повседневных чатов" to "Для повсякденних чатів",
    "Для решения сложных вопросов" to "Для вирішення складних питань",
    "Уровень усилий Thinking" to "Рівень зусиль Thinking",
    "Стандартный" to "Стандартний",
    "Расширенное" to "Розширений",
    "Используется 5.5 Thinking" to "Використовується 5.5 Thinking",
    "Редактировать" to "Редагувати",
    "Поделиться" to "Поділитися",
    "Скачать" to "Завантажити",
    "Ещё" to "Ще",
    "Хороший ответ" to "Хороша відповідь",
    "Плохой ответ" to "Погана відповідь",
    "Нравится" to "Подобається",
    "Не нравится" to "Не подобається",
    "Изображения" to "Зображення",
    "Библиотека" to "Бібліотека",
    "Недавнее" to "Нещодавнє",
    "Приветствие в чате" to "Вітання в чаті",
    "Инвестиции" to "Інвестиції",
    "Поиск" to "Пошук",
    "Очистить" to "Очистити",
    "Добавить" to "Додати",
    "Отправить" to "Надіслати",
    "Стоп" to "Стоп",
    "Создать изображение" to "Створити зображення",
    "Создать любое изображение" to "Створити будь-яке зображення",
    "Глубокое исследование" to "Глибоке дослідження",
    "Получить подробный отчёт" to "Отримати докладний звіт",
    "Поиск в сети" to "Пошук у мережі",
    "Искать актуальные новости и информацию" to "Шукати актуальні новини та інформацію",
    "Режим агента" to "Режим агента",
    "Делает работу за вас" to "Виконує роботу за вас",
    "Добавить файлы" to "Додати файли",
    "Анализ или краткое изложение" to "Аналіз або стислий виклад",
    "Все фотографии" to "Усі фотографії"
)
