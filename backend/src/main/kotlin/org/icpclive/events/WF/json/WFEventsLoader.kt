package org.icpclive.events.WF.json

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.icpclive.Config.loadProperties
import org.icpclive.api.ContestStatus
import org.icpclive.events.ContestInfo
import org.icpclive.events.EventsLoader
import org.icpclive.events.NetworkUtils.openAuthorizedStream
import org.icpclive.events.NetworkUtils.prepareNetwork
import org.icpclive.events.WF.WFOrganizationInfo
import org.icpclive.events.WF.WFRunInfo
import org.icpclive.events.WF.WFTestCaseInfo
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.*
import java.math.BigInteger
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Created by aksenov on 16.04.2015.
 */
class WFEventsLoader(regionals: Boolean) : EventsLoader() {
    private var url: String? = null
    private var login: String? = null
    private var password: String? = null
    private var regionals = false
    private var emulation = false
    override val contestData: ContestInfo?
        get() = contestInfo

    @Throws(IOException::class)
    fun readJsonArray(url: String?): String {
        val br = BufferedReader(
            InputStreamReader(
                openAuthorizedStream(url!!, login, password!!)
            )
        )
        var json = ""
        var line: String
        while (br.readLine().also { line = it } != null) {
            json += line.trim { it <= ' ' }
        }
        return json
    }

    @Throws(IOException::class)
    private fun readGroupsInfo(contest: WFContestInfo) {
        val jsonGroups = Gson().fromJson(
            readJsonArray("$url/groups"), JsonArray::class.java
        )
        contest.groupById = HashMap()
        for (i in 0 until jsonGroups.size()) {
            val je = jsonGroups[i].asJsonObject
            val id = je["id"].asString
            val name = je["name"].asString
            contest.groupById!![id] = name
            ContestInfo.GROUPS.add(name)
        }
    }

    @Throws(IOException::class)
    private fun readProblemInfos(contest: WFContestInfo) {
        val jsonProblems = Gson().fromJson(
            readJsonArray("$url/problems"), JsonArray::class.java
        )
        contest.problems = ArrayList()
        contest.problemById = HashMap()
        contest.problemById = HashMap()
        val problems = arrayOfNulls<WFProblemInfo>(jsonProblems.size())
        for (i in 0 until jsonProblems.size()) {
            val je = jsonProblems[i].asJsonObject
            val problemInfo = WFProblemInfo(contest.languages.size)
            val cdsId = je["id"].asString
            problemInfo.name = je["name"].asString
            problemInfo.id = je["ordinal"].asInt
            problemInfo.color = Color.decode(je["rgb"].asString)
            if (je["test_data_count"] == null) {
                // TODO
                problemInfo.testCount = 100
            } else {
                problemInfo.testCount = je["test_data_count"].asInt
            }
            problemInfo.letter = je["label"].asString
            problems[i] = problemInfo
            contest.problemById[cdsId] = problemInfo
        }
        Arrays.sort(problems) { a: WFProblemInfo?, b: WFProblemInfo? -> a!!.id - b!!.id }
        for (i in problems.indices) {
            problems[i]!!.id = i
            contest.problems.add(problems[i]!!)
        }
    }

    internal class Organization(
        val formalName: String,
        val shortName: String,
        val hashTag: String?,
        private val id: String
    )

