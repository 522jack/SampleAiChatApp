# MCP Server Setup Guide

## Что было создано

Создан standalone MCP (Model Context Protocol) сервер на Kotlin, который:

1. ✅ Работает как отдельный процесс
2. ✅ Подключается к OpenWeather API
3. ✅ Поддерживает два транспортных протокола:
   - **STDIO** - для подключения из Claude Desktop и других MCP клиентов
   - **SSE (Server-Sent Events)** - для подключения через HTTP
4. ✅ Предоставляет два weather tools:
   - `get_current_weather` - текущая погода для города
   - `get_weather_forecast` - прогноз на 5 дней

## Структура проекта

```
mcp-server/
├── src/main/kotlin/com/claude/mcp/server/
│   ├── Main.kt                      # Точка входа
│   ├── McpServerHandler.kt          # Обработчик MCP протокола
│   ├── protocol/
│   │   └── McpProtocol.kt           # MCP модели данных
│   ├── services/
│   │   └── WeatherService.kt        # Сервис для Weather API
│   └── transport/
│       ├── StdioTransport.kt        # STDIO транспорт
│       └── SseTransport.kt          # SSE транспорт
├── build.gradle.kts
├── README.md
├── run-server.sh                    # Скрипт запуска
└── claude_desktop_config_example.json
```

## Сборка

```bash
# Собрать JAR с зависимостями
./gradlew :mcp-server:fatJar

# JAR будет создан в:
# mcp-server/build/libs/mcp-weather-server-1.0.0.jar
```

## Запуск

### Вариант 1: STDIO режим (для Claude Desktop)

```bash
# Используя скрипт
cd mcp-server
./run-server.sh stdio

# Или напрямую
export OPENWEATHER_API_KEY=your_key_here
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar stdio
```

### Вариант 2: SSE режим (HTTP сервер)

```bash
# Используя скрипт (порт 3000 по умолчанию)
cd mcp-server
./run-server.sh sse 3000

# Или напрямую
export OPENWEATHER_API_KEY=your_key_here
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000

# Сервер будет доступен на http://localhost:3000
```

## Конфигурация OpenWeather API

1. Получите бесплатный API ключ на https://openweathermap.org/api
2. Установите его как переменную окружения:

```bash
export OPENWEATHER_API_KEY=your_api_key_here
```

Или передайте в конфигурации (см. ниже).

## Подключение к Claude Desktop

### macOS

Отредактируйте файл конфигурации:
```bash
nano ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

Добавьте:
```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/YOUR_USERNAME/Desktop/Projects/SampleAiAgentApp/mcp-server/build/libs/mcp-weather-server-1.0.0.jar",
        "stdio"
      ],
      "env": {
        "OPENWEATHER_API_KEY": "your_api_key_here"
      }
    }
  }
}
```

**Важно:** Замените `/Users/YOUR_USERNAME/Desktop/Projects/...` на абсолютный путь к вашему JAR файлу.

### Windows

Файл конфигурации: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\path\\to\\SampleAiAgentApp\\mcp-server\\build\\libs\\mcp-weather-server-1.0.0.jar",
        "stdio"
      ],
      "env": {
        "OPENWEATHER_API_KEY": "your_api_key_here"
      }
    }
  }
}
```

После настройки перезапустите Claude Desktop.

## Подключение из вашего приложения

### Через HTTP (SSE режим)

1. Запустите сервер в SSE режиме:
   ```bash
   java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000
   ```

2. Подключитесь к `http://localhost:3000` из приложения:
   - `GET /sse` - установить SSE соединение
   - `POST /message` - отправить MCP сообщение (с заголовком `X-Session-Id`)
   - `GET /health` - проверка здоровья сервера
   - `GET /sessions` - список активных сессий

### Через процесс (STDIO режим)

Запустите сервер как подпроцесс из вашего приложения и общайтесь через stdin/stdout.

Пример на Kotlin:
```kotlin
val process = ProcessBuilder(
    "java", "-jar",
    "path/to/mcp-weather-server-1.0.0.jar",
    "stdio"
).apply {
    environment()["OPENWEATHER_API_KEY"] = "your_key"
}.start()

// Отправка JSON-RPC сообщений через process.outputStream
// Чтение ответов через process.inputStream
```

## Использование tools

### get_current_weather

Получить текущую погоду для города.

**Пример запроса:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_current_weather",
    "arguments": {
      "city": "London",
      "units": "metric"
    }
  }
}
```

**Параметры:**
- `city` (обязательный): Название города (например, "London", "New York", "Moscow")
- `units` (опциональный): "metric" (°C, по умолчанию), "imperial" (°F), или "standard" (K)

### get_weather_forecast

Получить прогноз погоды на несколько дней.

**Пример запроса:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_weather_forecast",
    "arguments": {
      "city": "Paris",
      "units": "metric",
      "days": 3
    }
  }
}
```

**Параметры:**
- `city` (обязательный): Название города
- `units` (опциональный): "metric", "imperial", или "standard"
- `days` (опциональный): Количество дней (1-5, по умолчанию 3)

## Тестирование

### Тест через STDIO (ручной)

```bash
# Запустите сервер
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar stdio

# Вставьте это в stdin (одна строка):
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}

# Затем запросите список tools:
{"jsonrpc":"2.0","id":2,"method":"tools/list"}

# Вызовите tool:
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_current_weather","arguments":{"city":"London","units":"metric"}}}
```

### Тест через HTTP (с curl)

```bash
# Запустите сервер в SSE режиме
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000

# В другом терминале подключитесь к SSE
curl -N http://localhost:3000/sse
# Запишите session ID из ответа

# В третьем терминале отправьте сообщение
curl -X POST http://localhost:3000/message \
  -H "Content-Type: application/json" \
  -H "X-Session-Id: YOUR_SESSION_ID" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "test", "version": "1.0.0"}
    }
  }'
```

## Следующие шаги

1. ✅ **MCP Сервер создан и готов к использованию**
2. ⏳ **Добавить UI в приложении** для управления подключениями к MCP серверам
3. ⏳ **Реализовать MCP Client** в shared модуле для подключения к внешним серверам
4. ⏳ **Добавить настройки** где можно указать URL/команду для запуска MCP сервера

## Полезные ссылки

- [MCP Specification](https://spec.modelcontextprotocol.io)
- [OpenWeather API](https://openweathermap.org/api)
- [Claude Desktop MCP Configuration](https://modelcontextprotocol.io/docs/tools/claude-desktop)

## Troubleshooting

### Сервер не запускается

1. Проверьте, что Java 17+ установлена: `java -version`
2. Проверьте, что JAR собран: `ls -lh mcp-server/build/libs/`
3. Проверьте логи в stderr

### Claude Desktop не видит tools

1. Проверьте правильность пути к JAR в конфигурации
2. Убедитесь, что используется абсолютный путь
3. Перезапустите Claude Desktop после изменения конфигурации
4. Проверьте логи Claude Desktop

### Weather API не работает

1. Проверьте, что API ключ установлен: `echo $OPENWEATHER_API_KEY`
2. Проверьте, что ключ активен на openweathermap.org
3. Попробуйте с demo ключом (ограниченная функциональность)