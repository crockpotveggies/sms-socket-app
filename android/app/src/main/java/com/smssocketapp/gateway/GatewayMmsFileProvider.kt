package com.smssocketapp.gateway

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import java.io.File
import java.io.FileNotFoundException

class GatewayMmsFileProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0

  override fun getType(uri: Uri): String? = null

  override fun openFile(uri: Uri, fileMode: String): ParcelFileDescriptor {
    val file = File(context?.cacheDir, uri.path.orEmpty())
    val mode =
      if (TextUtils.equals(fileMode, "r")) {
        ParcelFileDescriptor.MODE_READ_ONLY
      } else {
        ParcelFileDescriptor.MODE_WRITE_ONLY or
          ParcelFileDescriptor.MODE_TRUNCATE or
          ParcelFileDescriptor.MODE_CREATE
      }

    return ParcelFileDescriptor.open(file, mode)
  }
}
