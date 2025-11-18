# Weather MCP Server

Standalone MCP (Model Context Protocol) server providing weather information from OpenWeather API.

## Features

- ✅ MCP Protocol 2024-11-05 compliant
- ✅ Two transport modes: STDIO and SSE (Server-Sent Events)
- ✅ Weather tools:
  - `get_current_weather` - Get current weather for any city
  - `get_weather_forecast` - Get 5-day weather forecast
- ✅ Supports multiple temperature units (Celsius, Fahrenheit, Kelvin)

## Building

Build the server JAR:

```bash
./gradlew :mcp-server:fatJar
```

The JAR will be created at: `mcp-server/build/libs/mcp-weather-server-1.0.0.jar`

## Running

### STDIO Mode (for MCP clients like Claude Desktop)

```bash
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar stdio
```

Or using Gradle:

```bash
./gradlew :mcp-server:run
```

### SSE Mode (HTTP server)

```bash
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000
```

The server will start on port 3000 with the following endpoints:
- `GET /sse` - SSE connection endpoint
- `POST /message` - Send MCP messages
- `GET /health` - Health check
- `GET /sessions` - List active sessions

## Configuration

### OpenWeather API Key

Set the API key as an environment variable:

```bash
export OPENWEATHER_API_KEY=your_api_key_here
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar stdio
```

If not set, the server will use a demo key (limited functionality).

## Connecting to Claude Desktop

Add to your Claude Desktop configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-weather-server-1.0.0.jar",
        "stdio"
      ],
      "env": {
        "OPENWEATHER_API_KEY": "your_api_key_here"
      }
    }
  }
}
```

## Connecting from Your App

### Via HTTP (SSE mode)

1. Start server in SSE mode:
   ```bash
   java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000
   ```

2. Connect from your app to `http://localhost:3000`

### Via Process (STDIO mode)

1. Launch the server as a subprocess from your app
2. Communicate via stdin/stdout using JSON-RPC

## Tools Available

### get_current_weather

Get current weather conditions for a city.

**Parameters:**
- `city` (required): City name (e.g., "London", "New York", "Moscow")
- `units` (optional): "metric" (default), "imperial", or "standard"

**Example:**
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

### get_weather_forecast

Get weather forecast for upcoming days.

**Parameters:**
- `city` (required): City name
- `units` (optional): "metric" (default), "imperial", or "standard"
- `days` (optional): Number of days (1-5, default: 3)

**Example:**
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

## Development

### Project Structure

```
mcp-server/
├── src/main/kotlin/com/claude/mcp/server/
│   ├── Main.kt                      # Entry point
│   ├── McpServerHandler.kt          # MCP protocol handler
│   ├── protocol/
│   │   └── McpProtocol.kt           # MCP protocol models
│   ├── services/
│   │   └── WeatherService.kt        # Weather API service
│   └── transport/
│       ├── StdioTransport.kt        # STDIO transport
│       └── SseTransport.kt          # SSE transport
└── build.gradle.kts
```

### Running in Development

```bash
# STDIO mode
./gradlew :mcp-server:run

# SSE mode
./gradlew :mcp-server:run --args="sse 3000"
```

## Testing

Test the server manually using curl (SSE mode):

```bash
# Start server
java -jar mcp-server/build/libs/mcp-weather-server-1.0.0.jar sse 3000

# In another terminal, connect to SSE
curl -N http://localhost:3000/sse

# In another terminal, send a message
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
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }'
```

## License

MIT