package com.yourapp.drawing2d.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Drawing2D container holding all drawing data.
 *
 * This is Step 1/3 implementation:
 * - Core fields: id, name, entities, annotations, metadata
 * - Missing: Page, Layer (added in Step 2)
 * - Missing: syncId, syncStatus, updatedAt, version (added in Step 3)
 */
@Serializable
data class Drawing2D(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val entities: List<EntityV1> = emptyList(),
    val annotations: List<AnnotationV1> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Produces deterministic JSON output.
     *
     * Guarantees:
     * - Same data → same JSON → same SHA256 hash
     * - Collections sorted by ID lexicographically
     * - Pretty print with 2-space indent
     * - Stable map key ordering (alphabetically)
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun toJsonStable(): String {
        val json =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }

        val stable =
            this.copy(
                entities = this.entities.sortedBy { it.id },
                annotations = this.annotations.sortedBy { it.id },
            )

        return json.encodeToString(stable)
    }
}
