package com.yourapp.drawing2d.events

import com.yourapp.drawing2d.model.Point2D
import kotlinx.serialization.Serializable

/**
 * Event Sourcing delta operations with inverse() for undo/redo.
 *
 * Step 1/3 implementation:
 * - Node operations only
 * - Missing: Member operations (Step 2)
 */
@Serializable
sealed class PatchOpV1 {
    abstract fun inverse(): PatchOpV1

    @Serializable
    data class AddNode(
        val nodeId: String,
        val position: Point2D,
    ) : PatchOpV1() {
        override fun inverse() = DeleteNode(nodeId, position)
    }

    @Serializable
    data class DeleteNode(
        val nodeId: String,
        val deletedPosition: Point2D,
    ) : PatchOpV1() {
        override fun inverse() = AddNode(nodeId, deletedPosition)
    }

    @Serializable
    data class MoveNode(
        val nodeId: String,
        val oldPosition: Point2D,
        val newPosition: Point2D,
    ) : PatchOpV1() {
        override fun inverse() = MoveNode(nodeId, newPosition, oldPosition)
    }
}
