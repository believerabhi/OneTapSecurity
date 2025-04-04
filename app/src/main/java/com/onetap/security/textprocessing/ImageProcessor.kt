package com.onetap.security.textprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Utility class for image processing operations
 */
class ImageProcessor {
    private val TAG = "ImageProcessor"

    /**
     * Load an image from a file path and prepare it for text recognition
     * @param imagePath Path to the image file
     * @return Processed Bitmap or null if loading failed
     */
    fun loadAndProcessImage(imagePath: String): Bitmap? {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "Image file does not exist: $imagePath")
                return null
            }

            // First, decode the dimensions of the image without loading it fully
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            // Calculate a sample size to avoid OutOfMemoryError for large images
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT)
            }

            // Load the bitmap with the calculated sample size
            val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null
            
            // Handle image orientation based on EXIF data
            return rotateImageIfRequired(bitmap, imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading/processing image: ${e.message}")
            return null
        }
    }

    /**
     * Calculate appropriate sample size for loading a large bitmap
     */
    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    /**
     * Rotate an image if required based on EXIF orientation data
     */
    private fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            
            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error checking image orientation: ${e.message}")
            return bitmap
        }
    }

    /**
     * Preprocess the image to improve OCR accuracy (if needed)
     */
    fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        // This is a placeholder for more advanced preprocessing
        // For now, we're just returning the original bitmap
        
        // Possible enhancements:
        // - Convert to grayscale
        // - Apply thresholding
        // - Apply noise reduction
        // - Increase contrast
        // - Apply deskewing
        
        return bitmap
    }

    companion object {
        const val MAX_WIDTH = 2048
        const val MAX_HEIGHT = 2048
    }
}