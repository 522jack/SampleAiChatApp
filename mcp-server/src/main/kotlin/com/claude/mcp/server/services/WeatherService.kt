package com.claude.mcp.server.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Service for interacting with OpenWeather API
 */
class WeatherService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class WeatherResponse(
        val weather: List<WeatherCondition>,
        val main: MainWeatherData,
        val wind: WindData,
        val name: String,
        val sys: SysData? = null
    )

    @Serializable
    data class WeatherCondition(
        val main: String,
        val description: String,
        val icon: String
    )

    @Serializable
    data class MainWeatherData(
        val temp: Double,
        val feels_like: Double,
        val temp_min: Double,
        val temp_max: Double,
        val pressure: Int,
        val humidity: Int
    )

    @Serializable
    data class WindData(
        val speed: Double,
        val deg: Int? = null
    )

    @Serializable
    data class SysData(
        val country: String? = null
    )

    @Serializable
    data class ForecastResponse(
        val list: List<ForecastItem>,
        val city: CityInfo
    )

    @Serializable
    data class ForecastItem(
        val dt: Long,
        val main: MainWeatherData,
        val weather: List<WeatherCondition>,
        val wind: WindData,
        val dt_txt: String
    )

    @Serializable
    data class CityInfo(
        val name: String,
        val country: String
    )

    suspend fun getCurrentWeather(city: String, units: String = "metric"): Result<String> {
        return try {
            logger.info { "Fetching current weather for city: $city with units: $units" }

            val response = httpClient.get("https://api.openweathermap.org/data/2.5/weather") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
            }

            if (response.status == HttpStatusCode.OK) {
                val weatherData = json.decodeFromString<WeatherResponse>(response.bodyAsText())
                val formatted = formatCurrentWeather(weatherData, units)
                logger.info { "Successfully fetched weather for $city" }
                Result.success(formatted)
            } else {
                val error = "Failed to fetch weather: ${response.status}. Please check if the city name is correct."
                logger.error { error }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching weather for city: $city" }
            Result.failure(e)
        }
    }

    suspend fun getWeatherForecast(
        city: String,
        units: String = "metric",
        days: Int = 3
    ): Result<String> {
        return try {
            logger.info { "Fetching $days-day forecast for city: $city with units: $units" }

            val count = days * 8 // 8 forecasts per day (3-hour intervals)
            val response = httpClient.get("https://api.openweathermap.org/data/2.5/forecast") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
                parameter("cnt", count)
            }

            if (response.status == HttpStatusCode.OK) {
                val forecastData = json.decodeFromString<ForecastResponse>(response.bodyAsText())
                val formatted = formatForecast(forecastData, units, days)
                logger.info { "Successfully fetched forecast for $city" }
                Result.success(formatted)
            } else {
                val error = "Failed to fetch forecast: ${response.status}. Please check if the city name is correct."
                logger.error { error }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching forecast for city: $city" }
            Result.failure(e)
        }
    }

    private fun formatCurrentWeather(data: WeatherResponse, units: String): String {
        val unitSymbol = when (units) {
            "metric" -> "°C"
            "imperial" -> "°F"
            else -> "K"
        }

        val windUnit = if (units == "metric") "m/s" else "mph"

        return buildString {
            appendLine("🌍 Weather in ${data.name}${data.sys?.country?.let { ", $it" } ?: ""}")
            appendLine()
            appendLine("🌡️ Temperature: ${data.main.temp}$unitSymbol (feels like ${data.main.feels_like}$unitSymbol)")
            appendLine("📊 Conditions: ${data.weather.firstOrNull()?.description ?: "Unknown"}")
            appendLine("💧 Humidity: ${data.main.humidity}%")
            appendLine("🎚️ Pressure: ${data.main.pressure} hPa")
            appendLine("💨 Wind: ${data.wind.speed} $windUnit${data.wind.deg?.let { " from ${it}°" } ?: ""}")
            appendLine("🌡️ Min/Max: ${data.main.temp_min}$unitSymbol / ${data.main.temp_max}$unitSymbol")
        }
    }

    private fun formatForecast(data: ForecastResponse, units: String, days: Int): String {
        val unitSymbol = when (units) {
            "metric" -> "°C"
            "imperial" -> "°F"
            else -> "K"
        }

        return buildString {
            appendLine("📅 ${days}-Day Weather Forecast for ${data.city.name}, ${data.city.country}")
            appendLine()

            // Group by day
            val dailyForecasts = data.list.groupBy {
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
    }
}