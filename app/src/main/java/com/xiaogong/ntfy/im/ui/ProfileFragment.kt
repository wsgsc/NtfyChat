package com.xiaogong.ntfy.im.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.app.Application
import com.xiaogong.ntfy.im.db.CustomHeader
import com.xiaogong.ntfy.im.db.Repository
import com.xiaogong.ntfy.im.db.User
import com.xiaogong.ntfy.im.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileFragment : Fragment(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    UserFragment.UserDialogListener,
    CustomHeaderFragment.CustomHeaderDialogListener {

    private lateinit var repository: Repository
    private val serviceManager by lazy { com.xiaogong.ntfy.im.service.SubscriberServiceManager(requireContext()) }

    private val profileAvatar by lazy { view?.findViewById<ImageView>(R.id.profile_avatar) }
    private val profileUsername by lazy { view?.findViewById<TextView>(R.id.profile_username) }
    private val profileHeaderCard by lazy { view?.findViewById<View>(R.id.profile_header_card) }

    var settingsFragment: SettingsActivity.SettingsFragment? = null
        private set
    var userSettingsFragment: SettingsActivity.UserSettingsFragment? = null
    var customHeaderSettingsFragment: SettingsActivity.CustomHeaderSettingsFragment? = null

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

        profileHeaderCard?.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        if (savedInstanceState == null) {
            val frag = SettingsActivity.SettingsFragment()
            settingsFragment = frag
            childFragmentManager.beginTransaction()
                .replace(R.id.profile_settings_container, frag)
                .commit()
        } else {
            settingsFragment = childFragmentManager.findFragmentById(R.id.profile_settings_container)
                as? SettingsActivity.SettingsFragment
            userSettingsFragment = childFragmentManager.findFragmentByTag(TAG_USER_SETTINGS)
                as? SettingsActivity.UserSettingsFragment
            customHeaderSettingsFragment = childFragmentManager.findFragmentByTag(TAG_CUSTOM_HEADER_SETTINGS)
                as? SettingsActivity.CustomHeaderSettingsFragment
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (childFragmentManager.backStackEntryCount > 0) {
                        childFragmentManager.popBackStack()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        loadUserProfile()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragmentClass = pref.fragment ?: return false
        val fragment = childFragmentManager.fragmentFactory
            .instantiate(requireContext().classLoader, fragmentClass)
        fragment.arguments = pref.extras

        val tag = when {
            fragment is SettingsActivity.UserSettingsFragment -> TAG_USER_SETTINGS
            fragment is SettingsActivity.CustomHeaderSettingsFragment -> TAG_CUSTOM_HEADER_SETTINGS
            else -> null
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.profile_settings_container, fragment, tag)
            .addToBackStack(null)
            .commit()

        if (fragment is SettingsActivity.UserSettingsFragment) userSettingsFragment = fragment
        if (fragment is SettingsActivity.CustomHeaderSettingsFragment) customHeaderSettingsFragment = fragment
        return true
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
        settingsFragment?.updateExactAlarmsPref()
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    repository.getUserProfile()
                }
                profile?.let {
                    profileUsername?.text = it.username
                    it.avatarPath?.let { path ->
                        val avatarFile = File(path)
                        if (avatarFile.exists()) {
                            Glide.with(requireContext())
                                .load(avatarFile)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
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

    fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == SettingsActivity.REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION_FOR_AUTO_DOWNLOAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                settingsFragment?.setAutoDownload()
            }
        }
    }

    // UserFragment.UserDialogListener
    override fun onAddUser(dialog: DialogFragment, user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addUser(user)
            requireActivity().runOnUiThread { userSettingsFragment?.reload() }
        }
    }

    override fun onUpdateUser(dialog: DialogFragment, user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateUser(user)
            serviceManager.refresh()
            requireActivity().runOnUiThread { userSettingsFragment?.reload() }
        }
    }

    override fun onDeleteUser(dialog: DialogFragment, baseUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.deleteUser(baseUrl)
            serviceManager.refresh()
            requireActivity().runOnUiThread { userSettingsFragment?.reload() }
        }
    }

    // CustomHeaderFragment.CustomHeaderDialogListener
    override fun onAddCustomHeader(dialog: DialogFragment, header: CustomHeader) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.addCustomHeader(header)
            serviceManager.refresh()
            requireActivity().runOnUiThread { customHeaderSettingsFragment?.reload() }
        }
    }

    override fun onUpdateCustomHeader(dialog: DialogFragment, oldHeader: CustomHeader, newHeader: CustomHeader) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateCustomHeader(oldHeader, newHeader)
            serviceManager.refresh()
            requireActivity().runOnUiThread { customHeaderSettingsFragment?.reload() }
        }
    }

    override fun onDeleteCustomHeader(dialog: DialogFragment, header: CustomHeader) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.deleteCustomHeader(header)
            serviceManager.refresh()
            requireActivity().runOnUiThread { customHeaderSettingsFragment?.reload() }
        }
    }

    companion object {
        private const val TAG = "ProfileFragment"
        private const val TAG_USER_SETTINGS = "user_settings"
        private const val TAG_CUSTOM_HEADER_SETTINGS = "custom_header_settings"
        fun newInstance() = ProfileFragment()
    }
}
