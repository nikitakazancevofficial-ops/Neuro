# Neuro

<p align="center">
  <a href="README.md">English</a> | <strong>Русский</strong>
</p>

<p align="center">
  <img src="app/src/main/res/drawable/neuro_logo.png" alt="Логотип Neuro" width="112" />
</p>

<p align="center">
  <strong>Локальный Android AI-помощник для приватной и мощной повседневной работы.</strong>
</p>

Neuro - активно развивающийся offline-first AI-помощник и портфолио-проект. Приложение объединяет нативный Android-интерфейс, локальные языковые модели, постоянную память, анализ изображений, локальную генерацию картинок и распознавание голоса. Сейчас это ранняя стадия проекта, но основа уже работает и быстро развивается.

## Скриншоты

<p align="center">
  <img src="docs/screenshots/neuro-drawer.jpg" alt="Боковое меню Neuro" width="22%" />
  <img src="docs/screenshots/neuro-empty-chat.jpg" alt="Экран чата Neuro" width="22%" />
  <img src="docs/screenshots/neuro-models.jpg" alt="Выбор локальной модели Neuro" width="22%" />
  <img src="docs/screenshots/neuro-image-editing.jpg" alt="Результат локальной обработки изображения в Neuro" width="22%" />
</p>

<p align="center">
  <em>Навигация, локальный чат, выбор моделей и обработка изображений прямо внутри диалога.</em>
</p>

<p align="center">
  <img src="docs/screenshots/neuro-settings.jpg" alt="Настройки Neuro" width="28%" />
  <img src="docs/screenshots/neuro-personalization.jpg" alt="Персонализация Neuro" width="28%" />
  <img src="docs/screenshots/neuro-memory.jpg" alt="Настройки памяти Neuro" width="28%" />
</p>

## Возможности

- Локальное выполнение: личные чаты и созданные изображения остаются на вашем компьютере
- Нативный Android-клиент на Kotlin и Jetpack Compose
- Потоковые ответы по SSE
- Выбор локальных моделей через LM Studio
- Сохраненная память, персонализация и контекст недавних чатов
- Анализ изображений мультимодальными моделями
- Локальная генерация изображений FLUX.2 Klein с референсами и повторной визуальной проверкой
- Полноэкранный просмотр изображений с масштабированием, ограниченным перемещением, скачиванием и отправкой
- Распознавание голоса Faster Whisper
- Выбор языка интерфейса и ответов Neuro прямо в приложении

## Языки

Полноценный перевод интерфейса доступен для:

- Русского
- Английского
- Украинского

Neuro также умеет отвечать на немецком, испанском, французском, итальянском, португальском, польском, турецком, китайском и японском языках. Для этих языков интерфейс пока использует английский перевод.

## Планы развития

Neuro активно развивается. В планах:

- Локальная генерация музыки
- Более мощный AI Agent для многошаговых задач
- Дальнейшая полировка Android-интерфейса
- Более простой запуск и управление моделями
- Расширение локальных мультимодальных возможностей

## Архитектура

```text
Android-приложение (Jetpack Compose)
        |
        | HTTP + SSE
        v
FastAPI backend
   |           |
   |           +--> FLUX.2 Klein worker
   |
   +--> OpenAI-совместимый API LM Studio
   +--> Модель Faster Whisper
```

Большие модели, личная память, загруженные и созданные изображения, а также локальные Python-окружения намеренно не попадают в Git.

## Требования

- Windows 10 или новее
- Android Studio с Android SDK 34
- JDK 17
- Python 3.10
- LM Studio с включенным локальным OpenAI-совместимым сервером
- NVIDIA GPU рекомендуется для локальной генерации изображений FLUX

## Быстрый старт

### 1. Настройте Android-клиент

Создайте локальную Android-конфигурацию:

```powershell
Copy-Item local.properties.example local.properties
```

Укажите `sdk.dir` и LAN-адрес компьютера в `local.properties`:

```properties
neuro.serverUrl=http://192.168.1.10:3510
```

### 2. Настройте LM Studio и backend

Создайте локальную конфигурацию backend:

```powershell
Copy-Item run_server.local.bat.example run_server.local.bat
```

Укажите тот же LAN-адрес в `PUBLIC_SERVER_URL`. В `LLM_BASE_URL` укажите адрес LM Studio.

### 3. Запустите Neuro

```powershell
.\start_neuro.bat
```

Скрипт создаст `server\.venv`, установит зависимости и запустит API на порту `3510`. Если FLUX.2 Klein уже установлен, локальная генерация изображений включится автоматически. В консоли появятся адреса в блоке `Введите в приложении`.

На экране входа Android-приложения нажмите `Настроить подключение к ПК`, вставьте адрес из консоли и нажмите `Проверить подключение`. Позже адрес можно изменить в настройках Neuro.

### 4. Необязательно: установите локальную генерацию изображений

```powershell
.\setup_flux_klein.bat
.\start_neuro.bat
```

### 5. Соберите Android-приложение

```powershell
.\gradlew.bat :app:assembleDebug
```

APK появится в:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Тесты

Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Backend:

```powershell
Push-Location server
.\.venv\Scripts\python.exe -m unittest test_image_routing.py
Pop-Location
```

## Приватность

Не добавляйте в Git:

- `local.properties`
- `run_server.local.bat`
- `server/storage*.json`
- `server/models/`
- `server/uploads/`
- `server/generated/`
- `server/.venv/`
- `server/.image_venv/`

Эти пути уже исключены в `.gitignore`.

## Лицензия

Исходный код доступен по [лицензии MIT](LICENSE). Вы можете использовать, изменять и развивать Neuro. Для весов моделей и сторонних зависимостей действуют их собственные лицензии.
