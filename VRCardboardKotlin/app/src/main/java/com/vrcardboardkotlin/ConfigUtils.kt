package com.vrcardboardkotlin

import android.content.res.AssetManager
import android.os.Environment
import java.io.File
import java.io.IOException
import java.io.InputStream


object ConfigUtils {
    const val CARDBOARD_CONFIG_FOLDER: String = "Cardboard"

    fun getConfigFile(filename: String?): File {
        val configFolder = File(
            Environment.getExternalStorageDirectory(), CARDBOARD_CONFIG_FOLDER
        )
        if (!configFolder.exists()) {
            configFolder.mkdirs()
        } else if (!configFolder.isDirectory) {
            val value = configFolder.toString().toString()
            throw IllegalStateException(
                StringBuilder()
                    .append("Folder ").append(value).append(" already exists").toString()
            )
        }
        return File(configFolder, filename)
    }

    @Throws(IOException::class)
    fun openAssetConfigFile(
        assetManager: AssetManager,
        filename: String?
    ): InputStream {
        val assetPath = File(CARDBOARD_CONFIG_FOLDER, filename).path
        return assetManager.open(assetPath)
    }
}
