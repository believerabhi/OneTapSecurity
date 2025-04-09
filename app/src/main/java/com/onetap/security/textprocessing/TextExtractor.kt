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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
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
            
            // Check if the file exists and is not empty
            val file = File(imagePath)
            if (!file.exists() || file.length().toInt() == 0) {
                Log.e(TAG, "Image file does not exist or is empty: $imagePath")
                return@withContext TextExtractionResult(
                    fullText = "",
                    lines = emptyList(),
                    blocks = emptyList(),
                    imagePath = imagePath,
                    success = false,
                    errorMessage = "Image file does not exist or is empty"
                )
            }
            
            // Load and process the image
            val bitmap = imageProcessor.loadAndProcessImage(imagePath) ?: return@withContext TextExtractionResult(
                fullText = "",
                lines = emptyList(),
                blocks = emptyList(),
                imagePath = imagePath,
                success = false,
                errorMessage = "Failed to load or process the image"
            )
            
            // If the bitmap dimensions are invalid, return error
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                Log.e(TAG, "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                return@withContext TextExtractionResult(
                    fullText = "",
                    lines = emptyList(),
                    blocks = emptyList(),
                    imagePath = imagePath,
                    success = false,
                    errorMessage = "Invalid bitmap dimensions"
                )
            }
            
            // Enhance the image for OCR if needed
            val enhancedBitmap = imageProcessor.enhanceImageForOCR(bitmap)
            
            // Create an ML Kit InputImage
            val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)
            
            var visionText: Text
            
            try {
                // Perform text recognition with timeout
                visionText = withTimeout(10000) {
                    recognizeText(inputImage)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Text recognition timed out after 10 seconds")
                
                // Return a partial result
                return@withContext TextExtractionResult(
                    fullText = "Text recognition timed out. Please try again.",
                    lines = listOf("Text recognition timed out"),
                    blocks = listOf(TextBlock(
                        text = "Text recognition timed out",
                        confidence = 0.0f,
                        boundingBox = BoundingBox(0, 0, 0, 0)
                    )),
                    imagePath = imagePath,
                    success = false,
                    errorMessage = "Text recognition timed out after 10 seconds"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error recognizing text: ${e.message}")
                throw e
            }
            
            // If text extraction fails
            if (visionText.text.isBlank()) {
                Log.w(TAG, "No text detected in the image")
                
                // Return a partial result with a message that no text was detected
                return@withContext TextExtractionResult(
                    fullText = "No text detected in the image.",
                    lines = listOf("No text detected"),
                    blocks = listOf(TextBlock(
                        text = "No text detected",
                        confidence = 0.0f,
                        boundingBox = BoundingBox(0, 0, 0, 0)
                    )),
                    imagePath = imagePath,
                    success = true,
                    errorMessage = null
                )
            }
            
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