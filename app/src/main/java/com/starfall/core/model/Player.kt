package com.starfall.core.model

import com.starfall.core.items.ArmorSlot
import com.starfall.core.mutation.MutationState
import com.starfall.core.progression.XpManager
import kotlin.math.max
import kotlin.random.Random

/** The player-controlled hero. */
class Player(
    id: Int,
    name: String,
    position: Position,
    glyph: Char,
    stats: Stats,
    var level: Int = 1,
    var experience: Int = 0,
    val inventory: MutableList<Item> = mutableListOf(),
    var equippedWeaponId: Int? = null,
    val equippedArmorBySlot: MutableMap<ArmorSlot, Int> = mutableMapOf(),
    val activeEffects: MutableList<PlayerEffect> = mutableListOf(),
    val mutationState: MutationState = MutationState()
) : Entity(id, name, position, glyph, true, stats) {
    private val baseDefense = stats.defense
    /**
     * Legacy shim to keep existing callers working. Prefer [XpManager.gainXp]
     * so mutation rolls and the updated XP curve are respected.
     */
    @Deprecated("Use XpManager for XP handling")
    fun gainExperience(amount: Int) {
        XpManager(this).gainXp(amount)
    }

    fun addItem(item: Item): Boolean {
        val incoming = item.copy(position = null, isEquipped = false)
        val stackIndex = inventory.indexOfFirst { it.canStackWith(incoming) }
        if (stackIndex >= 0 && incoming.isStackable()) {
            val existing = inventory[stackIndex]
            inventory[stackIndex] = existing.copy(quantity = existing.quantity + incoming.quantity)
            reindexInventory()
            return true
        }

        if (inventory.size >= currentInventorySlots()) {
            return false
        }

        inventory.add(incoming)
        reindexInventory()
        return true
    }

    fun removeItem(itemId: Int) {
        removeItemQuantity(itemId, Int.MAX_VALUE)
    }

    fun removeItemQuantity(itemId: Int, quantity: Int): Int {
        if (quantity <= 0) return 0
        val index = inventory.indexOfFirst { it.id == itemId }
        if (index == -1) return 0

        val item = inventory[index]
        val amountToRemove = minOf(quantity, item.quantity)
        val remaining = item.quantity - amountToRemove

        if (remaining <= 0) {
            if (equippedWeaponId == itemId) {
                unequipWeapon(item)
            }
            equippedArmorBySlot.entries
                .firstOrNull { it.value == itemId }
                ?.let { unequipArmor(item) }
            inventory.removeAt(index)
        } else {
            inventory[index] = item.copy(quantity = remaining)
        }

        reindexInventory()
        return amountToRemove
    }

    fun breakEquippedArmor() {
        val armorId = equippedArmorBySlot.values.firstOrNull() ?: return
        val armorItem = inventory.firstOrNull { it.id == armorId } ?: return
        unequipArmor(armorItem)
        removeItem(armorId)
        reindexInventory()
    }

    fun heal(amount: Int): Int = stats.heal(amount)

    fun hasEffect(type: PlayerEffectType): Boolean =
        activeEffects.any { it.type == type }

    fun equip(itemId: Int): Boolean {
        val item = inventory.firstOrNull { it.id == itemId } ?: return false
        val equipped = when {
            item.type == ItemType.EQUIPMENT_WEAPON || item.weaponTemplate != null -> equipOrUnequipWeapon(item)
            item.type == ItemType.EQUIPMENT_ARMOR || item.armorTemplate != null -> equipOrUnequipArmor(item)
            else -> false
        }
        if (equipped) {
            reindexInventory()
        }
        return equipped
    }

    fun consumePotion(itemId: Int, healAmount: Int): Int {
        val potion = inventory.firstOrNull { it.id == itemId && it.type == ItemType.HEALING_POTION }
            ?: return 0
        val healed = heal(healAmount)
        decrementStackOrRemove(potion)
        reindexInventory()
        return healed
    }

    fun inventorySnapshot(): List<Item> {
        reindexInventory()
        return inventory.map { it.copy() }
    }

    private fun decrementStackOrRemove(item: Item) {
        val index = inventory.indexOfFirst { it.id == item.id }
        if (index == -1) return
        val existing = inventory[index]
        val newQuantity = existing.quantity - 1
        if (newQuantity > 0) {
            inventory[index] = existing.copy(quantity = newQuantity)
        } else {
            inventory.removeAt(index)
        }
        reindexInventory()
    }

    private fun equipOrUnequipWeapon(item: Item): Boolean {
        if (equippedWeaponId == item.id) {
            unequipWeapon(item)
            markEquippedState(item, null, equippedArmorBySlot.values.toSet())
            reindexInventory()
            return true
        }
        val existingWeapon = equippedWeaponId?.let { id -> inventory.firstOrNull { it.id == id } }
        existingWeapon?.let { stats.attack -= weaponAttackBonus(it) }

        equippedWeaponId = item.id
        stats.attack += weaponAttackBonus(item)
        markEquippedState(item, equippedWeaponId, equippedArmorBySlot.values.toSet())
        reindexInventory()
        return true
    }

    private fun equipOrUnequipArmor(item: Item): Boolean {
        val slot = item.armorTemplate?.slot ?: return false
        val existingId = equippedArmorBySlot[slot]
        if (existingId == item.id) {
            unequipArmor(item)
            markEquippedState(item, equippedWeaponId, equippedArmorBySlot.values.toSet())
            reindexInventory()
            return true
        }

        existingId?.let { current ->
            inventory.firstOrNull { it.id == current }?.let { equipped ->
                stats.defense = max(0, stats.defense - armorDefenseBonus(equipped))
                val newMax = max(0, stats.maxArmor - armorCapacity(equipped))
                stats.maxArmor = newMax
                stats.armor = stats.armor.coerceAtMost(newMax)
            }
        }

        equippedArmorBySlot[slot] = item.id
        stats.defense = baseDefense + equippedArmorBySlot.values.sumOf { id ->
            inventory.firstOrNull { it.id == id }?.let { armorDefenseBonus(it) } ?: 0
        }
        stats.maxArmor += armorCapacity(item)
        stats.armor = stats.maxArmor
        markEquippedState(item, equippedWeaponId, equippedArmorBySlot.values.toSet())
        reindexInventory()
        return true
    }

    private fun markEquippedState(item: Item, weaponId: Int?, armorIds: Set<Int>) {
        inventory.replaceAll { invItem ->
            when (invItem.type) {
                ItemType.EQUIPMENT_WEAPON -> invItem.copy(isEquipped = invItem.id == weaponId)
                ItemType.EQUIPMENT_ARMOR -> invItem.copy(isEquipped = armorIds.contains(invItem.id))
                else -> invItem.copy(isEquipped = false)
            }
        }
    }

    private fun reindexInventory() {
        inventory.replaceAll { it.copy(inventoryIndex = -1) }
        inventory.forEachIndexed { idx, item ->
            inventory[idx] = item.copy(inventoryIndex = idx)
        }
    }

    private fun weaponAttackBonus(item: Item): Int =
        item.weaponTemplate?.baseDamage ?: 0

    private fun armorDefenseBonus(item: Item): Int =
        item.armorTemplate?.let { max(1, it.material.tierIndex / 2) } ?: 0

    private fun armorCapacity(item: Item): Int =
        item.armorTemplate?.armorCapacity ?: 0

    private fun unequipWeapon(item: Item) {
        stats.attack -= weaponAttackBonus(item)
        equippedWeaponId = null
    }

    private fun unequipArmor(item: Item) {
        stats.defense = max(0, stats.defense - armorDefenseBonus(item))
        val newMaxArmor = max(0, stats.maxArmor - armorCapacity(item))
        stats.maxArmor = newMaxArmor
        stats.armor = stats.armor.coerceAtMost(newMaxArmor)
        item.armorTemplate?.slot?.let { equippedArmorBySlot.remove(it) }
    }

    fun hasEquippedArmor(): Boolean = equippedArmorBySlot.isNotEmpty()

    fun currentInventorySlots(): Int = BASE_INVENTORY_SLOTS + mutationState.bonusInventorySlots

    fun addEffect(effect: PlayerEffect) {
        val existing = activeEffects.firstOrNull { it.type == effect.type }
        if (existing != null) {
            if (existing.type == PlayerEffectType.IRONBARK_SHIELD || existing.type == PlayerEffectType.HOLLOW_SHARD_BARRIER) {
                stats.maxArmor = max(0, stats.maxArmor - existing.magnitude)
                stats.armor = stats.armor.coerceAtMost(stats.maxArmor)
            }
        }
        activeEffects.removeAll { it.type == effect.type }
        if (effect.type == PlayerEffectType.IRONBARK_SHIELD) {
            val buffer = effect.magnitude
            stats.maxArmor += buffer
            stats.armor += buffer
        }
        if (effect.type == PlayerEffectType.HOLLOW_SHARD_BARRIER) {
            val buffer = effect.magnitude
            stats.maxArmor += buffer
            stats.armor += buffer
        }
        activeEffects += effect
    }

    fun tickEffects(): List<String> {
        val messages = mutableListOf<String>()
        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            if (effect.type == PlayerEffectType.VOIDRAGE) {
                val loss = Random.nextInt(1, 3)
                stats.takeDamage(loss)
                messages += "Voidrage drains $loss HP."
            }
            if (effect.type == PlayerEffectType.POISONED) {
                val loss = max(1, effect.magnitude)
                stats.takeDamage(loss)
                messages += "Poison burns for $loss damage."
            }

            effect.remainingTurns -= 1
            if (effect.remainingTurns <= 0) {
                when (effect.type) {
                    PlayerEffectType.IRONBARK_SHIELD -> {
                        stats.maxArmor = max(0, stats.maxArmor - effect.magnitude)
                        stats.armor = stats.armor.coerceAtMost(stats.maxArmor)
                    }

                    PlayerEffectType.HOLLOW_SHARD_BARRIER -> {
                        stats.maxArmor = max(0, stats.maxArmor - effect.magnitude)
                        stats.armor = stats.armor.coerceAtMost(stats.maxArmor)
                    }

                    PlayerEffectType.WEAKENED -> {
                        // no-op on expiry; multiplier handled dynamically
                    }

                    PlayerEffectType.CHILLED -> {
                        // stacks degrade naturally
                    }

                    PlayerEffectType.FROZEN -> {
                        // immobilize handled in turn manager
                    }

                    else -> Unit
                }
                messages += "${effect.displayName} fades."
                iterator.remove()
            }
        }
        return messages
    }

    fun effectDamageBonus(): Int =
        activeEffects.filter { it.type == PlayerEffectType.TITANBLOOD }.sumOf { it.magnitude }

    fun effectCritBonus(): Double =
        activeEffects.filter { it.type == PlayerEffectType.TITANBLOOD }.sumOf { it.critBonus }

    fun effectDamageMultiplier(): Double {
        var multiplier = 1.0
        activeEffects.forEach { effect ->
            when (effect.type) {
                PlayerEffectType.ASTRAL_SURGE -> multiplier *= 1.5
                PlayerEffectType.VOIDRAGE -> multiplier *= 1.5
                PlayerEffectType.WEAKENED -> multiplier *= (1.0 - (effect.magnitude / 100.0))
                else -> Unit
            }
        }
        return multiplier
    }

    fun visionRadiusBonus(): Int = activeEffects.sumOf { it.visionBonus }

    fun mutationProcBonus(): Int = activeEffects.sumOf { it.mutationBonus }

    fun hasStatusImmunity(): Boolean = activeEffects.any { it.statusImmunity }
}

