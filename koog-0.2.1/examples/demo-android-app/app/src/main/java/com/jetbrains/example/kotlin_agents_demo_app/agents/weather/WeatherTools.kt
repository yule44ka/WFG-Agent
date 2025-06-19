package com.jetbrains.example.kotlin_agents_demo_app.agents.weather

import ai.koog.agents.core.tools.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Tools for the weather agent
 */
object WeatherTools {
    private val openMeteoClient = OpenMeteoClient()

    // Date formatters
    private val ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val ISO_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val ISO_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    private val UTC_ZONE = ZoneId.of("UTC")

    /**
     * Granularity options for weather forecasts
     */
    @Serializable
    enum class Granularity {
        @SerialName("daily")
        DAILY,

        @SerialName("hourly")
        HOURLY
    }

    /**
     * Tool for getting the current date and time
     */
    object CurrentDatetimeTool : Tool<CurrentDatetimeTool.Args, CurrentDatetimeTool.Result>() {
        @Serializable
        data class Args(
            val timezone: String = "UTC"
        ) : Tool.Args

        @Serializable
        data class Result(
            val datetime: String,
            val date: String,
            val time: String,
            val timezone: String
        ) : ToolResult {
            override fun toStringDefault(): String {
                return "Current datetime: $datetime, Date: $date, Time: $time, Timezone: $timezone"
            }
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "current_datetime",
            description = "Get the current date and time in the specified timezone",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "timezone",
                    description = "The timezone to get the current date and time in (e.g., 'UTC', 'America/New_York', 'Europe/London'). Defaults to UTC.",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val zoneId = try {
                ZoneId.of(args.timezone)
            } catch (e: Exception) {
                UTC_ZONE
            }

            val now = ZonedDateTime.now(zoneId)

            return Result(
                datetime = now.format(ISO_DATETIME_FORMAT),
                date = now.format(ISO_DATE_FORMAT),
                time = now.format(ISO_TIME_FORMAT),
                timezone = zoneId.id
            )
        }
    }

    /**
     * Tool for adding a duration to a date
     */
    object AddDatetimeTool : Tool<AddDatetimeTool.Args, AddDatetimeTool.Result>() {
        @Serializable
        data class Args(
            val date: String,
            val days: Int,
            val hours: Int,
            val minutes: Int
        ) : Tool.Args

        @Serializable
        data class Result(
            val date: String,
            val originalDate: String,
            val daysAdded: Int,
            val hoursAdded: Int,
            val minutesAdded: Int
        ) : ToolResult {
            override fun toStringDefault(): String {
                return buildString {
                    append("Date: $date")
                    if (originalDate.isBlank()) {
                        append(" (starting from today)")
                    } else {
                        append(" (starting from $originalDate)")
                    }

                    if (daysAdded != 0 || hoursAdded != 0 || minutesAdded != 0) {
                        append(" after adding")

                        if (daysAdded != 0) {
                            append(" $daysAdded days")
                        }

                        if (hoursAdded != 0) {
                            if (daysAdded != 0) append(",")
                            append(" $hoursAdded hours")
                        }

                        if (minutesAdded != 0) {
                            if (daysAdded != 0 || hoursAdded != 0) append(",")
                            append(" $minutesAdded minutes")
                        }
                    }
                }
            }
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "add_datetime",
            description = "Add a duration to a date. Use this tool when you need to calculate offsets, such as tomorrow, in two days, etc.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "date",
                    description = "The date to add to in ISO format (e.g., '2023-05-20')",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "days",
                    description = "The number of days to add",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "hours",
                    description = "The number of hours to add",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "minutes",
                    description = "The number of minutes to add",
                    type = ToolParameterType.Integer
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val baseDate = if (args.date.isNotBlank()) {
                try {
                    LocalDate.parse(args.date, ISO_DATE_FORMAT)
                } catch (e: Exception) {
                    // Use current date if parsing fails
                    LocalDate.now(UTC_ZONE)
                }
            } else {
                LocalDate.now(UTC_ZONE)
            }

            // Convert to LocalDateTime to handle hours and minutes
            var dateTime = baseDate.atStartOfDay()
            dateTime = dateTime.plusDays(args.days.toLong())
                .plusHours(args.hours.toLong())
                .plusMinutes(args.minutes.toLong())

            // Extract the date part
            val resultDate = dateTime.toLocalDate().format(ISO_DATE_FORMAT)

            return Result(
                date = resultDate,
                originalDate = args.date,
                daysAdded = args.days,
                hoursAdded = args.hours,
                minutesAdded = args.minutes
            )
        }
    }

    /**
     * Tool for getting a weather forecast
     */
    object WeatherForecastTool : Tool<WeatherForecastTool.Args, WeatherForecastTool.Result>() {
        @Serializable
        data class Args(
            val location: String,
            val date: String = "",
            val days: Int = 1,
            val granularity: Granularity = Granularity.DAILY
        ) : Tool.Args

