package com.xiaogong.ntfy.im.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xiaogong.ntfy.im.R
import com.xiaogong.ntfy.im.util.copyToClipboard
import java.security.SecureRandom

class EncryptPasswordFragment : DialogFragment() {
    private var currentPassword: String? = null
    private lateinit var listener: EncryptPasswordDialogListener

    private lateinit var toolbar: MaterialToolbar
    private lateinit var saveMenuItem: MenuItem
    private lateinit var descriptionView: TextView
    private lateinit var passwordViewLayout: TextInputLayout
    private lateinit var passwordView: TextInputEditText
    private lateinit var generateButton: MaterialButton
    private lateinit var copyButton: MaterialButton

    interface EncryptPasswordDialogListener {
        fun onSavePassword(dialog: DialogFragment, password: String?)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? EncryptPasswordDialogListener
            ?: throw IllegalStateException("Parent fragment must implement EncryptPasswordDialogListener")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (activity == null) {
            throw IllegalStateException("Activity cannot be null")
        }

        // Reconstruct password from bundle
        currentPassword = arguments?.getString(BUNDLE_PASSWORD)

        // Build root view
        val view = requireActivity().layoutInflater.inflate(R.layout.fragment_encrypt_password_dialog, null)

        // Setup toolbar
        toolbar = view.findViewById(R.id.encrypt_password_dialog_toolbar)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.encrypt_password_dialog_action_save -> {
                    saveClicked()
                    true
                }
                else -> false
            }
        }
        saveMenuItem = toolbar.menu.findItem(R.id.encrypt_password_dialog_action_save)

        // Setup views
        descriptionView = view.findViewById(R.id.encrypt_password_dialog_description)
        passwordViewLayout = view.findViewById(R.id.encrypt_password_dialog_password_layout)
        passwordView = view.findViewById(R.id.encrypt_password_dialog_password)
        generateButton = view.findViewById(R.id.encrypt_password_dialog_generate_button)
        copyButton = view.findViewById(R.id.encrypt_password_dialog_copy_button)

        // Set current password
        if (currentPassword != null) {
            passwordView.setText(currentPassword)
        }

        // Setup generate button
        generateButton.setOnClickListener {
            val randomPassword = generateRandomPassword(32)
            passwordView.setText(randomPassword)
            passwordView.setSelection(randomPassword.length)
        }

        // Setup copy button
        copyButton.setOnClickListener {
            val password = passwordView.text?.toString()
            if (!TextUtils.isEmpty(password)) {
                copyToClipboard(requireContext(), getString(R.string.encrypt_password_dialog_copy_label), password!!)
                Toast.makeText(requireContext(), getString(R.string.encrypt_password_dialog_copy_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.encrypt_password_dialog_copy_empty), Toast.LENGTH_SHORT).show()
            }
        }

        // Build dialog
        val dialog = Dialog(requireContext(), R.style.Theme_App_FullScreenDialog)
        dialog.setContentView(view)

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Show keyboard after the dialog is fully visible
        passwordView.postDelayed({
            passwordView.requestFocus()
            if (passwordView.text != null) {
                passwordView.setSelection(passwordView.text!!.length)
            }
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(passwordView, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun saveClicked() {
        if (!this::listener.isInitialized) return
        val password = passwordView.text?.toString()
        val passwordToSave = if (password.isNullOrEmpty()) null else password
        listener.onSavePassword(this, passwordToSave)
        dismiss()
    }

    private fun generateRandomPassword(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        val password = StringBuilder(length)
        for (i in 0 until length) {
            password.append(chars[random.nextInt(chars.length)])
        }
        return password.toString()
    }

    companion object {
        const val TAG = "NtfyEncryptPasswordFragment"
        private const val BUNDLE_PASSWORD = "password"

        fun newInstance(currentPassword: String?): EncryptPasswordFragment {
            val fragment = EncryptPasswordFragment()
            val args = Bundle()
            if (currentPassword != null) {
                args.putString(BUNDLE_PASSWORD, currentPassword)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
