#!/bin/bash

# HTTP Polling Test Script
# Демонстрирует работу HTTP polling для уведомлений MCP сервера

SERVER_URL="${1:-http://localhost:3001}"
POLL_INTERVAL="${2:-10}"  # Интервал опроса в секундах (по умолчанию 10 для демо)

echo "=================================================="
echo "  MCP Server HTTP Polling Test"
echo "=================================================="
echo "Server URL: $SERVER_URL"
echo "Poll interval: ${POLL_INTERVAL}s"
echo "Endpoint: ${SERVER_URL}/notifications/latest"
echo "=================================================="
echo ""
echo "Нажмите Ctrl+C для остановки"
echo ""

# Счетчик запросов
REQUEST_COUNT=0

# Функция для форматирования времени
timestamp() {
    date "+%Y-%m-%d %H:%M:%S"
}

# Функция для получения и отображения уведомления
fetch_notification() {
    REQUEST_COUNT=$((REQUEST_COUNT + 1))

    echo "┌─────────────────────────────────────────────────"
    echo "│ [$(timestamp)] Request #${REQUEST_COUNT}"
    echo "└─────────────────────────────────────────────────"

    # Выполнить запрос
    RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "${SERVER_URL}/notifications/latest")

    # Извлечь HTTP статус
    HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d':' -f2)
    BODY=$(echo "$RESPONSE" | sed '/HTTP_STATUS/d')

    # Обработать ответ
    if [ "$HTTP_STATUS" = "200" ]; then
        echo "✅ Status: 200 OK"
        echo ""

        # Извлечь и показать уведомление
        NOTIFICATION=$(echo "$BODY" | jq -r '.notification')
        TIMESTAMP=$(echo "$BODY" | jq -r '.timestamp')

        # Конвертировать timestamp в читаемый формат (для macOS)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            READABLE_TIME=$(date -r $(echo "$TIMESTAMP / 1000" | bc) "+%Y-%m-%d %H:%M:%S")
        else
            READABLE_TIME=$(date -d "@$(echo "$TIMESTAMP / 1000" | bc)" "+%Y-%m-%d %H:%M:%S")
        fi

        echo "📅 Timestamp: $READABLE_TIME"
        echo ""
        echo "📋 Notification:"
        echo "────────────────────────────────────────────────"
        echo "$NOTIFICATION"
        echo "────────────────────────────────────────────────"

    elif [ "$HTTP_STATUS" = "204" ]; then
        echo "⏳ Status: 204 No Content"
        MESSAGE=$(echo "$BODY" | jq -r '.message // "No notifications available yet"')
        echo "📭 $MESSAGE"

    else
        echo "❌ Status: $HTTP_STATUS"
        echo "Error: $BODY"
    fi

    echo ""
}

# Основной цикл опроса
while true; do
    fetch_notification
    echo "⏱️  Waiting ${POLL_INTERVAL} seconds until next poll..."
    echo ""
    sleep "$POLL_INTERVAL"
done
