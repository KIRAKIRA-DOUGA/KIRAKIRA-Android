package moe.kirakira.feature.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import moe.kirakira.data.kaomoji.RecentKaomojiStore

internal class KaomojiViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RecentKaomojiStore.get(application)
    val recent = store.recent

    fun record(text: String) = store.record(text)
}
