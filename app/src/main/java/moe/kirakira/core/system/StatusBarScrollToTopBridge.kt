package moe.kirakira.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Activity-owned adapter. Scroll targets and callbacks exist only for their UI lifetime. */
internal class StatusBarScrollToTopBridge(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    internal class Target(
        val enabled: () -> Boolean,
        val canScrollToTop: () -> Boolean,
        val scope: CoroutineScope,
        val scrollToTop: suspend () -> Unit,
        val priority: Int,
    )

    private val targets = mutableListOf<Target>()
    private var resumed = false
    private var selected: Target? = null
    private var request: Job? = null
    private var generation = 0L
    private var proxy: ScrollView? = null
    private var armed = false
    private var nativeRequestPending = false
    private var registered = false
    private var disposed = false
    private val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    private val useProxy = manufacturer in setOf("xiaomi", "redmi", "poco")
    private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { refresh() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in ACTIONS) requestScrollToTop()
        }
    }

    fun attach() {
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)
        activity.lifecycle.addObserver(this)
    }

    fun add(target: Target) {
        targets.add(target)
        refresh()
    }

    fun remove(target: Target) {
        targets.remove(target)
        refresh()
    }

    override fun onResume(owner: LifecycleOwner) {
        resumed = true
        val filter = when (manufacturer) {
            "oppo", "oneplus", "realme" -> IntentFilter().apply {
                addAction(OPLUS_CLICK_TOP)
                addAction(COLOR_CLICK_TOP)
            }
            "huawei", "honor" -> IntentFilter(HUAWEI_CLICK_TOP)
            else -> null
        }
        if (filter != null && !registered) {
            try {
                ContextCompat.registerReceiver(
                    activity,
                    receiver,
                    filter,
                    if (filter.hasAction(HUAWEI_CLICK_TOP)) HUAWEI_SENDER_PERMISSION else null,
                    null,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                registered = true
            } catch (_: SecurityException) {
                // Private OEM broadcasts are optional; unavailable adapters leave scrolling untouched.
            }
        }
        refresh()
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
        if (registered) {
            activity.unregisterReceiver(receiver)
            registered = false
        }
        refresh()
    }

    fun refresh() {
        if (disposed) return
        val enabledTargets = targets.filter { it.enabled() }
        val modal = enabledTargets.firstOrNull { it.priority == Int.MAX_VALUE }
        val target = modal ?: selected?.takeIf { request != null && it in enabledTargets }
            ?: enabledTargets.filter { it.canScrollToTop() }.maxByOrNull { it.priority }
        val available = resumed && activity.window.decorView.hasWindowFocus()
        if (target !== selected || !available) {
            generation++
            nativeRequestPending = false
            request?.cancel()
            request = null
        }
        val changedTarget = target !== selected
        selected = target
        val shouldArm = available && request == null && !nativeRequestPending && target?.canScrollToTop() == true
        if (useProxy && (proxy == null || changedTarget || armed != shouldArm)) {
            armed = shouldArm
            replaceProxy()
        }
    }

    private fun requestScrollToTop() {
        if (disposed || !resumed || !activity.window.decorView.hasWindowFocus() || request != null) return
        val target = selected?.takeIf { it.enabled() && it.canScrollToTop() } ?: return
        val requestGeneration = ++generation
        request = target.scope.launch(start = CoroutineStart.LAZY) {
            try {
                target.scrollToTop()
            } finally {
                if (requestGeneration == generation) {
                    request = null
                    // Retire the OEM animation before rearming a cancelled request.
                    armed = false
                    proxy?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    proxy = null
                    refresh()
                }
            }
        }
        request?.start()
    }

    private fun replaceProxy() {
        proxy?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val next = object : ScrollView(activity) {
            private var synchronizing = false
            private var synchronizedHeight = -1

            init {
                isFocusable = false
                isFocusableInTouchMode = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                // A real range and visible, nonzero viewport are required by the OEM view traversal.
                addView(View(activity), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }

            override fun onTouchEvent(event: MotionEvent): Boolean = false
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false

            override fun canScrollVertically(direction: Int): Boolean =
                this === proxy && armed && resumed && request == null &&
                    activity.window.decorView.hasWindowFocus() && super.canScrollVertically(direction)

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // ScrollView measures its child with an unspecified vertical constraint. A plain
                // View ignores layoutParams.height there, so provide an actual intrinsic minimum.
                getChildAt(0).minimumHeight = MeasureSpec.getSize(heightMeasureSpec) + SENTINEL_OFFSET * 2
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                synchronizing = true
                super.onLayout(changed, left, top, right, bottom)
                if (this === proxy && synchronizedHeight != height) {
                    synchronizedHeight = height
                    scrollTo(0, if (armed) SENTINEL_OFFSET else 0)
                }
                synchronizing = false
            }

            override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
                super.onScrollChanged(x, y, oldX, oldY)
                if (this === proxy && !synchronizing && armed && y == 0 && oldY > 0) {
                    armed = false
                    nativeRequestPending = true
                    // Leave the framework's scroll callback before changing its view hierarchy.
                    post {
                        if (this === proxy) {
                            nativeRequestPending = false
                            requestScrollToTop()
                        }
                    }
                }
            }
        }
        proxy = next
        content.addView(
            next,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun dispose() {
        disposed = true
        generation++
        request?.cancel()
        request = null
        if (registered) activity.unregisterReceiver(receiver)
        registered = false
        activity.lifecycle.removeObserver(this)
        activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
        proxy?.let { (it.parent as? ViewGroup)?.removeView(it) }
        proxy = null
        selected = null
        targets.clear()
    }

    private companion object {
        const val SENTINEL_OFFSET = 100
        const val OPLUS_CLICK_TOP = "com.oplus.clicktop"
        const val COLOR_CLICK_TOP = "com.color.clicktop"
        const val HUAWEI_CLICK_TOP = "com.huawei.intent.action.CLICK_STATUSBAR"
        const val HUAWEI_SENDER_PERMISSION = "huawei.permission.CLICK_STATUSBAR_BROADCAST"
        val ACTIONS = setOf(OPLUS_CLICK_TOP, COLOR_CLICK_TOP, HUAWEI_CLICK_TOP)
    }
}