private const val BASE_INVENTORY_SLOTS = 15

data class PlayerEffect(
    val type: PlayerEffectType,
    var remainingTurns: Int,
    val magnitude: Int = 0,
    val critBonus: Double = 0.0,
    val visionBonus: Int = 0,
    val revealRadius: Int? = null,
    val wallSenseRadius: Int? = null,
    val trapSense: Boolean = false,
    val mutationBonus: Int = 0,
    val statusImmunity: Boolean = false
) {
    val displayName: String
        get() = when (type) {
            PlayerEffectType.TITANBLOOD -> "Titanblood surge"
            PlayerEffectType.IRONBARK_SHIELD -> "Ironbark buffer"
            PlayerEffectType.ASTRAL_SURGE -> "Astral surge"
            PlayerEffectType.VOIDRAGE -> "Voidrage frenzy"
            PlayerEffectType.HOLLOWGUARD -> "Hollowguard ward"
            PlayerEffectType.MINDWARD -> "Mindward focus"
            PlayerEffectType.HOLLOW_SHARD_BARRIER -> "Hollow shard barrier"
            PlayerEffectType.MUTATION_BOON -> "Mutation boon"
            PlayerEffectType.VISION_BOOST -> "Hollowsight clarity"
            PlayerEffectType.LUMENVEIL -> "Lumenveil revelation"
            PlayerEffectType.WALLSIGHT -> "Wallsight attunement"
            PlayerEffectType.TRAP_SENSE -> "Echo-lit traps"
            PlayerEffectType.STATUS_IMMUNITY -> "Status immunity"
            PlayerEffectType.STAIRS_COMPASS -> "Echo compass"
            PlayerEffectType.SLIPSHADOW -> "Slipshadow phase"
            PlayerEffectType.WEAKENED -> "Weakened"
            PlayerEffectType.POISONED -> "Poisoned"
            PlayerEffectType.CHILLED -> "Chilled"
            PlayerEffectType.FROZEN -> "Frozen"
        }
}

enum class PlayerEffectType {
    TITANBLOOD,
    IRONBARK_SHIELD,
    ASTRAL_SURGE,
    VOIDRAGE,
    HOLLOWGUARD,
    MINDWARD,
    HOLLOW_SHARD_BARRIER,
    MUTATION_BOON,
    VISION_BOOST,
    LUMENVEIL,
    WALLSIGHT,
    TRAP_SENSE,
    STATUS_IMMUNITY,
    STAIRS_COMPASS,
    SLIPSHADOW,
    WEAKENED,
    POISONED,
    CHILLED,
    FROZEN
}
