package com.starfall.core.model

import com.starfall.core.items.ArmorTemplate
import com.starfall.core.items.WeaponTemplate

/** Types of lootable items the player can collect. */
enum class ItemType(val displayName: String, val icon: String, val description: String) {
    // Core starter gear
    HEALING_POTION(
        displayName = "Healing Potion",
        icon = "🧪",
        description = "Restores 5 HP when consumed."
    ),
    EQUIPMENT_WEAPON(
        displayName = "Weapon",
        icon = "⚔️",
        description = "A weapon that can be equipped for extra damage."
    ),
    EQUIPMENT_ARMOR(
        displayName = "Armor",
        icon = "🛡️",
        description = "Protective armor that boosts defense and armor capacity."
    ),

    // Vision & awareness
    HOLLOWSIGHT_VIAL(
        displayName = "Hollowsight Vial",
        icon = "👁️",
        description = "+2 vision radius for 50 turns; uncommon early, common midgame."
    ),
    LUMENVEIL_ELIXIR(
        displayName = "Lumenveil Elixir",
        icon = "✨",
        description = "Reveals an 8–10 tile bubble of fog around you for 30 turns."
    ),
    STARSEERS_PHIAL(
        displayName = "Starseer's Phial",
        icon = "🌌",
        description = "See enemy silhouettes through walls within 8 tiles for 40 turns."
    ),
    VEILBREAKER_DRAUGHT(
        displayName = "Veilbreaker Draught",
        icon = "🗺️",
        description = "Instantly reveals the whole floor layout; single use."
    ),
    LANTERN_OF_ECHOES(
        displayName = "Lantern of Echoes",
        icon = "🏮",
        description = "For 25 turns, highlights traps and invisible entities you can see."
    ),

    // Combat buffs
    TITANBLOOD_TONIC(
        displayName = "Titanblood Tonic",
        icon = "💉",
        description = "+3 damage and +10% crit chance for 25 turns."
    ),
    IRONBARK_INFUSION(
        displayName = "Ironbark Infusion",
        icon = "🌳",
        description = "Gain a 15–25 point armor buffer until used or 40 turns pass."
    ),
    ASTRAL_SURGE_DRAUGHT(
        displayName = "Astral Surge Draught",
        icon = "🌠",
        description = "+50% astral damage and +1 mutation proc chance for 20 turns."
    ),
    VOIDRAGE_AMPOULE(
        displayName = "Voidrage Ampoule",
        icon = "🩸",
        description = "+50% damage dealt but lose 1–2 HP each turn for 20 turns."
    ),

    // Movement / escape
    PHASESTEP_DUST(
        displayName = "Phasestep Dust",
        icon = "💨",
        description = "Instantly blink 2 tiles in a chosen direction, ignoring blockers."
    ),
    SLIPSHADOW_OIL(
        displayName = "Slipshadow Oil",
        icon = "🛢️",
        description = "For 20 turns, enemies cannot body-block your movement."
    ),
    BLINKSTONE_SHARD(
        displayName = "Blinkstone Shard",
        icon = "💎",
        description = "Random short-range teleport within 6 tiles; can land near danger."
    ),

    // Offensive utility
    VOIDFLARE_ORB(
        displayName = "Voidflare Orb",
        icon = "🔥",
        description = "Thrown AoE dealing 8–12 astral/fire damage; breaks fragile walls."
    ),
    FROSTSHARD_ORB(
        displayName = "Frostshard Orb",
        icon = "❄️",
        description = "Thrown AoE that slows or roots foes for 5–8 turns with light damage."
    ),
    STARSPIKE_DART(
        displayName = "Starspike Dart",
        icon = "🎯",
        description = "High single-target damage that pierces or ignores some armor."
    ),
    GLOOMSMOKE_VESSEL(
        displayName = "Gloomsmoke Vessel",
        icon = "💨",
        description = "Creates a 10-turn smoke cloud reducing enemy accuracy and sight."
    ),

    // Debuff cures & protection
    VOIDBANE_SALTS(
        displayName = "Voidbane Salts",
        icon = "🧂",
        description = "Cures poison, rot, and bleed effects."
    ),
    LUMENPURE_DRAUGHT(
        displayName = "Lumenpure Draught",
        icon = "💫",
        description = "Cleanses all negatives and grants 3–5 turns of status immunity."
    ),
    HOLLOWGUARD_INFUSION(
        displayName = "Hollowguard Infusion",
        icon = "🛡️",
        description = "Halves corruption or hollowing gain for 40 turns."
    ),
    MINDWARD_CAPSULE(
        displayName = "Mindward Capsule",
        icon = "🧠",
        description = "+50% resistance to psychic, fear, and illusion effects for 30 turns."
    ),

    // Environmental / informational tools
    STARGAZERS_INK(
        displayName = "Stargazer's Ink",
        icon = "🖋️",
        description = "Permanently reveals a 12-tile radius around you on the map."
    ),
    ECHO_TUNED_COMPASS(
        displayName = "Echo-Tuned Compass",
        icon = "🧭",
        description = "For 60 turns, the minimap marks the direction of the stairs."
    ),
    VEILKEY_PICK(
        displayName = "Veilkey Pick",
        icon = "🗝️",
        description = "Single-use key that opens any locked door or chest."
    ),
    STARBOUND_HOOK(
        displayName = "Starbound Hook",
        icon = "🪝",
        description = "Grapple across pits or chasms to a tile within 3–4 tiles."
    ),

