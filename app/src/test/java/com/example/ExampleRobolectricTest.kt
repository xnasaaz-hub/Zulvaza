package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionStore
import com.example.data.model.NotificationType
import com.example.data.model.SellerNotificationItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ZULVAZA Seller Center", appName)
  }

  @Test
  fun `app lock 5 digit pin lifecycle hashes verifies toggles and deletes`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SessionStore(context)

    assertFalse(store.hasAppPin())
    assertFalse(store.isAppPinEnabled())

    store.setAppPin("48291", enabled = true)
    assertTrue(store.hasAppPin())
    assertTrue(store.isAppPinEnabled())
    assertTrue(store.verifyAppPin("48291"))
    assertFalse(store.verifyAppPin("12345"))

    store.setAppPinEnabled(false)
    assertTrue(store.hasAppPin())
    assertFalse(store.isAppPinEnabled())

    store.deleteAppPin()
    assertFalse(store.hasAppPin())
    assertFalse(store.isAppPinEnabled())
    assertFalse(store.verifyAppPin("48291"))
  }

  @Test
  fun `notifications are scoped per user account`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SessionStore(context)

    val userANotification = SellerNotificationItem(
      id = "notif_a_1",
      title = "Verification Approved",
      message = "Seller store verified",
      type = NotificationType.VERIFICATION,
      createdAtEpochMs = 1000L,
      isRead = false,
      targetRoute = "verification"
    )
    store.saveNotifications(listOf(userANotification), userId = "user-alpha")

    val alphaLoaded = store.loadNotifications(userId = "user-alpha")
    val betaLoaded = store.loadNotifications(userId = "user-beta")

    assertEquals(1, alphaLoaded.size)
    assertEquals("notif_a_1", alphaLoaded.first().id)
    assertTrue(betaLoaded.isEmpty())
  }
}