    @Throws(IOException::class)
    private fun readTeamInfosWF(contest: WFContestInfo) {
        val jsonOrganizations = Gson().fromJson(
            readJsonArray("$url/organizations"), JsonArray::class.java
        )
        val organizations = HashMap<String, Organization>()
        for (i in 0 until jsonOrganizations.size()) {
            val je = jsonOrganizations[i].asJsonObject
            // TODO
            val name = je["formal_name"].asString
            val shortName = je["name"].asString
            val hashTag = if (je["twitter_hashtag"] == null) null else je["twitter_hashtag"].asString
            val id = je["id"].asString
            organizations[id] = Organization(name, shortName, hashTag, id)
        }
        val jsonTeams = Gson().fromJson(
            readJsonArray("$url/teams"), JsonArray::class.java
        )
        contest.teamById = HashMap()
        contest.teamInfos = arrayOfNulls(jsonTeams.size())
        for (i in 0 until jsonTeams.size()) {
            val je = jsonTeams[i].asJsonObject
            if (je["organization_id"].isJsonNull) {
                continue
            }
            val teamOrg = organizations[je["organization_id"].asString]
            val teamInfo = WFTeamInfo(contest.problems.size)
            teamInfo.shortName = teamOrg!!.shortName
            teamInfo.name = teamOrg.formalName
            teamInfo.hashTag = teamOrg.hashTag
            val groups = je["group_ids"].asJsonArray
            for (j in 0 until groups.size()) {
                val groupId = groups[j].asString
                val group = contest.groupById!![groupId]
                teamInfo.groups.add(group!!)
            }
            if (je["desktop"] != null) {
                val hrefs = je["desktop"].asJsonArray
                teamInfo.screens = hrefs.map { it.asJsonObject["href"].asString }.toTypedArray()
            }
            if (je["webcam"] != null) {
                val hrefs = je["webcam"].asJsonArray
                teamInfo.cameras = hrefs.map {
                    it.asJsonObject["href"].asString!!
                }.toTypedArray()
            }
            teamInfo.alias = je["id"].asString
            contest.teamById[teamInfo.alias] = teamInfo
            contest.teamInfos[i] = teamInfo
        }
        Arrays.sort(contest.teamInfos) { a: org.icpclive.events.WF.WFTeamInfo?, b: org.icpclive.events.WF.WFTeamInfo? ->
            compareAsNumbers(
                (a as WFTeamInfo?)!!.alias, (b as WFTeamInfo?)!!.alias
            )
        }
        for (i in contest.teamInfos.indices) {
            contest.teamInfos[i]!!.id = i
        }
    }

    @Throws(IOException::class)
    private fun readTeamInfosRegionals(contest: WFContestInfo) {
        val jsonOrganizations = Gson().fromJson(
            readJsonArray("$url/organizations"), JsonArray::class.java
        )
        val organizations = HashMap<String, WFOrganizationInfo>()
        for (i in 0 until jsonOrganizations.size()) {
            val je = jsonOrganizations[i].asJsonObject
            val organizationInfo = WFOrganizationInfo()
            // TODO
            organizationInfo.formalName = je["formal_name"].asString
            organizationInfo.name = je["name"].asString
            organizations[je["id"].asString] = organizationInfo
        }
        val jsonTeams = Gson().fromJson(
            readJsonArray("$url/teams"), JsonArray::class.java
        )
        contest.teamInfos = arrayOfNulls(jsonTeams.size())
        contest.teamById = HashMap()
        for (i in 0 until jsonTeams.size()) {
            val je = jsonTeams[i].asJsonObject
            if (je["organization_id"].isJsonNull) {
                continue
            }
            val teamInfo = WFTeamInfo(contest.problems.size)
            val organizationInfo = organizations[je["organization_id"].asString]
            teamInfo.name = organizationInfo!!.name + ": " + je["name"].asString
            teamInfo.shortName = shortName(teamInfo.name)!!
            val groups = je["group_ids"].asJsonArray
            for (j in 0 until groups.size()) {
                val groupId = groups[j].asString
                val group = contest.groupById!![groupId]
                teamInfo.groups.add(group!!)
            }
            if (je["desktop"] != null) {
                val hrefs = je["desktop"].asJsonArray
                teamInfo.screens = hrefs.map { it.asJsonObject["href"].asString!! }.toTypedArray()
            }
            if (je["webcam"] != null) {
                val hrefs = je["webcam"].asJsonArray
                teamInfo.cameras = hrefs.map { it.asJsonObject["href"].asString!! }.toTypedArray()
            }
            teamInfo.alias = je["id"].asString
            contest.teamById[teamInfo.alias] = teamInfo
            contest.teamInfos[i] = teamInfo
        }
        Arrays.sort(contest.teamInfos) { a: org.icpclive.events.WF.WFTeamInfo?, b: org.icpclive.events.WF.WFTeamInfo? ->
            compareAsNumbers(
                (a as WFTeamInfo?)!!.alias, (b as WFTeamInfo?)!!.alias
            )
        }
        for (i in contest.teamInfos.indices) {
            contest.teamInfos[i]!!.id = i
        }
    }

