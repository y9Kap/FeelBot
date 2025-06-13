package org.example.types

import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitText
import dev.inmo.tgbotapi.types.message.content.TextMessage
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneOffset

fun Long.truncateToDay(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC)
        .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.truncateToMonth(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC)
        .withDayOfMonth(1).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

suspend fun BehaviourContext.collectFeeling(message: TextMessage): Int {
    val input = waitText().first()
    val feel = input.text.toIntOrNull()
    return if (feel != null && feel in -10..10) {
        feel
    } else {
        bot.sendMessage(
            chatId = message.chat.id,
            text = "Ошибка. Введите число от -10 до 10, например, 5."
        )
        collectFeeling(message)
    }
}

suspend fun BehaviourContext.collectResolution(message: TextMessage): RangeResolution {
    val input = waitText().first().text

    return when (input) {
        "За день" -> RangeResolution.DAILY
        "За неделю" -> RangeResolution.WEEKLY
        "За месяц" -> RangeResolution.MONTHLY
        "За год" -> RangeResolution.YEARLY
        else -> {
            bot.sendMessage(
                chatId = message.chat.id,
                text = "Ошибка. Пожалуйста, выберите один из предложенных вариантов."
            )
            collectResolution(message)
        }
    }
}
