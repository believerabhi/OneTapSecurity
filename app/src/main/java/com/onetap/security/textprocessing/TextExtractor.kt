package com.onetap.security.textprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Class responsible for extracting text from images using ML Kit
 */
class TextExtractor(private val context: Context) {
    private val TAG = "TextExtractor"
    private val imageProcessor = ImageProcessor()
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from an image at the specified path
     * @param imagePath Path to the image file
     * @return TextExtractionResult containing the extracted text and metadata
     */
    suspend fun extractTextFromImage(imagePath: String): TextExtractionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting text extraction from image: $imagePath")
            
            // Load and process the image
            val bitmap = imageProcessor.loadAndProcessImage(imagePath) ?: return@withContext TextExtractionResult(
                fullText = "",
                lines = emptyList(),
                blocks = emptyList(),
                imagePath = imagePath,
                success = false,
                errorMessage = "Failed to load or process the image"
            )
            
            // Enhance the image for OCR if needed
            val enhancedBitmap = imageProcessor.enhanceImageForOCR(bitmap)
            
            // Create an ML Kit InputImage
            val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)
            
            // Perform text recognition
            val visionText = recognizeText(inputImage)
            
            // Process the results
            val blocks = processTextBlocks(visionText)
            val lines = blocks.flatMap { block -> 
                // Extract line texts from each block
                block.text.split("\n")
            }

            Log.i(TAG, "visionText: belall : ${visionText.text}")
            // Build the result
            TextExtractionResult(
                fullText = visionText.text,
                lines = lines,
                blocks = blocks,
                imagePath = imagePath,
                success = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from image: ${e.message}")
            TextExtractionResult(
                fullText = "",
                lines = emptyList(),
                blocks = emptyList(),
                imagePath = imagePath,
                success = false,
                errorMessage = "Text extraction failed: ${e.message}"
            )
        }
    }

    /**
     * Process ML Kit text recognition result into our model
     */
    private fun processTextBlocks(visionText: Text): List<TextBlock> {
        return visionText.textBlocks.map { block ->
            val boundingBox = block.boundingBox?.let { rect ->
                BoundingBox(
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom
                )
            } ?: BoundingBox(0, 0, 0, 0)
            
            TextBlock(
                text = block.text,
                confidence = 0.0f, // ML Kit doesn't provide confidence scores for text blocks
                boundingBox = boundingBox
            )
        }
    }

    /**
     * Perform text recognition using ML Kit and convert to a coroutine
     */
    private suspend fun recognizeText(image: InputImage): Text = suspendCancellableCoroutine { continuation ->
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
            
        continuation.invokeOnCancellation {
            // If coroutine is cancelled, we can cancel any ongoing work here if needed
        }
    }

    /**
     * Clean up resources when no longer needed
     */
    fun close() {
        textRecognizer.close()
    }
}