    @Throws(IOException::class)
    fun readLanguagesInfos(contestInfo: WFContestInfo) {
        val jsonLanguages = Gson().fromJson(
            readJsonArray("$url/languages"), JsonArray::class.java
        )
        contestInfo.languages = arrayOfNulls(jsonLanguages.size())
        contestInfo.languageById = HashMap()
        for (i in 0 until jsonLanguages.size()) {
            val je = jsonLanguages[i].asJsonObject
            val languageInfo = WFLanguageInfo()
            val cdsId = je["id"].asString
            languageInfo.name = je["name"].asString
            contestInfo.languages[i] = languageInfo.name
            contestInfo.languageById[cdsId] = languageInfo
        }
    }

    @Throws(IOException::class)
    private fun initialize(): WFContestInfo {
        val contestInfo = WFContestInfo()
        readGroupsInfo(contestInfo)
        System.err.println("Groups")
        readLanguagesInfos(contestInfo)
        System.err.println("lanugage")
        readProblemInfos(contestInfo)
        System.err.println("problem")
        if (regionals) {
            readTeamInfosRegionals(contestInfo)
        } else {
            readTeamInfosWF(contestInfo)
        }
        contestInfo.initializationFinish()
        log.info("Problems " + contestInfo.problems.size + ", teamInfos " + contestInfo.teamInfos.size)
        contestInfo.recalcStandings()
        return contestInfo
    }

    @Throws(IOException::class)
    fun reinitialize() {
        val contestInfo = WFContestInfo()
        readGroupsInfo(contestInfo)
        readLanguagesInfos(contestInfo)
        readProblemInfos(contestInfo)
        readTeamInfosRegionals(contestInfo)
        contestInfo.initializationFinish()
        contestInfo.status = ContestStatus.RUNNING
        contestInfo.startTime = this.contestInfo.startTime
        contestInfo.recalcStandings()
        this.contestInfo = contestInfo
    }

