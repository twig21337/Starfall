package com.starfall.core.model

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
        inventory.add(item.copy(position = null, isEquipped = false))
    }

    fun removeItem(itemId: Int) {
        inventory.removeAll { it.id == itemId }
        if (equippedWeaponId == itemId) {
            equippedWeaponId = null
        }
        if (equippedArmorId == itemId) {
            equippedArmorId = null
        }
    }

    fun heal(amount: Int): Int = stats.heal(amount)

    fun equip(itemId: Int): Boolean {
        val item = inventory.firstOrNull { it.id == itemId } ?: return false
        return when (item.type) {
            ItemType.WOOD_SWORD -> equipWeapon(item, attackBonus = 1)
            ItemType.WOOD_ARMOR -> equipArmor(item, defenseBonus = 1)
            ItemType.HEALING_POTION -> false
        }
    }

    fun consumePotion(itemId: Int): Int {
        val potion = inventory.firstOrNull { it.id == itemId && it.type == ItemType.HEALING_POTION }
            ?: return 0
        val healed = heal(5)
        inventory.remove(potion)
        return healed
    }

    fun inventorySnapshot(): List<Item> = inventory.map { it.copy() }

    private fun equipWeapon(item: Item, attackBonus: Int): Boolean {
        if (equippedWeaponId == item.id) return false
        if (equippedWeaponId != null) {
            stats.attack -= attackBonus
        }
        equippedWeaponId = item.id
        stats.attack += attackBonus
        markEquippedState(item, equippedWeaponId, equippedArmorId)
        return true
    }

    private fun equipArmor(item: Item, defenseBonus: Int): Boolean {
        if (equippedArmorId == item.id) return false
        if (equippedArmorId != null) {
            stats.defense -= defenseBonus
        }
        equippedArmorId = item.id
        stats.defense += defenseBonus
        markEquippedState(item, equippedWeaponId, equippedArmorId)
        return true
    }

    private fun markEquippedState(item: Item, weaponId: Int?, armorId: Int?) {
        inventory.replaceAll { invItem ->
            when (invItem.type) {
                ItemType.WOOD_SWORD -> invItem.copy(isEquipped = invItem.id == weaponId)
                ItemType.WOOD_ARMOR -> invItem.copy(isEquipped = invItem.id == armorId)
                else -> invItem.copy(isEquipped = false)
            }
        }
    }
}
