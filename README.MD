# Dombra

Кроссплатформенный локальный музыкальный плеер (Kotlin Multiplatform + Compose Multiplatform).
Таргеты: **Android**, **iOS**, **Desktop JVM**, **Web (JS и Wasm)**.

Поведенческий референс — [Cosmos Music Player](https://github.com/clquwu/Cosmos-Music-Player) (iOS). Dombra — независимая реализация, см. `NOTICE`. Лицензия: GPL-3.0 (`LICENSE`).

## Требования

- **JDK 21+** (Metro Gradle plugin требует JVM 21). Подойдёт JBR из Android Studio:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Android SDK (путь в `local.properties`: `sdk.dir=...`)
- Для iOS: macOS + Xcode (полный, не только Command Line Tools)

## Сборка и запуск

### Android
- APK: `./gradlew :androidApp:assembleDebug` → `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Или запуск из Android Studio (run configuration `androidApp`)

### Desktop
- Запуск: `./gradlew :desktopApp:run`
- Hot reload: `./gradlew :desktopApp:hotRun --auto`

### iOS
- Открыть `iosApp/iosApp.xcproject` в Xcode и запустить стандартную конфигурацию
- Быстрая проверка компиляции общего кода без Xcode: `./gradlew :sharedUI:compileKotlinIosArm64`

### Web
- Dev-запуск JS: `./gradlew :webApp:jsBrowserDevelopmentRun`
- Dev-запуск Wasm: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Дистрибутив: `./gradlew :webApp:composeCompatibilityBrowserDistribution` → `webApp/build/dist/composeWebCompatibility/productionExecutable`

### Тесты
- Общая доменная логика (JVM): `./gradlew :sharedUI:jvmTest`
- Вся матрица: `./gradlew :sharedUI:jvmTest :androidApp:assembleDebug :webApp:jsBrowserDistribution :webApp:wasmJsBrowserDistribution`

## Конфигурация внешних API (опционально)

Ключи Discogs/Spotify не хранятся в репозитории и не попадают в web bundle.
Без ключей приложение полностью работает локально; внешние данные показывают состояние «источник не настроен».
Локальная конфигурация — см. `docs/` (появится в задаче T10).

## Структура

- `sharedUI/` — общий код: домен, данные, presentation, CMP UI
  - `commonMain` — вся платформонезависимая логика
  - `nonWebMain` — общий код для android/jvm/ios (Room и другие библиотеки без js/wasm-поддержки)
  - `webMain` — общий код js+wasm (браузерное хранилище и адаптеры)
  - `androidMain` / `jvmMain` / `iosMain` — платформенные адаптеры
- `androidApp/`, `desktopApp/`, `iosApp/`, `webApp/` — тонкие платформенные приложения

## Известные ограничения

Смотри `docs/known-limitations.md` (заполняется по ходу задач; ключевое: web — без фонового воспроизведения и доступа к каталогам, аудиоформаты зависят от платформенного backend-а).
