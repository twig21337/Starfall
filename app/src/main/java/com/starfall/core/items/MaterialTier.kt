package com.starfall.core.items

/**
 * Material progression ladder from weakest to strongest.
 */
enum class MaterialTier(
    val displayName: String,
    val tierIndex: Int
) {
    WOOD("Wooden", 1),
    BONE("Bone", 2),
    BRONZE("Bronze", 3),
    IRON("Iron", 4),
    STEEL("Steel", 5),
    DARKSTEEL("Darksteel", 6),
    AETHERSTEEL("Aethersteel", 7),
    VOIDGLASS("Voidglass", 8),
    STARFORGED("Starforged", 9);

    companion object {
        val weakestFirst: List<MaterialTier> = values().sortedBy { it.tierIndex }
        val strongestFirst: List<MaterialTier> = values().sortedByDescending { it.tierIndex }
    }
}
