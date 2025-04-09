package com.onetap.security.textprocessing

/**
 * Data class to store the result of text extraction from an image
 */
data class TextExtractionResult(
    val fullText: String,
    val lines: List<String>,
    val blocks: List<TextBlock>,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Data class to represent a block of text with its confidence and bounding box
 */
data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox
)

/**
 * Data class to represent a bounding box in the image
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)