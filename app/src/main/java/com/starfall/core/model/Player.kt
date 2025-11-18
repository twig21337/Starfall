package com.starfall.core.model

/** The player-controlled hero. */
class Player(
    id: Int,
    name: String,
    position: Position,
    glyph: Char,
    stats: Stats,
    var level: Int = 1,
    var experience: Int = 0
) : Entity(id, name, position, glyph, true, stats) {
    /** Awards experience and performs a simple level-up check. */
    fun gainExperience(amount: Int) {
        if (amount <= 0) return
        experience += amount
        var needed = level * 10
        while (experience >= needed) {
            experience -= needed
            level += 1
            stats.maxHp += 5
            stats.hp = stats.maxHp
            stats.attack += 1
            needed = level * 10
        }
    }
}
