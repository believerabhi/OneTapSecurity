package com.onetap.security.screencapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import android.util.Log
import java.io.File

/**
 * Class for processing images for better text recognition
 */
class ImageProcessor {
    private val TAG = "ImageProcessor"
    
    /**
     * Load an image from file and optionally perform initial processing
     */
    fun loadAndProcessImage(imagePath: String): Bitmap? {
        return try {
            Log.d(TAG, "Loading image from: $imagePath")
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "Image file does not exist: $imagePath")
                return null
            }
            
            // Load the bitmap
            val options = BitmapFactory.Options().apply {
                // Only decode bounds initially to check size
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            // Calculate sample size if image is very large
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, 2560, 1440)
            
            // Load actual bitmap with appropriate scaling
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }
            
            val bitmap = BitmapFactory.decodeFile(imagePath, options)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from: $imagePath")
                return null
            }
            
            // Return the loaded bitmap
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Calculate the optimal sample size for loading a large image
     */
    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Enhance the image for OCR by applying various filters and transformations
     */
    fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        try {
            // Create a mutable copy of the bitmap
            val enhancedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Apply OCR enhancement techniques based on the image characteristics
            val hasLowContrast = hasLowContrast(bitmap)
            
            if (hasLowContrast) {
                // Apply contrast enhancement
                increaseContrast(enhancedBitmap)
            } else {
                // Apply minimal processing
                applyBasicEnhancement(enhancedBitmap)
            }
            
            return enhancedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing image: ${e.message}")
            e.printStackTrace()
            // Return original bitmap if enhancement fails
            return bitmap
        }
    }
    
    /**
     * Check if the image has low contrast
     */
    private fun hasLowContrast(bitmap: Bitmap): Boolean {
        try {
            // Sample the image to analyze contrast
            val sampleSize = 10
            val width = bitmap.width
            val height = bitmap.height
            
            var min = 255
            var max = 0
            
            // Sample pixels across the image
            for (x in 0 until width step width / sampleSize) {
                for (y in 0 until height step height / sampleSize) {
                    val pixel = bitmap.getPixel(x, y)
                    
                    // Calculate grayscale value
                    val gray = (0.299 * ((pixel shr 16) and 0xff) + 
                               0.587 * ((pixel shr 8) and 0xff) + 
                               0.114 * (pixel and 0xff)).toInt()
                    
                    if (gray < min) min = gray
                    if (gray > max) max = gray
                }
            }
            
            // Calculate contrast ratio
            val contrastRatio = if (min == 0) max.toFloat() else max.toFloat() / min.toFloat()
            
            // Return true if contrast is low
            return contrastRatio < 3.0f || (max - min) < 50
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contrast: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Apply basic enhancement to the image
     */
    private fun applyBasicEnhancement(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        // Slightly sharpen the image for better text edges
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.1f, 0f, 0f, 0f, 0f,
                0f, 1.1f, 0f, 0f, 0f,
                0f, 0f, 1.1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
    
    /**
     * Increase contrast of the image for better OCR
     */
    private fun increaseContrast(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        // Apply contrast enhancement
        val colorMatrix = ColorMatrix().apply {
            // Increase contrast by a factor of 1.5
            set(floatArrayOf(
                1.5f, 0f, 0f, 0f, -20f,
                0f, 1.5f, 0f, 0f, -20f,
                0f, 0f, 1.5f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
}