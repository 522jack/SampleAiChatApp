#!/bin/bash

# Weather MCP Server Launcher

# Set your OpenWeather API key here or as environment variable
if [ -z "$OPENWEATHER_API_KEY" ]; then
    echo "⚠️  Warning: OPENWEATHER_API_KEY not set, using demo key (limited functionality)"
    echo "   Set it with: export OPENWEATHER_API_KEY=your_key_here"
    export OPENWEATHER_API_KEY="demo"
fi

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/build/libs/mcp-weather-server-1.0.0.jar"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "   Build it first with: ./gradlew :mcp-server:fatJar"
    exit 1
fi

# Get transport mode (stdio or sse)
TRANSPORT="${1:-stdio}"
PORT="${2:-3000}"

echo "🌦️  Starting Weather MCP Server"
echo "   Transport: $TRANSPORT"
if [ "$TRANSPORT" = "sse" ]; then
    echo "   Port: $PORT"
fi
echo "   API Key: ${OPENWEATHER_API_KEY:0:8}..."
echo ""

# Run the server
java -jar "$JAR_FILE" "$TRANSPORT" "$PORT"