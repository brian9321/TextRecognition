package com.example.textrecognition.faceRecognition


import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.textrecognition.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FaceDetectionActivity : AppCompatActivity() {

    private lateinit var ivSelectedImage: ImageView
    private lateinit var btnSelectGallery: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnDetectFace: Button
    private lateinit var tvResultText: TextView

    private var currentImageUri: Uri? = null

    // Seleccionar imagen de la galería
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                currentImageUri = uri
                ivSelectedImage.setImageURI(uri)
                tvResultText.text = "Imagen seleccionada desde Galería"
            } else {
                Toast.makeText(this, "No se seleccionó ninguna imagen", Toast.LENGTH_SHORT).show()
            }
        }

    // Tomar foto
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentImageUri?.let { uri ->
                    ivSelectedImage.setImageURI(uri)
                    tvResultText.text = "Foto capturada correctamente"
                }
            } else {
                Toast.makeText(this, "No se tomó la foto", Toast.LENGTH_SHORT).show()
            }
        }

    // Solicitud de permiso de cámara
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detection)

        // Referencias
        ivSelectedImage = findViewById(R.id.ivSelectedImageFace)
        btnSelectGallery = findViewById(R.id.btnSelectGalleryFace)
        btnTakePhoto = findViewById(R.id.btnTakePhotoFace)
        btnDetectFace = findViewById(R.id.btnGetTextFace)
        tvResultText = findViewById(R.id.tvResultTextFace)

        // Botones
        btnSelectGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTakePhoto.setOnClickListener {
            if (allPermissionsGranted()) {
                takePhoto()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnDetectFace.setOnClickListener {
            processFace()
        }
    }

    private fun processFace() {
        val uri = currentImageUri
        if (uri == null) {
            Toast.makeText(this, "No hay imagen seleccionada o capturada", Toast.LENGTH_SHORT).show()
            return
        }

        // Usamos nuestra función del Helper
        FaceDetectionHelper.detectAndCropFirstFace(this, uri)
            .addOnSuccessListener { croppedBitmap ->
                if (croppedBitmap != null) {
                    tvResultText.text = "¡Rostro detectado y recortado!"
                    ivSelectedImage.setImageBitmap(croppedBitmap)
                } else {
                    tvResultText.text = "No se detectó ningún rostro."
                }
            }
            .addOnFailureListener { e ->
                tvResultText.text = "Error: ${e.message}"
                e.printStackTrace()
            }
    }

    private fun takePhoto() {
        val photoUri = createImageUri()
        currentImageUri = photoUri
        if (photoUri != null) {
            takePhotoLauncher.launch(photoUri)
        }
    }

    private fun createImageUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}.jpg"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(storageDir, fileName)
        return FileProvider.getUriForFile(
            this,
            "com.example.recognition.fileprovider", // Ajusta la autoridad
            file
        )
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}
