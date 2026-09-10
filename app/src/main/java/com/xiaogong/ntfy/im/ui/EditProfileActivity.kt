package com.xiaogong.ntfy.im.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.app.Application
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.UserProfile
import com.xiaogong.ntfy.im.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : AppCompatActivity() {
    private lateinit var repository: Repository
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarPath: String? = null

    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.edit_profile_toolbar) }
    private val avatarView by lazy { findViewById<ImageView>(R.id.edit_profile_avatar) }
    private val usernameInput by lazy { findViewById<TextInputEditText>(R.id.edit_profile_username_input) }
    private val saveButton by lazy { findViewById<MaterialButton>(R.id.edit_profile_save_button) }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            Glide.with(this).load(uri).circleCrop().into(avatarView)
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
        setContentView(R.layout.activity_edit_profile)

        repository = (application as Application).repository

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        usernameInput.doAfterTextChanged { updateSaveButtonState() }
        avatarView.setOnClickListener { checkPermissionAndPickImage() }
        saveButton.setOnClickListener { saveProfile() }

        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { repository.getUserProfile() }
            profile?.let {
                usernameInput.setText(it.username)
                currentAvatarPath = it.avatarPath
                it.avatarPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        Glide.with(this@EditProfileActivity)
                            .load(file)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .circleCrop()
                            .into(avatarView)
                    }
                }
            }
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        saveButton.isEnabled = !usernameInput.text?.toString()?.trim().isNullOrEmpty()
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            launchImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun launchImagePicker() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun saveProfile() {
        val username = usernameInput.text?.toString()?.trim()
        if (username.isNullOrEmpty()) {
            Toast.makeText(this, R.string.welcome_username_required, Toast.LENGTH_SHORT).show()
            return
        }

        saveButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val newAvatarPath = selectedAvatarUri?.let { uri ->
                    saveAvatarToInternalStorage(uri)
                } ?: currentAvatarPath

                val profile = UserProfile(id = 1, username = username, avatarPath = newAvatarPath, createdAt = System.currentTimeMillis())
                withContext(Dispatchers.IO) { repository.saveUserProfile(profile) }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditProfileActivity, R.string.edit_profile_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditProfileActivity, R.string.edit_profile_save_error, Toast.LENGTH_SHORT).show()
                    saveButton.isEnabled = true
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
            if (!avatarDir.exists()) avatarDir.mkdirs()

            val avatarFile = File(avatarDir, "user_avatar.jpg")
            val outputStream = FileOutputStream(avatarFile)

            val resizedBitmap = if (bitmap.width > 512 || bitmap.height > 512) {
                val scale = 512f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            if (resizedBitmap != bitmap) resizedBitmap.recycle()
            bitmap.recycle()

            avatarFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving avatar", e)
            null
        }
    }

    companion object {
        private const val TAG = "EditProfileActivity"
    }
}
