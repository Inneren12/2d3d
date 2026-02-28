package com.yourapp.drawing2d.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Page defines the canvas dimensions and unit system.
 */
@Serializable
data class Page(
    val width: Double,
    val height: Double,
    val units: String = "mm",
)

/**
 * Layer groups entities for organization and visibility control.
 */
@Serializable
data class Layer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
)

/**
 * Drawing2D container holding all drawing data.
 *
 * Complete implementation with all fields:
 * - Core: id, name, page, layers, entities, annotations, metadata
 * - Sync: syncId, syncStatus, updatedAt, version (for Sprint 4.5)
 */
@Serializable
data class Drawing2D(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val page: Page,
    val layers: List<Layer> = emptyList(),
    val entities: List<EntityV1> = emptyList(),
    val annotations: List<AnnotationV1> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),

    // Sync fields (for cloud synchronization in Sprint 4.5)
    val syncId: String? = null,
    val syncStatus: String = "LOCAL",
    val updatedAt: Long = 0L,
    val version: Int = 1,
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
                layers = this.layers.sortedBy { it.id },
                entities = this.entities.sortedBy { it.id },
                annotations = this.annotations.sortedBy { it.id },
            )

        return json.encodeToString(stable)
    }
}
