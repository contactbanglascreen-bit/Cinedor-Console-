package com.cinedor.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the admin app for checking the design without a phone (folder: SCREENSHOT_DIR). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class AdminScreenshotTest {
    private fun shoot(name: String, loggedIn: Boolean, frames: Int = 20) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("cinedor_admin_plain", Context.MODE_PRIVATE).edit().apply {
            if (loggedIn) putString("refresh", "test").putString("email", "admin@example.com").putString("role", "admin")
            else clear()
        }.commit()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            repeat(frames) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)) }
            sc.onActivity { act ->
                val v = act.window.decorView
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(b))
                val out = File(System.getenv("SCREENSHOT_DIR") ?: ctx.cacheDir.absolutePath).apply { mkdirs() }
                File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun login() = shoot("admin-login", false)
}
