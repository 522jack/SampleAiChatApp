# Support Service - AI-powered Customer Support

Мини-сервис поддержки с RAG и MCP интеграцией для ответов на вопросы пользователей.

## Возможности

- ✅ **RAG (Retrieval-Augmented Generation)** - поиск в документации через OLLAMA embeddings
- ✅ **MCP Integration** - доступ к базе тикетов и пользователей
- ✅ **Автоматическая категоризация** - определение типа вопроса через Claude API
- ✅ **Claude AI ответы** - генерация ответов на основе контекста

## Архитектура

```
┌─────────────────┐
│   Client App    │ (KMP - Android/iOS/Desktop)
│  SupportScreen  │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────┐
│  Support API    │ (Ktor Server)
│   :8080/api     │
└────────┬────────┘
         │
    ┌────┴────┬──────────┬──────────┐
    │         │          │          │
┌───▼────┐ ┌─▼─────┐ ┌──▼─────┐ ┌──▼────────┐
│  RAG   │ │  MCP  │ │Catego- │ │  Claude   │
│ Engine │ │Tickets│ │rization│ │    API    │
└────────┘ └───────┘ └────────┘ └───────────┘
    │          │
┌───▼──┐   ┌──▼──────┐
│OLLAMA│   │JSON Data│
│ :11434   │(tickets)│
└──────┘   └─────────┘
```

## Требования

### Backend:
- **JDK 17+**
- **OLLAMA** с моделью `nomic-embed-text`
- **Claude API key**
- **Gradle**

### Client:
- Существующее KMP приложение

## Установка и запуск

### 1. Установка OLLAMA

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Запуск OLLAMA сервера
ollama serve

# Загрузка модели embeddings (в отдельном терминале)
ollama pull nomic-embed-text
```

### 2. Настройка Claude API key

```bash
export CLAUDE_API_KEY="your-api-key-here"
```

Или создайте файл `.env` в корне проекта:
```
CLAUDE_API_KEY=your-api-key-here
```

### 3. Сборка backend

```bash
cd supportService
../gradlew build
```

### 4. Запуск backend сервиса

```bash
../gradlew run
```

Сервис запустится на `http://localhost:8080`

### 5. Проверка работоспособности

```bash
curl http://localhost:8080/api/support/health
```

Должен вернуть:
```json
{
  "status": "ok",
  "ragDocuments": 4,
  "ticketsLoaded": true,
  "timestamp": 1234567890
}
```

### 6. Тестовый запрос

```bash
curl -X POST http://localhost:8080/api/support/ask \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_001",
    "question": "Почему не работает авторизация?"
  }'
```

## Структура проекта

```
supportService/
├── src/main/kotlin/com/claude/support/
│   ├── Application.kt              # Главный файл, точка входа
│   ├── model/                      # Модели данных
│   │   └── SupportModels.kt
│   ├── api/                        # API endpoints
│   │   ├── SupportService.kt       # Главный сервис
│   │   └── SupportRoutes.kt        # Маршруты
│   ├── rag/                        # RAG engine
│   │   └── SupportRagService.kt
│   ├── mcp/                        # MCP интеграция
│   │   └── TicketMcpService.kt
│   └── categorization/             # Категоризация
│       └── CategorizationService.kt
├── docs/                           # База знаний для RAG
│   ├── faq.md
│   ├── auth_guide.md
│   ├── payment_guide.md
│   └── troubleshooting.md
├── data/                           # Mock данные
│   └── support_data.json           # 10 тикетов
└── build.gradle.kts
```

## API Endpoints

### GET /
Информация о сервисе

### GET /api/support
Документация API

### GET /api/support/health
Health check

**Response:**
```json
{
  "status": "ok",
  "ragDocuments": 4,
  "ticketsLoaded": true,
  "timestamp": 1234567890
}
```

### POST /api/support/ask
Отправка вопроса в поддержку

**Request:**
```json
{
  "userId": "user_001",
  "question": "Почему не работает вход через Google?"
}
```

**Response:**
```json
{
  "answer": "При проблемах с входом через Google попробуйте...",
  "category": "авторизация",
  "confidence": 0.95,
  "sources": [
    {
      "type": "doc",
      "title": "Auth Guide",
      "id": "auth_guide.md",
      "relevanceScore": 0.87
    },
    {
      "type": "ticket",
      "title": "Не работает вход через Google",
      "id": "ticket_001"
    }
  ]
}
```

