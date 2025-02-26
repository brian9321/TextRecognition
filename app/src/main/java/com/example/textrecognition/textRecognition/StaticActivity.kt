package com.example.textrecognition.textRecognition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.textrecognition.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaticActivity : AppCompatActivity() {

    private lateinit var ivSelectedImage: ImageView
    private lateinit var btnSelectGallery: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnGetText: Button
    private lateinit var tvResultText: TextView

    private var currentImageUri: Uri? = null

    // Lanzador para seleccionar imagen desde la galería
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

    // Lanzador para tomar foto con la cámara
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                // Si la foto se tomó con éxito, la currentImageUri ya apunta al archivo creado
                currentImageUri?.let { uri ->
                    ivSelectedImage.setImageURI(uri)
                    tvResultText.text = "Foto capturada correctamente"
                }
            } else {
                Toast.makeText(this, "No se tomó la foto", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_static)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Referencias a vistas
        ivSelectedImage = findViewById(R.id.ivSelectedImage)
        btnSelectGallery = findViewById(R.id.btnSelectGallery)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnGetText = findViewById(R.id.btnGetText)
        tvResultText = findViewById(R.id.tvResultText)

        // Acciones de los botones
        btnSelectGallery.setOnClickListener {
            pickImageFromGallery()
        }

        btnTakePhoto.setOnClickListener {
            // Primero revisamos permiso de cámara
            if (allPermissionsGranted()) {
                takePhoto()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnGetText.setOnClickListener {
            recognizeTextFromCurrentImage()
        }
    }

    // Método para seleccionar imagen de la galería
    private fun pickImageFromGallery() {
        // Tipo "image/*" para que muestre solo imágenes
        pickImageLauncher.launch("image/*")
    }

    // Método para tomar foto con la cámara
    private fun takePhoto() {
        val photoUri = createImageUri()
        currentImageUri = photoUri
        if (photoUri != null) {
            takePhotoLauncher.launch(photoUri)
        }
    }

    // Crea un Uri en un archivo temporal para almacenar la foto
    private fun createImageUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}.jpg"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(storageDir, fileName)
        return FileProvider.getUriForFile(
            this,
            "com.example.recognition.fileprovider",
            file
        )
    }

    // Lanza reconocimiento de texto usando el helper, si existe un Uri
    private fun recognizeTextFromCurrentImage() {
        val uri = currentImageUri
        if (uri == null) {
            Toast.makeText(this, "No hay imagen seleccionada/capturada", Toast.LENGTH_SHORT).show()
            return
        }

        TextRecognitionHelper.recognizeTextFromUri(this, uri)
            .addOnSuccessListener { visionText ->
                val recognized = visionText.text
                tvResultText.text = recognized.ifBlank {
                    "No se detectó texto en la imagen"
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                tvResultText.text = "Error al reconocer texto: ${e.message}"
            }
    }

    // Manejo de permisos de cámara
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}
