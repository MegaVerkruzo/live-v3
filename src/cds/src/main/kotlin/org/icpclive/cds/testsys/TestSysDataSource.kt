package org.icpclive.cds.testsys

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import org.icpclive.api.*
import org.icpclive.cds.ContestParseResult
import org.icpclive.cds.FullReloadContestDataSource
import org.icpclive.cds.common.StringLoader
import org.icpclive.cds.common.map
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class TestSysDataSource(val properties: Properties) : FullReloadContestDataSource(5.seconds) {
    val loader = StringLoader(null) {
        properties.getProperty("url")
    }.map { it.substring(it.indexOf(EOF) + 1) }
        .map { it.split("\r\n").filter { it.isNotEmpty() } }

    val timeZone = properties.getProperty("timezone") ?: "Europe/Moscow"

    override suspend fun loadOnce(): ContestParseResult {
        val lines = loader.load()
        val data = lines.groupBy(
            keySelector = { it.split(" ", limit = 2)[0] },
            valueTransform = { it.split(" ", limit = 2)[1] }
        )
        val problemsWithPenalty = (data["@p"] ?: emptyList()).mapIndexed { index, prob ->
            val (letter, name, penalty) = prob.splitCommas()
            ProblemInfo(
                letter = letter,
                name = name,
                id = index,
                ordinal = index,
                cdsId = letter,
            ) to penalty.toInt()
        }
        val penalty = problemsWithPenalty.map { it.second }.distinct().takeIf { it.size <= 1 } ?:
            TODO("Different problem penalties are not supported")
        val teams = (data["@t"] ?: emptyList()).mapIndexed { index, team ->
            val (id, _, _, name) = team.splitCommas().also { println(it) }
            TeamInfo(
                id = index,
                name = name,
                shortName = name,
                contestSystemId = id,
                groups = listOf(),
                hashTag = null,
                medias = emptyMap()
            )
        }
        val isCEPenalty = data["@comment"]?.contains("@pragma IgnoreCE") != true
        val problems = problemsWithPenalty.map { it.first }
        val problemIdMap = problems.associate { it.cdsId to it.id }
        val teamIdMap = teams.associate { it.contestSystemId to it.id }
        val contestInfo = ContestInfo(
            name = data["@contest"]!!.single(),
            status = data["@state"]!!.single().toStatus(),
            resultType = ContestResultType.ICPC,
            startTime = data["@startat"]!!.single().toDate(),
            contestLength = data["@contlen"]!!.single().toInt().minutes,
            freezeTime = data["@freeze"]!!.single().toInt().minutes,
            teams = teams,
            problems = problems,
            penaltyPerWrongAttempt = (penalty.getOrNull(0) ?: 20),
            penaltyRoundingMode = PenaltyRoundingMode.SUM_DOWN_TO_MINUTE
        )
        val runs = (data["@s"] ?: emptyList()).mapIndexed { index, subm ->
            val (teamId, problemId, _, time, verdict) = subm.splitCommas()
            RunInfo(
                id = index,
                result = ICPCRunResult(
                    isAccepted = (verdict == "OK"),
                    isAddingPenalty = when {
                        verdict == "OK" -> false
                        verdict == "CE" -> isCEPenalty
                        else -> true
                    },
                    isFirstToSolveRun = false,
                    result = verdict
                ),
                percentage = 1.0,
                problemId = problemIdMap[problemId]!!,
                teamId = teamIdMap[teamId]!!,
                time = time.toInt().seconds,
            )
        }
        return ContestParseResult(
            contestInfo,
            runs,
            emptyList()
        )
    }

    private fun String.splitCommas() = buildList {
        val builder = StringBuilder()
        var inEsc = false
        for (c in this@splitCommas) {
            when {
                !inEsc && c == ',' -> {
                    add(builder.toString())
                    builder.clear()
                }
                c == '"' -> inEsc = !inEsc
                else -> builder.append(c)
            }
        }
        add(builder.toString())
    }

    private fun String.toDate() =
        java.time.LocalDateTime.parse(this, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
            .toKotlinLocalDateTime()
            .toInstant(TimeZone.of(timeZone))

    private fun String.toStatus() = when (this) {
        "RESULTS" -> ContestStatus.OVER
        "FROZEN" -> ContestStatus.OVER
        "OVER" -> ContestStatus.OVER
        "BEFORE" -> ContestStatus.BEFORE
        "RUNNING" -> ContestStatus.RUNNING
        else -> TODO("Unknown contests state $this")
    }

    companion object {
        const val EOF = 26.toChar()
    }
}