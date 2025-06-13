package org.example

import dev.inmo.tgbotapi.extensions.api.send.media.sendPhoto
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.utils.types.buttons.replyKeyboard
import dev.inmo.tgbotapi.extensions.utils.types.buttons.simpleButton
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.types.buttons.ReplyKeyboardRemove
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.example.database.FeelTable
import org.example.types.RangeResolution.*
import org.example.types.collectFeeling
import org.example.types.collectResolution
import org.jetbrains.exposed.v1.jdbc.Database
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.XYChart
import org.knowm.xchart.style.markers.SeriesMarkers
import java.io.File
import java.util.*

suspend fun main() {
    val token = System.getenv("BOT_TOKEN")
    val databaseUrl: String = System.getenv("DATABASE_URL") ?: error("Please provide a database url")
    val databaseUser: String = System.getenv("DATABASE_USER") ?: error("Please provide a database user")
    val databasePassword: String = System.getenv("DATABASE_PASSWORD") ?: error("Please provide a database password")

    val bot = telegramBot(token)

    val database = Database.connect(
        databaseUrl,
        user = databaseUser,
        password = databasePassword
    )

    val feelTable = FeelTable(database)


    bot.buildBehaviourWithLongPolling {
        launch {
            allUpdatesFlow.collect { update ->
                println(update)
            }
        }

        onCommand("start") {message ->
            bot.sendMessage(
                chatId = message.chat.id,
                text = "Добро пожаловать в бот для трекинга настроения. " + "Вы можете записывать текущее" +
                        " настроение от -10 до 10 по команде /add_feelings " +
                        ", а отслеживать графики настроения за период по команде /get_graphics"
            )
        }

        onCommand("add_feelings") { message ->
            bot.sendMessage(
                chatId = message.chat.id,
                text = "Отправьте значение вашего настроения от -10 до 10"
            )

            val feel = collectFeeling(message)
            val nowTime = Clock.System.now().toEpochMilliseconds()
            feelTable.addFeelNote(message.chat.id.chatId, feel, nowTime)

            bot.sendMessage(
                chatId = message.chat.id,
                text = "Записано!"
            )
        }


        onCommand("get_graphics") { message ->
            bot.sendMessage(
                chatId = message.chat.id,
                text = "Выберите тип графика",
                replyMarkup = replyKeyboard(true) {
                    row {
                        simpleButton("За день")
                        simpleButton("За неделю")
                    }
                    row {
                        simpleButton("За месяц")
                        simpleButton("За год")
                    }
                }
            )
            val resolution = collectResolution(message)
            val notes = feelTable.getNotesByUserIdAndResolution(
                message.chat.id.chatId,
                resolution,
                Clock.System.now().toEpochMilliseconds()
            )
            val timestampList = notes.map { it.timestamp }
            val feelList = notes.map { it.feel }

            val chart = XYChart(1080, 720)
            chart.title = "График настроения за период"
            chart.xAxisTitle = "Время"
            chart.yAxisTitle = "Настроение"

            chart.styler.datePattern = when (resolution) {
                DAILY -> "HH:mm"
                WEEKLY -> "dd.MM HH:mm"
                MONTHLY -> "dd.MM"
                YEARLY -> "MMM yyyy"
            }

            val xData = timestampList.map { Date(it) }
            chart.addSeries("Настроение", xData, feelList).setMarker(SeriesMarkers.CIRCLE)

            BitmapEncoder.saveBitmapWithDPI(chart, "feelPng", BitmapFormat.PNG, 300)

            val file = File("feelPng.png")

            bot.sendPhoto(
                chatId = message.chat.id,
                file.asMultipartFile(),
                replyMarkup = ReplyKeyboardRemove()
            )
            file.delete()
        }

    }.join()
}