        @Serializable
        data class Result(
            val locationName: String,
            val locationCountry: String? = null,
            val forecast: String,
            val date: String,
            val granularity: Granularity
        ) : ToolResult {
            override fun toStringDefault(): String {
                val granularityText = when (granularity) {
                    Granularity.DAILY -> "daily"
                    Granularity.HOURLY -> "hourly"
                }
                val dateInfo = if (date.isBlank()) "starting from today" else "for $date"
                val formattedLocation = if (locationCountry.isNullOrBlank()) {
                    locationName
                } else {
                    "$locationName, $locationCountry"
                }.trim().trimEnd(',')

                return "Weather forecast for $formattedLocation ($granularityText, $dateInfo):\n$forecast"
            }
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "weather_forecast",
            description = "Get a weather forecast for a location with specified granularity (daily or hourly)",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "location",
                    description = "The location to get the weather forecast for (e.g., 'New York', 'London', 'Paris')",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "date",
                    description = "The date to get the weather forecast for in ISO format (e.g., '2023-05-20'). If empty, the forecast starts from today.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "days",
                    description = "The number of days to forecast (1-7)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "granularity",
                    description = "The granularity of the forecast: 'daily' for day-by-day forecast or 'hourly' for hour-by-hour forecast. Default is 'daily'.",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            // Search for the location
            val locations = openMeteoClient.searchLocation(args.location)
            if (locations.isEmpty()) {
                return Result(
                    locationName = args.location,
                    forecast = "Location not found",
                    date = args.date,
                    granularity = args.granularity
                )
            }

            val location = locations.first()
            val forecastDays = args.days.coerceIn(1, 7)

            // Get the weather forecast
            val forecast = openMeteoClient.getWeatherForecast(
                latitude = location.latitude,
                longitude = location.longitude,
                forecastDays = forecastDays
            )

            // Format the forecast based on granularity
            val formattedForecast = when (args.granularity) {
                Granularity.HOURLY -> formatHourlyForecast(forecast, args.date)
                Granularity.DAILY -> formatDailyForecast(forecast, args.date)
            }

            return Result(
                locationName = location.name,
                locationCountry = location.country,
                forecast = formattedForecast,
                date = args.date,
                granularity = args.granularity
            )
        }

        private fun formatDailyForecast(forecast: WeatherForecast, date: String): String {
            val daily = forecast.daily ?: return "No daily forecast data available"

            val startDate = date.ifBlank {
                LocalDate.now(UTC_ZONE).format(ISO_DATE_FORMAT)
            }

            val startIndex = daily.time.indexOfFirst { it >= startDate }.coerceAtLeast(0)

            return buildString {
                for (i in startIndex until daily.time.size) {
                    val dateStr = daily.time[i]
                    val maxTemp = daily.temperature2mMax?.getOrNull(i)?.toString() ?: "N/A"
                    val minTemp = daily.temperature2mMin?.getOrNull(i)?.toString() ?: "N/A"
                    val weatherCode = daily.weatherCode?.getOrNull(i)
                    val weatherDesc = getWeatherDescription(weatherCode)
                    val precipSum = daily.precipitationSum?.getOrNull(i)?.toString() ?: "0"

                    append("$dateStr: $weatherDesc, ")
                    append("Temperature: $minTemp°C to $maxTemp°C, ")
                    append("Precipitation: $precipSum mm")

                    if (i < daily.time.size - 1) {
                        append("\n")
                    }
                }
            }
        }

        private fun formatHourlyForecast(forecast: WeatherForecast, date: String): String {
            val hourly = forecast.hourly ?: return "No hourly forecast data available"

            val startDate = date.ifBlank {
                LocalDate.now(UTC_ZONE).format(ISO_DATE_FORMAT)
            }

            // Find the starting index for the requested date
            val startIndex = hourly.time.indexOfFirst { 
                it.startsWith(startDate) || it > startDate 
            }.coerceAtLeast(0)

            return buildString {
                // Group hourly forecasts by date for better readability
                val groupedByDate = hourly.time.subList(startIndex, hourly.time.size).mapIndexed { index, time ->
                    val actualIndex = startIndex + index
                    val dateTime = time.split("T")
                    val date = dateTime[0]
                    val hour = if (dateTime.size > 1) dateTime[1].substringBefore(":") else "00"

                    val temp = hourly.temperature2m?.getOrNull(actualIndex)?.toString() ?: "N/A"
                    val precipProb = hourly.precipitationProbability?.getOrNull(actualIndex)?.toString() ?: "N/A"
                    val weatherCode = hourly.weatherCode?.getOrNull(actualIndex)
                    val weatherDesc = getWeatherDescription(weatherCode)

                    Triple(date, "$hour:00: $weatherDesc, Temperature: ${temp}°C, Precipitation probability: $precipProb%", actualIndex)
                }.groupBy { it.first }

                groupedByDate.forEach { (date, forecasts) ->
                    append("$date:\n")
                    forecasts.forEach { (_, forecast, _) ->
                        append("  $forecast\n")
                    }
                }
            }
        }

        private fun getWeatherDescription(code: Int?): String {
            return when (code) {
                0 -> "Clear sky"
                1 -> "Mainly clear"
                2 -> "Partly cloudy"
                3 -> "Overcast"
                45, 48 -> "Fog"
                51, 53, 55 -> "Drizzle"
                56, 57 -> "Freezing drizzle"
                61, 63, 65 -> "Rain"
                66, 67 -> "Freezing rain"
                71, 73, 75 -> "Snow fall"
                77 -> "Snow grains"
                80, 81, 82 -> "Rain showers"
                85, 86 -> "Snow showers"
                95 -> "Thunderstorm"
                96, 99 -> "Thunderstorm with hail"
                else -> "Unknown"
            }
        }
    }
}
