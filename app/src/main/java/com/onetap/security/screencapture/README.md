# Screen Capture Package

This package handles all screen capture functionality for the OneTapSecurity app.

## Components

### ScreenCaptureManager

The `ScreenCaptureManager` class is the main entry point for screen capture functionality. It provides methods to:

- Initialize the screen capture system
- Start a screen capture session
- Capture screenshots
- Process and save captured images
- Clean up resources

#### Usage Example

```kotlin
// Create an instance with required context and handler
val captureManager = ScreenCaptureManager(context, handler, coroutineScope)

// Initialize
captureManager.initialize()

// Set listeners for capture events
captureManager.setScreenCaptureListener(
    onSuccess = { path, file ->
        // Handle successful screenshot
    },
    onError = { errorMessage ->
        // Handle error
    }
)

// Start screen capture with MediaProjection data
captureManager.startScreenCapture(resultCode, data)

// When done, release resources
captureManager.release()
```

### ImageProcessor

The `ImageProcessor` class handles all image processing operations needed for optimal OCR performance:

- Loading and decoding images
- Scaling large images to manageable sizes
- Enhancing image contrast and sharpness for better text recognition
- Applying filters and transformations to improve OCR accuracy

#### Usage Example

```kotlin
val imageProcessor = ImageProcessor()

// Load and process image
val bitmap = imageProcessor.loadAndProcessImage(imagePath)

// Enhance for OCR
val enhancedBitmap = imageProcessor.enhanceImageForOCR(bitmap)
```

## Integration with Service

To use this package in a service:

1. Create a `ScreenCaptureManager` instance in your service
2. Initialize the manager in `onCreate()`
3. Start capture in `onStartCommand()` with projection data
4. Release resources in `onDestroy()`

## Permissions Required

This package requires the following permissions in the AndroidManifest:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
```

For Android 10+ (API 29+), you also need:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
```
