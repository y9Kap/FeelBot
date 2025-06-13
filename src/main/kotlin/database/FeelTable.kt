package org.example.database


import dev.inmo.tgbotapi.types.RawChatId
import kotlinx.coroutines.Dispatchers
import org.example.types.FeelNote
import org.example.types.RangeResolution
import org.example.types.truncateToDay
import org.example.types.truncateToMonth
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class FeelTable(private val db: Database) : Table() {
    private val USER_ID = long("USER_ID")
    private val FEEL = integer("FEEL")
    private val TIMESTAMP = long("TIMESTAMP")

    init {
        transaction(db) {
            SchemaUtils.create(this@FeelTable)
        }
    }

    suspend fun addFeelNote(userId: RawChatId, feel: Int, timestamp: Long) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            insert { statement ->
                statement[USER_ID] = userId.long
                statement[FEEL] = feel
                statement[TIMESTAMP] = timestamp
            }
        }

    suspend fun getNotesByUserId(userId: RawChatId) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            this@FeelTable.selectAll().where(USER_ID eq userId.long).map {
                FeelNote(it[TIMESTAMP], it[FEEL])
            }
        }

    suspend fun getNotesByUserIdAndResolution(
        userId: RawChatId,
        resolution: RangeResolution,
        nowMillis: Long
    ): List<FeelNote> = newSuspendedTransaction(Dispatchers.IO, db) {
        val now = Instant.ofEpochMilli(nowMillis)
        val (fromTimestamp, toTimestamp) = when (resolution) {
            RangeResolution.DAILY -> {
                val from = now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                from.toEpochMilli() to now.toEpochMilli()
            }
            RangeResolution.WEEKLY -> {
                val from = now.minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                from.toEpochMilli() to now.toEpochMilli()
            }
            RangeResolution.MONTHLY -> {
                val from = now.minus(1, ChronoUnit.MONTHS).truncatedTo(ChronoUnit.DAYS)
                from.toEpochMilli() to now.toEpochMilli()
            }
            RangeResolution.YEARLY -> {
                val from = now.minus(1, ChronoUnit.YEARS).truncatedTo(ChronoUnit.DAYS)
                from.toEpochMilli() to now.toEpochMilli()
            }
        }

        val allNotes = this@FeelTable.selectAll().where {
            USER_ID eq userId.long and
                    (TIMESTAMP greaterEq fromTimestamp) and
                    (TIMESTAMP lessEq toTimestamp)
        }.map {
            it[TIMESTAMP] to it[FEEL]
        }

        when (resolution) {
            RangeResolution.DAILY -> {
                allNotes.map { (ts, feel) -> FeelNote(ts, feel) }
            }

            RangeResolution.WEEKLY -> {
                allNotes.groupBy { it.first.truncateToDay() }.flatMap { (dayStart, entries) ->
                    val partsOfDay = mapOf(
                        "morning" to 6..11,
                        "afternoon" to 12..17,
                        "evening" to 18..23
                    )

                    partsOfDay.mapNotNull { (_, hours) ->
                        val valuesInPart = entries.filter { (ts, _) ->
                            val hour = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC).hour
                            hour in hours
                        }.map { it.second }

                        if (valuesInPart.isEmpty()) return@mapNotNull null

                        val median = valuesInPart.sorted().let { sorted ->
                            val mid = sorted.size / 2
                            if (sorted.size % 2 == 0)
                                ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
                            else
                                sorted[mid]
                        }

                        val partStart = Instant.ofEpochMilli(dayStart).atZone(ZoneOffset.UTC)
                            .withHour(hours.first).withMinute(0).withSecond(0).withNano(0)
                            .toInstant().toEpochMilli()

                        FeelNote(partStart, median)
                    }
                }.sortedBy { it.timestamp }
            }

            RangeResolution.MONTHLY -> {
                allNotes.groupBy { it.first.truncateToDay() }.mapNotNull { (dayStart, entries) ->
                    val median = entries.map { it.second }.sorted().let { sorted ->
                        val mid = sorted.size / 2
                        if (sorted.size % 2 == 0)
                            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
                        else
                            sorted[mid]
                    }
                    FeelNote(dayStart, median)
                }.sortedBy { it.timestamp }
            }

            RangeResolution.YEARLY -> {
                allNotes.groupBy { it.first.truncateToMonth() }.mapNotNull { (monthStart, entries) ->
                    val dailyMedians = entries.groupBy { it.first.truncateToDay() }.map { (_, dailyEntries) ->
                        dailyEntries.map { it.second }.sorted().let { sorted ->
                            val mid = sorted.size / 2
                            if (sorted.size % 2 == 0)
                                ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
                            else
                                sorted[mid]
                        }
                    }

                    if (dailyMedians.isEmpty()) return@mapNotNull null

                    val monthlyMedian = dailyMedians.sorted().let { sorted ->
                        val mid = sorted.size / 2
                        if (sorted.size % 2 == 0)
                            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
                        else
                            sorted[mid]
                    }

                    FeelNote(monthStart, monthlyMedian)
                }.sortedBy { it.timestamp }
            }
        }
    }
}
