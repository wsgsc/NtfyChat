package com.xiaogong.ntfy.im.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.UserProfile
import com.xiaogong.ntfy.im.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WelcomeActivity : AppCompatActivity() {
    private lateinit var repository: Repository
    private var selectedAvatarUri: Uri? = null

    private val avatarView by lazy { findViewById<android.widget.ImageView>(R.id.welcome_avatar) }
    private val selectAvatarButton by lazy { findViewById<MaterialButton>(R.id.welcome_select_avatar_button) }
    private val usernameInput by lazy { findViewById<TextInputEditText>(R.id.welcome_username_input) }
    private val completeButton by lazy { findViewById<MaterialButton>(R.id.welcome_complete_button) }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            loadAvatar(uri)
            updateCompleteButtonState()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchImagePicker()
        } else {
            Toast.makeText(this, R.string.welcome_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        repository = Repository.getInstance(this)

        setupViews()
    }

    private fun setupViews() {
        selectAvatarButton.setOnClickListener {
            checkPermissionAndPickImage()
        }

        usernameInput.doAfterTextChanged {
            updateCompleteButtonState()
        }

        completeButton.setOnClickListener {
            saveProfileAndContinue()
        }
    }

    private fun checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchImagePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchImagePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun launchImagePicker() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun loadAvatar(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(avatarView)
    }

    private fun updateCompleteButtonState() {
        val username = usernameInput.text?.toString()?.trim()
        completeButton.isEnabled = !username.isNullOrEmpty()
    }

    private fun saveProfileAndContinue() {
        val username = usernameInput.text?.toString()?.trim()
        if (username.isNullOrEmpty()) {
            Toast.makeText(this, R.string.welcome_username_required, Toast.LENGTH_SHORT).show()
            return
        }

        completeButton.isEnabled = false
        completeButton.text = getString(R.string.welcome_saving)

        lifecycleScope.launch {
            try {
                val avatarPath = selectedAvatarUri?.let { uri ->
                    saveAvatarToInternalStorage(uri)
                }

                val profile = UserProfile(
                    id = 1,
                    username = username,
                    avatarPath = avatarPath,
                    createdAt = System.currentTimeMillis()
                )

                repository.saveUserProfile(profile)
                repository.setFirstLaunchComplete()

                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@WelcomeActivity,
                        R.string.welcome_save_error,
                        Toast.LENGTH_SHORT
                    ).show()
                    completeButton.isEnabled = true
                    completeButton.text = getString(R.string.welcome_complete)
                }
            }
        }
    }

    private suspend fun saveAvatarToInternalStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val avatarDir = File(filesDir, "avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }

            val avatarFile = File(avatarDir, "user_avatar.jpg")
            val outputStream = FileOutputStream(avatarFile)

            val resizedBitmap = if (bitmap.width > 512 || bitmap.height > 512) {
                val scale = 512f / maxOf(bitmap.width, bitmap.height)
                val width = (bitmap.width * scale).toInt()
                val height = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

            avatarFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving avatar", e)
            null
        }
    }

    companion object {
        private const val TAG = "WelcomeActivity"
    }
}
