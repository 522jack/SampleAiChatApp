package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import kotlinx.serialization.json.*
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * MCP Client for OpenWeather API
 * Provides weather information tools through MCP protocol
 */
class WeatherMcpClient(
    private val httpClient: HttpClient,
    private val apiKey: String = "demo" // Use demo key or provide your own
) : McpClient {

    private var _initialized = false
    private var _serverInfo: McpServerInfo? = null

    override val isInitialized: Boolean
        get() = _initialized

    override val serverInfo: McpServerInfo?
        get() = _serverInfo

    private val weatherTools = listOf(
        McpTool(
            name = "get_current_weather",
            description = "Get current weather for a specific city. Returns temperature, humidity, pressure, and weather conditions.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("city", buildJsonObject {
                        put("type", "string")
                        put("description", "City name (e.g., 'London', 'New York', 'Moscow')")
                    })
                    put("units", buildJsonObject {
                        put("type", "string")
                        put("description", "Temperature units: 'metric' (Celsius), 'imperial' (Fahrenheit), or 'standard' (Kelvin)")
                        put("enum", buildJsonArray {
                            add("metric")
                            add("imperial")
                            add("standard")
                        })
                        put("default", "metric")
                    })
                })
                put("required", buildJsonArray {
                    add("city")
                })
            }
        ),
        McpTool(
            name = "get_weather_forecast",
            description = "Get 5-day weather forecast for a specific city. Returns forecast data in 3-hour intervals.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("city", buildJsonObject {
                        put("type", "string")
                        put("description", "City name (e.g., 'London', 'New York', 'Moscow')")
                    })
                    put("units", buildJsonObject {
                        put("type", "string")
                        put("description", "Temperature units: 'metric' (Celsius), 'imperial' (Fahrenheit), or 'standard' (Kelvin)")
                        put("enum", buildJsonArray {
                            add("metric")
                            add("imperial")
                            add("standard")
                        })
                        put("default", "metric")
                    })
                    put("days", buildJsonObject {
                        put("type", "number")
                        put("description", "Number of days to forecast (1-5)")
                        put("minimum", 1)
                        put("maximum", 5)
                        put("default", 3)
                    })
                })
                put("required", buildJsonArray {
                    add("city")
                })
            }
        )
    )

    @Serializable
    private data class WeatherResponse(
        val weather: List<WeatherCondition>,
        val main: MainWeatherData,
        val wind: WindData,
        val name: String
    )

    @Serializable
    private data class WeatherCondition(
        val main: String,
        val description: String
    )

    @Serializable
    private data class MainWeatherData(
        val temp: Double,
        val feels_like: Double,
        val temp_min: Double,
        val temp_max: Double,
        val pressure: Int,
        val humidity: Int
    )

    @Serializable
    private data class WindData(
        val speed: Double
    )

    @Serializable
    private data class ForecastResponse(
        val list: List<ForecastItem>,
        val city: CityInfo
    )

    @Serializable
    private data class ForecastItem(
        val dt: Long,
        val main: MainWeatherData,
        val weather: List<WeatherCondition>,
        val wind: WindData,
        val dt_txt: String
    )

    @Serializable
    private data class CityInfo(
        val name: String,
        val country: String
    )

    override suspend fun initialize(): Result<McpInitializeResult> {
        return try {
            _serverInfo = McpServerInfo(
                name = "WeatherMCP",
                version = "1.0.0"
            )
            _initialized = true

            val result = McpInitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = McpServerCapabilities(
                    tools = McpToolsCapability(listChanged = false),
                    resources = McpResourcesCapability(subscribe = false, listChanged = false),
                    prompts = McpPromptsCapability(listChanged = false)
                ),
                serverInfo = _serverInfo!!
            )
            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to initialize Weather MCP client", e)
            Result.failure(e)
        }
    }

    override suspend fun listTools(): Result<List<McpTool>> {
        return if (_initialized) {
            Result.success(weatherTools)
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("MCP client not initialized"))
        }

        return try {
            val result = when (name) {
                "get_current_weather" -> {
                    val city = arguments["city"] as? String ?: return Result.success(
                        McpToolCallResult(
                            content = listOf(McpContent(type = "text", text = "City parameter is required")),
                            isError = true
                        )
                    )
                    val units = arguments["units"] as? String ?: "metric"
                    executeGetCurrentWeather(city, units)
                }
                "get_weather_forecast" -> {
                    val city = arguments["city"] as? String ?: return Result.success(
                        McpToolCallResult(
                            content = listOf(McpContent(type = "text", text = "City parameter is required")),
                            isError = true
                        )
                    )
                    val units = arguments["units"] as? String ?: "metric"
                    val days = (arguments["days"] as? Number)?.toInt() ?: 3
                    executeGetWeatherForecast(city, units, days)
                }
                else -> McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = "Unknown tool: $name")),
                    isError = true
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to call tool $name", e)
            Result.success(
                McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = "Error: ${e.message}")),
                    isError = true
                )
            )
        }
    }

    private suspend fun executeGetCurrentWeather(city: String, units: String): McpToolCallResult {
        return try {
            val response = httpClient.get("https://api.openweathermap.org/data/2.5/weather") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
            }

            if (response.status == HttpStatusCode.OK) {
                val weatherData = Json.decodeFromString<WeatherResponse>(response.bodyAsText())

                val unitSymbol = when (units) {
                    "metric" -> "°C"
                    "imperial" -> "°F"
                    else -> "K"
                }

                val weatherText = buildString {
                    appendLine("🌍 Weather in ${weatherData.name}")
                    appendLine()
                    appendLine("🌡️ Temperature: ${weatherData.main.temp}$unitSymbol (feels like ${weatherData.main.feels_like}$unitSymbol)")
                    appendLine("📊 Conditions: ${weatherData.weather.firstOrNull()?.description ?: "Unknown"}")
                    appendLine("💧 Humidity: ${weatherData.main.humidity}%")
                    appendLine("🎚️ Pressure: ${weatherData.main.pressure} hPa")
                    appendLine("💨 Wind: ${weatherData.wind.speed} ${if (units == "metric") "m/s" else "mph"}")
                    appendLine("🌡️ Min/Max: ${weatherData.main.temp_min}$unitSymbol / ${weatherData.main.temp_max}$unitSymbol")
                }

                McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = weatherText)),
                    isError = false
                )
            } else {
                McpToolCallResult(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Failed to fetch weather: ${response.status}. Please check if the city name is correct."
                    )),
                    isError = true
                )
            }
        } catch (e: Exception) {
            Napier.e("Failed to get current weather", e)
            McpToolCallResult(
                content = listOf(McpContent(
                    type = "text",
                    text = "Error fetching weather data: ${e.message}. This might be due to invalid API key or network issues."
                )),
                isError = true
            )
        }
    }

    private suspend fun executeGetWeatherForecast(city: String, units: String, days: Int): McpToolCallResult {
        return try {
            val response = httpClient.get("https://api.openweathermap.org/data/2.5/forecast") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
                parameter("cnt", days * 8) // 8 forecasts per day (3-hour intervals)
            }

            if (response.status == HttpStatusCode.OK) {
                val forecastData = Json.decodeFromString<ForecastResponse>(response.bodyAsText())

                val unitSymbol = when (units) {
                    "metric" -> "°C"
                    "imperial" -> "°F"
                    else -> "K"
                }

                val forecastText = buildString {
                    appendLine("📅 ${days}-Day Weather Forecast for ${forecastData.city.name}, ${forecastData.city.country}")
                    appendLine()

                    // Group by day
                    val dailyForecasts = forecastData.list.groupBy {
                        it.dt_txt.substring(0, 10) // Group by date
                    }

                    dailyForecasts.entries.take(days).forEach { (date, forecasts) ->
                        appendLine("📆 $date")
                        forecasts.take(4).forEach { forecast -> // Show 4 forecasts per day
                            val time = forecast.dt_txt.substring(11, 16)
                            val temp = forecast.main.temp
                            val condition = forecast.weather.firstOrNull()?.description ?: "Unknown"
                            appendLine("  ⏰ $time - $temp$unitSymbol, $condition")
                        }
                        appendLine()
                    }
                }

                McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = forecastText)),
                    isError = false
                )
            } else {
                McpToolCallResult(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Failed to fetch forecast: ${response.status}. Please check if the city name is correct."
                    )),
                    isError = true
                )
            }
        } catch (e: Exception) {
            Napier.e("Failed to get weather forecast", e)
            McpToolCallResult(
                content = listOf(McpContent(
                    type = "text",
                    text = "Error fetching forecast data: ${e.message}. This might be due to invalid API key or network issues."
                )),
                isError = true
            )
        }
    }

    override suspend fun listResources(): Result<List<McpResource>> {
        return if (_initialized) {
            Result.success(emptyList())
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun listPrompts(): Result<List<McpPrompt>> {
        return if (_initialized) {
            Result.success(emptyList())
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun close() {
        _initialized = false
        _serverInfo = null
    }
}