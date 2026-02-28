package com.yourapp.drawing2d.events

import com.yourapp.drawing2d.model.Point2D
import kotlinx.serialization.Serializable

/**
 * Event Sourcing delta operations with inverse() for undo/redo.
 *
 * Complete implementation:
 * - Node operations: AddNode, DeleteNode, MoveNode
 * - Member operations: AddMember, DeleteMember, UpdateMemberProfile
 * - Each operation has inverse() for undo/redo
 * - Memory: <1KB per operation (verified by tests)
 *
 * NOTE: Full Drawing2D.apply(PatchOpV1) implementation deferred to Sprint 3
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

    @Serializable
    data class AddMember(
        val memberId: String,
        val startNodeId: String,
        val endNodeId: String,
        val profileRef: String? = null,
    ) : PatchOpV1() {
        override fun inverse() =
            DeleteMember(
                memberId = memberId,
                deletedStartNodeId = startNodeId,
                deletedEndNodeId = endNodeId,
                deletedProfileRef = profileRef,
            )
    }

    @Serializable
    data class DeleteMember(
        val memberId: String,
        val deletedStartNodeId: String,
        val deletedEndNodeId: String,
        val deletedProfileRef: String? = null,
    ) : PatchOpV1() {
        override fun inverse() =
            AddMember(
                memberId = memberId,
                startNodeId = deletedStartNodeId,
                endNodeId = deletedEndNodeId,
                profileRef = deletedProfileRef,
            )
    }

    @Serializable
    data class UpdateMemberProfile(
        val memberId: String,
        val oldProfileRef: String?,
        val newProfileRef: String?,
    ) : PatchOpV1() {
        override fun inverse() =
            UpdateMemberProfile(
                memberId = memberId,
                oldProfileRef = newProfileRef,
                newProfileRef = oldProfileRef,
            )
    }
}
