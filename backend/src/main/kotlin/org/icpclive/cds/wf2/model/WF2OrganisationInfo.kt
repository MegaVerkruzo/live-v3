package org.icpclive.cds.wf2.model

data class WF2OrganisationInfo(
    val id: String,
    val name: String,
    val formalName: String,
    val country: String?,
    val countryFlag: String?,
    val logo: String?,
    val hashtag: String?
)