    // Starfall-specific cores
    ASTRAL_RESIDUE_PHIAL(
        displayName = "Astral Residue Phial",
        icon = "🔮",
        description = "30 turns of increased mutation gain and ability power."
    ),
    HOLLOW_SHARD_FRAGMENT(
        displayName = "Hollow Shard Fragment",
        icon = "🪨",
        description = "15-turn massive mitigation boost but adds permanent corruption."
    ),
    TITAN_EMBER_GRANULE(
        displayName = "Titan Ember Granule",
        icon = "🔥",
        description = "Sacrifice 10% current HP to restore astral resource and gain a core shard."
    ),
    LUNAR_ECHO_VIAL(
        displayName = "Lunar Echo Vial",
        icon = "🌙",
        description = "Guarantees your next mutation choice rerolls once."
    ),

    // Boss uniques - Fallen Astromancer
    STARHEART_FOCUS(
        displayName = "Starheart Focus",
        icon = "⭐",
        description = "Offhand that boosts ability damage, crit chance, and mutation proc odds."
    ),
    CELESTIAL_PRISM(
        displayName = "Celestial Prism",
        icon = "📿",
        description = "Accessory that periodically grants small random buffs to offense or speed."
    ),
    COSMIC_ECHO_MUTATION(
        displayName = "Mutation: Cosmic Echo",
        icon = "🌌",
        description = "Attacks may echo for 20–40% astral damage."
    ),

    // Boss uniques - Bone-forged Colossus
    COLOSSUS_PLATE(
        displayName = "Colossus Plate",
        icon = "🦴",
        description = "Heavy armor with strong defense and a chance to ignore incoming hits."
    ),
    RIBBREAKER_MAUL(
        displayName = "Ribbreaker Maul",
        icon = "🔨",
        description = "Slow brutal weapon that deals extra damage to armored foes."
    ),
    GRAVE_RESILIENCE_MUTATION(
        displayName = "Mutation: Grave Resilience",
        icon = "☠️",
        description = "At low HP, gain a temporary bone shield that absorbs damage."
    ),

    // Boss uniques - Blighted Hive-Mind
    HIVE_QUEEN_MANDIBLE(
        displayName = "Hive Queen's Mandible",
        icon = "🪲",
        description = "Weapon with poison on hit; poison scales with your mutations."
    ),
    INFECTED_SPINE(
        displayName = "Infected Spine",
        icon = "🧬",
        description = "Trinket that speeds you up when near enemies."
    ),
    VIRAL_MUTATION_MUTATION(
        displayName = "Mutation: Viral Mutation",
        icon = "🦠",
        description = "Your status effects can spread to nearby foes."
    ),

    // Boss uniques - Echo Knight Remnant
    BROKEN_ECHO_BLADE(
        displayName = "Broken Echo Blade",
        icon = "🗡️",
        description = "Fast blade with a chance to duplicate strikes for bonus damage."
    ),
    SHADOWGUARD_MANTLE(
        displayName = "Shadowguard Mantle",
        icon = "🧥",
        description = "Cloak that grants a brief dodge window after being hit."
    ),
    TEMPORAL_BLUR_MUTATION(
        displayName = "Mutation: Temporal Blur",
        icon = "⏳",
        description = "Small chance to automatically dodge incoming attacks."
    ),

    // Boss uniques - Heartstealer Wyrm
    WYRMFANG_DAGGER(
        displayName = "Wyrmfang Dagger",
        icon = "🩸",
        description = "Rapid dagger that heals for a portion of damage dealt."
    ),
    HEARTFORGE_CORE(
        displayName = "Heartforge Core",
        icon = "❤️",
        description = "Core that grants minor regeneration and boosts all healing."
    ),
    SANGUINE_BURST_MUTATION(
        displayName = "Mutation: Sanguine Burst",
        icon = "💥",
        description = "Slain enemies release a small healing nova that scales with max HP."
    );
}

/** Represents an item that can live on the ground or in the player's inventory. */
data class Item(
    val id: Int,
    val type: ItemType,
    var position: Position? = null,
    var isEquipped: Boolean = false,
    val weaponTemplate: WeaponTemplate? = null,
    val armorTemplate: ArmorTemplate? = null,
    val quantity: Int = 1,
    val inventoryIndex: Int = -1
) {
    val displayName: String
        get() = weaponTemplate?.name ?: armorTemplate?.name ?: type.displayName

    val description: String
        get() = when {
            weaponTemplate != null -> "Base damage ${weaponTemplate.baseDamage} (${weaponTemplate.material.displayName} ${weaponTemplate.type.displayName})."
            armorTemplate != null -> "Armor capacity ${armorTemplate.armorCapacity} (${armorTemplate.weight.displayName} ${armorTemplate.material.displayName} ${armorTemplate.slot.displayName})."
            else -> type.description
        }

    val icon: String
        get() = type.icon

    fun isStackable(): Boolean = type != ItemType.EQUIPMENT_WEAPON && type != ItemType.EQUIPMENT_ARMOR

    fun canStackWith(other: Item): Boolean {
        if (!isStackable() || !other.isStackable()) return false
        if (type != other.type) return false
        if (weaponTemplate != other.weaponTemplate) return false
        if (armorTemplate != other.armorTemplate) return false
        return true
    }
}
