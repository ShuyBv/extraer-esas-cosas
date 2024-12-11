package com.vrcardboardkotlin

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

internal object UiUtils {
    fun launchOrInstallCardboard(context: Context) {
        val pm: PackageManager = context.getPackageManager()
        val settingsIntent: Intent = Intent()
        settingsIntent.setAction("com.google.vrtoolkit.cardboard.CONFIGURE")
        settingsIntent.putExtra("VERSION", "0.5.1")
        val intentsToGoogleCardboard: MutableList<Intent> = ArrayList()
        for (info: ResolveInfo in pm.queryIntentActivities(settingsIntent, 0)) {
            val pkgName: String = info.activityInfo.packageName
            if (pkgName.startsWith("com.google.")) {
                val intent: Intent = Intent(settingsIntent)
                intent.setClassName(pkgName, info.activityInfo.name)
                intentsToGoogleCardboard.add(intent)
            }
        }
        if (intentsToGoogleCardboard.isEmpty()) {
            showInstallDialog(context)
        } else if (intentsToGoogleCardboard.size == 1) {
            showConfigureDialog(context, intentsToGoogleCardboard.get(0))
        } else {
            showConfigureDialog(context, settingsIntent)
        }
    }

    private fun showInstallDialog(context: Context) {
        val fragmentManager: FragmentManager = (context as Activity).getFragmentManager()
        val dialog: DialogFragment = SettingsDialogFragment(
            InstallDialogStrings() as DialogStrings,
            object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, id: Int) {
                    try {
                        context.startActivity(
                            Intent(
                                "android.intent.action.VIEW",
                                Uri.parse("http://google.com/cardboard/cfg?vrtoolkit_version=0.5.1")
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context.getApplicationContext(),
                            "No browser to open website." as CharSequence?,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        dialog.show(fragmentManager, "InstallCardboardDialog")
    }

    private fun showConfigureDialog(context: Context, intent: Intent) {
        val fragmentManager: FragmentManager = (context as Activity).getFragmentManager()
        val dialog: DialogFragment = SettingsDialogFragment(
            ConfigureDialogStrings() as DialogStrings,
            object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface, id: Int) {
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        showInstallDialog(context)
                    }
                }
            })
        dialog.show(fragmentManager, "ConfigureCardboardDialog")
    }

    private open class DialogStrings() {
        var mTitle: String? = null
        var mMessage: String? = null
        var mPositiveButtonText: String? = null
        var mNegativeButtonText: String? = null
    }

    private class InstallDialogStrings() : DialogStrings() {
        init {
            this.mTitle = "Configure"
            this.mMessage = "Get the Cardboard app in order to configure your viewer."
            this.mPositiveButtonText = "Go to Play Store"
            this.mNegativeButtonText = "Cancel"
        }
    }

    private class ConfigureDialogStrings() : DialogStrings() {
        init {
            this.mTitle = "Configure"
            this.mMessage = "Set up your viewer for the best experience."
            this.mPositiveButtonText = "Setup"
            this.mNegativeButtonText = "Cancel"
        }
    }

    private class SettingsDialogFragment(
        private val mDialogStrings: DialogStrings,
        private val mPositiveButtonListener: DialogInterface.OnClickListener
    ) : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val builder: AlertDialog.Builder = AlertDialog.Builder(
                getActivity() as Context?
            )
            builder.setTitle(mDialogStrings.mTitle as CharSequence?).setMessage(
                mDialogStrings.mMessage as CharSequence?
            ).setPositiveButton(
                mDialogStrings.mPositiveButtonText as CharSequence?, this.mPositiveButtonListener
            ).setNegativeButton(
                mDialogStrings.mNegativeButtonText as CharSequence?,
                null as DialogInterface.OnClickListener?
            )
            return builder.create() as Dialog
        }
    }
}
