package com.starfall.core.progression

/**
 * PlayerProfile represents long-term player information used to seed new runs.
 * Additional progression systems can attach data here as they come online.
 */
data class PlayerProfile(
    val id: String = "profile-default",
    val name: String = "Starfarer",
    val metaProgressionState: MetaProgressionState = MetaProgressionState()
)
