package com.example.textrecognition.faceRecognition


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks.call
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.io.InputStream

object FaceDetectionHelper {

    // 1) Configura ML Kit FaceDetector (opcionalmente ajusta opciones)
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(faceDetectorOptions)
    }

    /**
     * 2) Función principal: Detectar y recortar el primer rostro de la imagen.
     *    Retorna un Task<Bitmap?> con el rostro recortado (o null si no hay rostro).
     */
    fun detectAndCropFirstFace(context: Context, uri: Uri): Task<Bitmap?> {
        // a) Detectar rostros
        return faceDetector.process(InputImage.fromFilePath(context, uri))
            .onSuccessTask { faces ->
                if (faces.isEmpty()) {
                    // Si no hay rostros, retornamos null (pero en un Task)
                    return@onSuccessTask call {
                        null
                    }
                }

                // b) Tomar el primer rostro
                val face = faces[0]

                // c) Cargar el Bitmap original
                val originalBitmap = getBitmapFromUri(context, uri)

                // d) Corregir orientación (EXIF)
                val correctedBitmap = correctBitmapOrientation(context, uri, originalBitmap)

                // e) Recortar bounding box
                val box = face.boundingBox
                val x = box.left.coerceAtLeast(0)
                val y = box.top.coerceAtLeast(0)
                val width = box.width().coerceAtMost(correctedBitmap.width - x)
                val height = box.height().coerceAtMost(correctedBitmap.height - y)

                val cropped = Bitmap.createBitmap(correctedBitmap, x, y, width, height)

                // f) Retornar el cropped en un Task
                call { cropped }
            }
    }

    /**
     * Carga un Bitmap usando MediaStore (sin corregir orientación).
     */
    private fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }

    /**
     * Corrige la orientación del Bitmap si en la metadata EXIF
     * está rotado o volteado.
     */
    private fun correctBitmapOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        inputStream.close()

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
            else -> bitmap
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun flipBitmap(source: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = android.graphics.Matrix()
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        matrix.preScale(sx, sy)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // Extensión de Task para concatenar tareas success -> success
    inline fun <TResult, TContinuationResult> Task<TResult>.onSuccessTask(
        crossinline continuation: (TResult) -> Task<TContinuationResult>
    ): Task<TContinuationResult> {
        val tcs = TaskCompletionSource<TContinuationResult>() // Crear "Task manual"

        addOnSuccessListener { result ->
            continuation(result)
                .addOnSuccessListener { r ->
                    tcs.setResult(r) // Completar con el resultado del segundo Task
                }
                .addOnFailureListener { e ->
                    tcs.setException(e) // Completar con error si el segundo Task falla
                }
        }

        addOnFailureListener { e ->
            tcs.setException(e) // Si el primer Task falla, también lo reflejamos
        }

        return tcs.task
    }
}
