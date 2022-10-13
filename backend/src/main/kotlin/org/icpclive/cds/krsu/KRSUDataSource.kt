package org.icpclive.cds.krsu

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.icpclive.api.*
import org.icpclive.cds.ContestDataSource
import org.icpclive.cds.ContestParseResult
import org.icpclive.cds.pcms.PCMSDataSource
import org.icpclive.service.RegularLoaderService
import org.icpclive.service.RunsBufferService
import org.icpclive.service.launchICPCServices
import org.icpclive.utils.*
import java.awt.Color
import java.io.IOException
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class KRSUDataSource(val properties: Properties) : ContestDataSource {

    override suspend fun run() {
        coroutineScope {
            val runsBufferFlow = MutableStateFlow<List<RunInfo>>(emptyList())
            val rawRunsFlow = reliableSharedFlow<RunInfo>()
            launch { RunsBufferService(runsBufferFlow, rawRunsFlow).run() }
            val contestInfoFlow = MutableStateFlow(ContestInfo.unknown())
            launchICPCServices(rawRunsFlow, contestInfoFlow)
            val (info, runs) = loadOnce()
            contestInfoFlow.value = info
            runsBufferFlow.value = runs
        }
    }

    override suspend fun loadOnce() = parseAndUpdateStandings(
        contestInfoLoader.loadOnce(), submissionsLoader.loadOnce()
    )

    val teams = mutableMapOf<String, TeamInfo>()
    var lastTeamId: Int = 0

    private fun parseAndUpdateStandings(contest: Contest, submissions: List<Submission>): ContestParseResult {
//        val startTime = submissions.map{it->it.ReceivedTime}.toList().min()
        val startTime = contest.StartTime

        val problemsList = contest.ProblemSet.mapIndexed { index, it ->
            ProblemInfo(
                "" + ('A' + index),
                "" + ('A' + index),
                Color.BLACK,
                it.Problem,
                index
            )
        }
//        val problemById = problemsList.associateBy { it.id }

        for (submission in submissions) {
            if (!teams.contains(submission.Login)) {
                teams[submission.Login] =
                    TeamInfo(
                        lastTeamId++,
                        submission.AuthorName,
                        submission.AuthorName,
                        submission.Login,
                        emptyList(),
                        null,
                        emptyMap()
                    )
            }
        }
        val runs = submissions.mapIndexed { index, it ->
            val result = outcomeMap.getOrDefault(it.StatusName, "")
            logger.info("" + (it.ReceivedTime - startTime))
            RunInfo(
                id = index,
                isAccepted = "AC" == result,
                isJudged = "" != result,
                isAddingPenalty = "AC" != result && "CE" != result,
                result = result,
                problemId = it.Problem,
                teamId = teams[it.Login]?.id ?: -1,
                percentage = if ("" == result) 0.0 else 1.0,
                time = it.ReceivedTime - startTime,
            )
        }.toList()

        return ContestParseResult(
            ContestInfo(
                ContestStatus.OVER,
                startTime,
                5.hours,
                4.hours,
                problemsList,
                teams.values.toList(),
            ),
            runs
        )
    }

    private val submissionsLoader = object : RegularLoaderService<List<Submission>>(null) {
        override val url = properties.getProperty("submissions-url")
        private val json = Json { ignoreUnknownKeys = true }
        override fun processLoaded(data: String) = try {
            json.decodeFromString<List<Submission>>(data)
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }

    private val contestInfoLoader = object : RegularLoaderService<Contest>(null) {
        override val url = properties.getProperty("contest-url")
        private val json = Json { ignoreUnknownKeys = true }
        override fun processLoaded(data: String) = try {
            json.decodeFromString<Contest>(data)
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }

    companion object {
        private val logger = getLogger(KRSUDataSource::class)
        private val outcomeMap = mapOf(
            "InternalError" to "FL",
            "Received" to "",
            "Compiling" to "",
            "Running" to "",
            "Compile Error" to "CE",
            "Run-Time Error" to "RE",
            "Time Limit Exceeded" to "TL",
            "Memory Limit Exceeded" to "ML",
            "Output Limit Exceeded" to "OL",
            "Security Violation" to "SV",
            "Wrong Answer" to "WA",
            "Accepted" to "AC",
            "Waiting For Compile" to "",
            "Waiting For Run" to "",
            "Presentation Error" to "PE",
            "Partial Solution" to "",
            "Rejected" to "",
            "Disqualified" to ""
        )
    }

    @Serializable
    class Submission(
        val Id: Int,
        val Login: String,
        val Problem: Int,
        val Letter: Int,
        val Target: String,
        val Status: Int,
        val StatusName: String,
        val TestPassed: Int,
        @Serializable(with = TimeSerializer::class)
        val ReceivedTime: Instant,
        val AuthorName: String,
    )

    @Serializable
    class Contest(
        val Id: Int,
        val ProblemSet: List<Problem>,
        @Serializable(with = TimeSerializer::class)
        val StartTime: Instant
    )

    @Serializable
    class Problem(
        val Letter: Int,
        val Problem: Int
    )

    class TimeSerializer : KSerializer<Instant> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("krsu time", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Instant {
            return Instant.parse(decoder.decodeString() + "Z")
        }

        override fun serialize(encoder: Encoder, value: Instant) {
            TODO("Not yet implemented")
        }

    }
}

