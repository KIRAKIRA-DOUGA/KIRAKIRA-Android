package moe.kirakira.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records startup and the first feed scroll; run against a staging build only. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "moe.kirakira", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