    fun parseTime(time: String): Long {
        val zdt = ZonedDateTime.parse("$time:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        //        ZonedDateTime zdt = ZonedDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return zdt.toInstant().toEpochMilli()
        //        LocalDateTime ldt = LocalDateTime.parse(time + ":00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
//        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    fun parseRelativeTime(time: String): Long {
        val z = time.split("\\.").toTypedArray()
        val t = z[0].split(":").toTypedArray()
        val h = t[0].toInt()
        val m = t[1].toInt()
        val s = t[2].toInt()
        val ms = if (z.size == 1) 0 else z[1].toInt()
        return (((h * 60 + m) * 60 + s) * 1000 + ms).toLong()
    }

    fun readContest(contestInfo: WFContestInfo, je: JsonObject) {
        val startTimeElement = je["start_time"]
        if (!startTimeElement.isJsonNull) {
            contestInfo.startTime = parseTime(startTimeElement.asString)
            contestInfo.status = ContestStatus.RUNNING
        } else {
            contestInfo.status = ContestStatus.BEFORE
        }
        if (emulation) {
            contestInfo.startTime = System.currentTimeMillis()
        }
        ContestInfo.CONTEST_LENGTH = parseRelativeTime(je["duration"].asString).toInt()
        ContestInfo.FREEZE_TIME = ContestInfo.CONTEST_LENGTH - parseRelativeTime(
            je["scoreboard_freeze_duration"].asString
        ).toInt()
    }

    fun readState(contestInfo: WFContestInfo, je: JsonObject) {
        if (je["started"].isJsonNull) {
            return
        }
        val startTime = je["started"].asString
        contestInfo.startTime = parseTime(startTime)
        if (emulation) {
            contestInfo.startTime = System.currentTimeMillis()
        }
        if (je["ended"].isJsonNull) {
            contestInfo.status = ContestStatus.RUNNING
        } else {
            contestInfo.status = ContestStatus.OVER
        }
    }

    var firstRun = true
    fun waitForEmulation(time: Long) {
        if (emulation) {
            try {
//                if (firstRun) {
//                    contestInfo.setStartTime((long) (contestInfo.getStartTime() - emulationStartTime * 60000 / emulationSpeed));
//                    firstRun = false;
//                }
                val dt = ((time - contestInfo!!.currentTime) / emulationSpeed).toLong()
                //System.err.println("wait for " + dt + " ms");
                if (dt > 0) Thread.sleep(dt)
            } catch (e: InterruptedException) {
                log.error("error", e)
            }
        }
    }

    fun readSubmission(contestInfo: WFContestInfo, je: JsonObject, update: Boolean) {
        waitForEmulation(parseRelativeTime(je["contest_time"].asString))
        if (update) {
            return
        }
        val run = WFRunInfo()
        val cdsId = je["id"].asString
        val languageInfo = contestInfo.languageById[je["language_id"].asString]
        run.languageId = languageInfo!!.id
        val problemInfo = contestInfo.problemById[je["problem_id"].asString]
        run.problemId = problemInfo!!.id
        val teamInfo = contestInfo.teamById[je["team_id"].asString] as WFTeamInfo?
        run.teamId = teamInfo!!.id
        run.team = teamInfo
        run.time = parseRelativeTime(je["contest_time"].asString)
        run.lastUpdateTime = run.time
        contestInfo.addRun(run)
        contestInfo.runBySubmissionId[cdsId] = run
    }

    fun readJudgement(contestInfo: WFContestInfo, je: JsonObject) {
        val cdsId = je["id"].asString
        val runInfo = contestInfo.runBySubmissionId[je["submission_id"].asString]
        if (runInfo == null) {
            System.err.println("FAIL! $je")
            return
        }
        contestInfo.runByJudgementId[cdsId] = runInfo
        val verdictElement = je["judgement_type_id"]
        val verdict = if (verdictElement.isJsonNull) "" else verdictElement.asString
        log.info("Judging " + contestInfo.getParticipant(runInfo.teamId) + " " + verdict)
        if (verdictElement.isJsonNull) {
            runInfo.isJudged = false
            runInfo.result = ""
            waitForEmulation(parseRelativeTime(je["start_contest_time"].asString))
            return
        }
        val time = if (je["end_contest_time"].isJsonNull) 0 else parseRelativeTime(je["end_contest_time"].asString)
        waitForEmulation(time)
        if (runInfo.time <= ContestInfo.FREEZE_TIME) {
            runInfo.result = verdict
            runInfo.isJudged = true

//            long start = System.currentTimeMillis();
            contestInfo.recalcStandings()
            //            contestInfo.checkStandings(url, login, password);
//            log.info("Standing are recalculated in " + (System.currentTimeMillis() - start) + " ms");
        } else {
            runInfo.isJudged = false
        }
        runInfo.lastUpdateTime = time
    }

    fun readRun(contestInfo: WFContestInfo, je: JsonObject, update: Boolean) {
        if (je["judgement_id"].isJsonNull) {
            System.err.println(je)
            return
        }
        val runInfo = contestInfo.runByJudgementId[je["judgement_id"].asString]
        val time = parseRelativeTime(je["contest_time"].asString)
        waitForEmulation(time)
        if (runInfo == null || runInfo.time > ContestInfo.FREEZE_TIME || update) {
            return
        }
        val testCaseInfo = WFTestCaseInfo()
        testCaseInfo.id = je["ordinal"].asInt
        testCaseInfo.result = je["judgement_type_id"].asString
        testCaseInfo.time = time
        testCaseInfo.timestamp = parseTime(je["time"].asString).toDouble()
        testCaseInfo.runId = runInfo.id
        testCaseInfo.total = contestInfo.getProblemById(runInfo.problemId).testCount

//        System.err.println(runInfo);
        contestInfo.addTest(testCaseInfo)
    }

    override fun run() {
        var lastEvent: String? = null
        var initialized = false
        while (true) {
            try {
                BufferedReader(
                    InputStreamReader(
                        openAuthorizedStream(url + "/event-feed", login, password!!),
                        "utf-8"
                    )
                ).use { br ->
                    val abortedEvent = lastEvent
                    lastEvent = null
                    val contestInfo = initialize()
                    if (abortedEvent == null) {
                        this.contestInfo = contestInfo
                    }
                    System.err.println("Aborted event $abortedEvent")
                    while (true) {
                        val line = br.readLine() ?: break

//                    System.err.println(line);
                        val je = Gson().fromJson(line, JsonObject::class.java)
                        if (je == null) {
                            log.info("Non-json line")
                            System.err.println("Non-json line: " + Arrays.toString(line.toCharArray()))
                            continue
                        }
                        val id = je["id"].asString.substring(3)
                        if (id == abortedEvent) {
                            this.contestInfo = contestInfo
                        }
                        lastEvent = id
                        val update = je["op"].asString != "create"
                        val type = je["type"].asString
                        val json = je["data"].asJsonObject
                        synchronized(GLOBAL_LOCK) {
                            when (type) {
                                "contests" -> readContest(contestInfo, json)
                                "state" -> readState(contestInfo, json)
                                "submissions" -> readSubmission(contestInfo, json, update)
                                "judgements" -> readJudgement(contestInfo, json)
                                "runs" -> readRun(contestInfo, json, update)
                                "problems" -> if (!update && !initialized) {
                                    initialized = true
                                    throw Exception("Problems weren't loaded, exception to restart feed")
                                }
                                else -> {}
                            }
                        }
                    }
                    return
                }
            } catch (e: Throwable) {
                log.error("error", e)
                try {
                    Thread.sleep(2000)
                } catch (e1: InterruptedException) {
                    log.error("error", e1)
                }
                log.info("Restart event feed")
                System.err.println("Restarting feed")
            }
        }
    }

    @Volatile
    private lateinit var contestInfo: WFContestInfo

    init {
        try {
            val properties = loadProperties("events")
            login = properties.getProperty("login")
            password = properties.getProperty("password")
            prepareNetwork(login, password)

            // in format https://example.com/api/contests/wf14/
            url = properties.getProperty("url")
            emulationSpeed = properties.getProperty("emulation.speed", "1").toDouble()
            emulationStartTime = properties.getProperty("emulation.startTime", "0").toLong()
            if (!(url!!.startsWith("http") || url!!.startsWith("https"))) {
                emulation = true
            } else {
                emulationSpeed = 1.0
            }
            this.regionals = regionals
            this.contestInfo = initialize()
        } catch (e: IOException) {
            log.error("error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WFEventsLoader::class.java)
        val GLOBAL_LOCK = Any()

        private fun compareAsNumbers(a: String, b: String): Int {
            for (i in 0 until Math.min(a.length, b.length)) {
                if (a[i] != b[i]) {
                    val aDigit = Character.isDigit(a[i])
                    val bDigit = Character.isDigit(b[i])
                    return if (!aDigit) {
                        if (!bDigit) {
                            Character.compare(a[i], b[i])
                        } else {
                            if (i > 0 && Character.isDigit(a[i - 1])) {
                                -1
                            } else Character.compare(a[i], b[i])
                        }
                    } else {
                        if (!bDigit) {
                            if (i > 0 && Character.isDigit(a[i - 1])) {
                                1
                            } else Character.compare(
                                a[i],
                                b[i]
                            )
                        } else {
                            var aTo = i + 1
                            while (aTo < a.length && Character.isDigit(a[aTo])) {
                                aTo++
                            }
                            var bTo = i + 1
                            while (bTo < b.length && Character.isDigit(b[bTo])) {
                                bTo++
                            }
                            if (aTo != bTo) {
                                Integer.compare(aTo, bTo)
                            } else BigInteger(a.substring(i, aTo)).compareTo(BigInteger(b.substring(i, bTo)))
                        }
                    }
                }
            }
            return Integer.compare(a.length, b.length)
        }

        // public static ArrayBlockingQueue<RunInfo> getAllRuns() {
        var shortNames: MutableMap<String, String?> = HashMap()

        init {
            try {
                val properties = Properties()
                properties.load(WFEventsLoader::class.java.classLoader.getResourceAsStream("events.properties"))
                val override = File(
                    properties.getProperty(
                        "teamInfos.shortnames.override",
                        "override.txt"
                    )
                )
                if (override.exists()) {
                    val `in` = BufferedReader(FileReader("override.txt"))
                    var line: String
                    while (`in`.readLine()
                            .also { line = it } != null
                    ) {
                        val ss: Array<String> = line.split("\t").toTypedArray()
                        shortNames[ss.get(0)] = ss.get(1)
                    }
                }
            } catch (e: Exception) {
                log.error("error", e)
            }
        }

        fun shortName(name: String): String? {
            assert(shortNames[name] == null)
            return if (shortNames.containsKey(name)) {
                shortNames[name]
            } else if (name.length > 22) {
                name.substring(0, 19) + "..."
            } else {
                name
            }
        }
    }
}