package moe.kirakira.data.settings

import android.content.Context
import android.content.SharedPreferences

/** Shared device settings file; search history and recent kaomoji retain their separate stores. */
internal fun settingsPreferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)
