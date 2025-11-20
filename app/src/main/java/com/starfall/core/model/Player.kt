package com.starfall.core.model

import kotlin.math.max

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
    var equippedArmorId: Int? = null
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

    fun addItem(item: Item) {
        val incoming = item.copy(position = null, isEquipped = false)
        val stackIndex = inventory.indexOfFirst { it.canStackWith(incoming) }
        if (stackIndex >= 0 && incoming.isStackable()) {
            val existing = inventory[stackIndex]
            inventory[stackIndex] = existing.copy(quantity = existing.quantity + incoming.quantity)
        } else {
            inventory.add(incoming)
        }
    }

    fun removeItem(itemId: Int) {
        val item = inventory.firstOrNull { it.id == itemId }
        if (item != null) {
            if (equippedWeaponId == itemId) {
                unequipWeapon(item)
            }
            if (equippedArmorId == itemId) {
                unequipArmor(item)
            }
        }
        inventory.removeAll { it.id == itemId }
    }

    fun breakEquippedArmor() {
        val armorId = equippedArmorId ?: return
        val armorItem = inventory.firstOrNull { it.id == armorId } ?: return
        unequipArmor(armorItem)
        removeItem(armorId)
    }

    fun heal(amount: Int): Int = stats.heal(amount)

    fun equip(itemId: Int): Boolean {
        val item = inventory.firstOrNull { it.id == itemId } ?: return false
        return when (item.type) {
            ItemType.EQUIPMENT_WEAPON -> equipWeapon(item)
            ItemType.EQUIPMENT_ARMOR -> equipArmor(item)
            ItemType.HEALING_POTION -> false
            else -> false
        }
    }

    fun consumePotion(itemId: Int): Int {
        val potion = inventory.firstOrNull { it.id == itemId && it.type == ItemType.HEALING_POTION }
            ?: return 0
        val healed = heal(5)
        decrementStackOrRemove(potion)
        return healed
    }

    fun inventorySnapshot(): List<Item> = inventory.map { it.copy() }

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
    }

    private fun equipWeapon(item: Item): Boolean {
        if (equippedWeaponId == item.id) return false
        val existingWeapon = equippedWeaponId?.let { id -> inventory.firstOrNull { it.id == id } }
        existingWeapon?.let { stats.attack -= weaponAttackBonus(it) }

        equippedWeaponId = item.id
        stats.attack += weaponAttackBonus(item)
        markEquippedState(item, equippedWeaponId, equippedArmorId)
        return true
    }

    private fun equipArmor(item: Item): Boolean {
        if (equippedArmorId == item.id) return false
        val existingArmor = equippedArmorId?.let { id -> inventory.firstOrNull { it.id == id } }
        existingArmor?.let { stats.defense -= armorDefenseBonus(it) }

        equippedArmorId = item.id
        stats.defense += armorDefenseBonus(item)
        stats.maxArmor = armorCapacity(item)
        stats.armor = stats.maxArmor
        markEquippedState(item, equippedWeaponId, equippedArmorId)
        return true
    }

    private fun markEquippedState(item: Item, weaponId: Int?, armorId: Int?) {
        inventory.replaceAll { invItem ->
            when (invItem.type) {
                ItemType.EQUIPMENT_WEAPON -> invItem.copy(isEquipped = invItem.id == weaponId)
                ItemType.EQUIPMENT_ARMOR -> invItem.copy(isEquipped = invItem.id == armorId)
                else -> invItem.copy(isEquipped = false)
            }
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
        stats.maxArmor = 0
        stats.armor = 0
        equippedArmorId = null
    }
}
