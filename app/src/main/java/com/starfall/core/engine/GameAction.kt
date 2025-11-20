package com.starfall.core.engine

import com.starfall.core.model.Direction

/** Player intent fed into the engine each turn. */
sealed class GameAction {
    data class Move(val direction: Direction) : GameAction()
    data class MoveTo(val x: Int, val y: Int) : GameAction()
    object Wait : GameAction()
    object DescendStairs : GameAction()
    data class UseItem(val itemId: Int) : GameAction()
    data class EquipItem(val itemId: Int) : GameAction()
    object PickUp : GameAction()
}