## Интеграция с клиентом

### 1. Добавить SupportApiClient в AppContainer

```kotlin
// shared/src/commonMain/kotlin/com/claude/chat/di/AppContainer.kt
class AppContainer {
    // ...
    val supportApiClient = SupportApiClient(
        httpClient = httpClient,
        baseUrl = "http://localhost:8080"
    )
}
```

### 2. Создать SupportViewModel

```kotlin
val supportViewModel = SupportViewModel(
    supportApiClient = container.supportApiClient
)
```

### 3. Добавить навигацию в App.kt

```kotlin
// Добавить Screen
sealed class Screen {
    // ...
    object Support : Screen()
}

// В NavHost
composable<Screen.Support> {
    SupportScreen(
        viewModel = supportViewModel,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### 4. Добавить кнопку в SettingsScreen

```kotlin
// В SettingsScreen
Button(
    onClick = { navController.navigate(Screen.Support) }
) {
    Text("Техподдержка")
}
```

## Данные

### Mock тикеты (10 штук)
`supportService/data/support_data.json`

Содержит:
- 3 пользователя
- 10 решенных тикетов по категориям:
  - авторизация (3)
  - оплата (2)
  - функционал (3)
  - баги (2)

### Документация для RAG
`supportService/docs/`

- `faq.md` - Часто задаваемые вопросы
- `auth_guide.md` - Руководство по авторизации
- `payment_guide.md` - Руководство по оплате
- `troubleshooting.md` - Решение проблем

## Как это работает

### Процесс обработки вопроса:

1. **Пользователь отправляет вопрос** через SupportScreen
2. **Backend получает запрос** POST /api/support/ask
3. **Категоризация** - Claude определяет категорию вопроса
4. **RAG поиск** - ищет релевантные фрагменты в документации
5. **MCP поиск** - ищет похожие решенные тикеты
6. **Формирование контекста**:
   - Информация о пользователе
   - Топ-3 фрагмента документации
   - Топ-3 похожих тикета
7. **Генерация ответа** - Claude создает ответ на основе контекста
8. **Возврат ответа** с категорией и источниками
9. **Отображение** в SupportScreen

### Пример flow:

```
Вопрос: "Почему не работает авторизация?"
    ↓
Категория: "авторизация" (confidence: 0.95)
    ↓
RAG находит в auth_guide.md:
  - "Ошибка Authentication failed с Google OAuth"
  - "Проблемы с сессиями"
    ↓
MCP находит тикеты:
  - ticket_001: "Не работает вход через Google"
  - ticket_010: "Ошибка Session expired"
    ↓
Claude генерирует ответ на основе контекста:
  "При проблемах с авторизацией попробуйте:
   1. Очистить кэш браузера...
   2. Проверить cookies...

   Согласно документации [Source 1] и решенному
   тикету [Source 2], проблема часто связана..."
```

## Производительность

- **Индексация документации**: ~5-10 секунд при старте
- **Обработка запроса**: 3-5 секунд (включая все этапы)
  - Категоризация: ~1 сек
  - RAG поиск: ~200ms
  - MCP поиск: ~50ms
  - Генерация ответа: ~2-3 сек

## Troubleshooting

### OLLAMA не запускается
```bash
# Проверить статус
ps aux | grep ollama

# Запустить вручную
ollama serve
```

### Модель не найдена
```bash
ollama list
ollama pull nomic-embed-text
```

### Backend не находит документы
Проверьте пути в Application.kt:
- `docsPath = "supportService/docs"`
- `dataPath = "supportService/data/support_data.json"`

### Ошибка CLAUDE_API_KEY not set
```bash
export CLAUDE_API_KEY="your-key"
# Или добавьте в .env файл
```

### Port 8080 уже занят
Измените порт в Application.kt:
```kotlin
embeddedServer(Netty, port = 8081, host = "0.0.0.0")
```

## Дальнейшее развитие

- [ ] Добавить создание новых тикетов
- [ ] Интеграция с реальной CRM системой
- [ ] Улучшенный reranking результатов RAG
- [ ] Кеширование ответов
- [ ] Метрики и аналитика
- [ ] Web UI для управления документацией
- [ ] Поддержка нескольких языков
- [ ] История обращений пользователя

## Лицензия

Проект создан в образовательных целях.