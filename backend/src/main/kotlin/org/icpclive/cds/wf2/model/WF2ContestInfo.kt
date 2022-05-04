package org.icpclive.cds.wf2.model

import kotlinx.datetime.Instant
import org.icpclive.api.ContestStatus
import org.icpclive.cds.ContestInfo
import org.icpclive.cds.ProblemInfo
import org.icpclive.cds.TeamInfo
import kotlin.time.Duration

open class WF2ContestInfo(
    override val problemsNumber: Int,
    override val teamsNumber: Int,
    override val problems: List<ProblemInfo>,
    final override val teams: List<TeamInfo>,
    override val contestTime: Duration
) : ContestInfo(Instant.fromEpochMilliseconds(0), ContestStatus.BEFORE) {

    private val participantsByName = teams.groupBy { it.name }.mapValues { it.value.first() }
    override fun getParticipant(name: String) = participantsByName[name]
    private val participantsById = teams.groupBy { it.id }.mapValues { it.value.first() }
    override fun getParticipant(id: Int) = participantsById[id]
    private val participantsByHashtag = teams.groupBy { it.hashTag }.mapValues { it.value.first() }
    override fun getParticipantByHashTag(hashTag: String) = participantsByHashtag[hashTag]
}
