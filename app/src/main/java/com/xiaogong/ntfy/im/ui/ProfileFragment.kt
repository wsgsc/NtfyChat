package com.xiaogong.ntfy.im.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.xiaogong.ntfy.im.BuildConfig
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.app.Application
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileFragment : Fragment() {
    private lateinit var repository: Repository

    private val profileAvatar by lazy { view?.findViewById<ImageView>(R.id.profile_avatar) }
    private val profileUsername by lazy { view?.findViewById<TextView>(R.id.profile_username) }
    private val profileHeaderCard by lazy { view?.findViewById<View>(R.id.profile_header_card) }
    private val profileItemSettings by lazy { view?.findViewById<TextView>(R.id.profile_item_settings) }
    private val profileItemAbout by lazy { view?.findViewById<TextView>(R.id.profile_item_about) }
    private val profileItemHelp by lazy { view?.findViewById<TextView>(R.id.profile_item_help) }
    private val profileVersion by lazy { view?.findViewById<TextView>(R.id.profile_version) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = (requireActivity().application as Application).repository

        setupViews()
        loadUserProfile()
    }

    private fun setupViews() {
        // Profile header - click to edit
        profileHeaderCard?.setOnClickListener {
            // TODO: 打开编辑资料页面
            // startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        // Settings
        profileItemSettings?.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // About
        profileItemAbout?.setOnClickListener {
            showAboutDialog()
        }

        // Help & Documentation
        profileItemHelp?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.main_menu_docs_url)))
            startActivity(intent)
        }

        // Version info
        profileVersion?.text = getString(R.string.profile_version, BuildConfig.VERSION_NAME)
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    repository.getUserProfile()
                }

                profile?.let {
                    profileUsername?.text = it.username

                    // Load avatar
                    it.avatarPath?.let { path ->
                        val avatarFile = File(path)
                        if (avatarFile.exists()) {
                            Glide.with(requireContext())
                                .load(avatarFile)
                                .circleCrop()
                                .into(profileAvatar!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user profile", e)
            }
        }
    }

    private fun showAboutDialog() {
        val message = "NtfyChat ${BuildConfig.VERSION_NAME}\n\nAn open source chat application based on ntfy."
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.profile_item_about)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    companion object {
        private const val TAG = "ProfileFragment"
        fun newInstance() = ProfileFragment()
    }
}
