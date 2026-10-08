package ca.mattmccormick.phone_sense

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppInfoResolverTest {
    private lateinit var packageManager: PackageManager
    private lateinit var shadowPackageManager: ShadowPackageManager
    private lateinit var resolver: AppInfoResolver

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        packageManager = application.packageManager
        shadowPackageManager = shadowOf(packageManager)
        resolver = AppInfoResolver(packageManager)
    }

    @Test
    fun launcherActivityResolvesToItsLabelAndIcon() {
        val packageName = "com.example.readable"
        val icon = ColorDrawable(Color.MAGENTA)
        addLauncherActivity(packageName, "Readable App", icon)

        val result = resolver.resolve(packageName)

        assertEquals("Readable App", result.label)
        assertSame(icon, result.icon)
    }

    @Test
    fun unknownPackageFallsBackToPackageNameAndNoIcon() {
        val packageName = "com.example.unknown"

        val result = resolver.resolve(packageName)

        assertEquals(packageName, result.label)
        assertEquals(null, result.icon)
    }

    @Test
    fun resultsAreCachedAcrossResolversForProcessLifetime() {
        val packageName = "com.example.cached"
        addLauncherActivity(packageName, "Cached App", packageManager.defaultActivityIcon)
        val firstResult = resolver.resolve(packageName)
        shadowPackageManager.setResolveInfosForIntent(launcherIntent(packageName), emptyList())

        val redrawResults = List(60) {
            AppInfoResolver(packageManager).resolve(packageName)
        }

        redrawResults.forEach { assertSame(firstResult, it) }
    }

    private fun addLauncherActivity(packageName: String, label: String, icon: Drawable) {
        val component = ComponentName(packageName, "$packageName.MainActivity")
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = component.className
            this.applicationInfo = applicationInfo
            nonLocalizedLabel = label
            this.icon = TEST_ICON_RESOURCE
        }
        val launcherFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        shadowPackageManager.addOrUpdateActivity(activityInfo)
        shadowPackageManager.addIntentFilterForActivity(
            component,
            launcherFilter,
        )
        shadowPackageManager.addDrawableResolution(packageName, TEST_ICON_RESOURCE, icon)
    }

    private fun launcherIntent(packageName: String) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName)

    private companion object {
        const val TEST_ICON_RESOURCE = 1234
    }
}
