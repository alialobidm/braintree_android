package com.braintreepayments.api

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import io.mockk.every
import io.mockk.mockk
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayPalLauncherUnitTest {
    private val browserSwitchClient: BrowserSwitchClient = mockk(relaxed = true)
    private val activity: ComponentActivity = mockk(relaxed = true)
    private val paymentAuthRequestParams: PayPalPaymentAuthRequestParams = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val options: BrowserSwitchOptions = mockk(relaxed = true)
    private val browserSwitchRequest = BrowserSwitchRequest(
        1,
        Uri.parse("http://"),
        JSONObject().put("test_key", "test_value"),
        "return-url-scheme",
        false
    )
    private lateinit var sut: PayPalLauncher

    @Before
    fun setup() {
        every { paymentAuthRequestParams.browserSwitchOptions } returns options
        sut = PayPalLauncher(browserSwitchClient)
    }

    @Test
    fun `launch starts browser switch and returns pending request`() {
        val startedPendingRequest = BrowserSwitchPendingRequest.Started(browserSwitchRequest)
        every { browserSwitchClient.start(activity, options) } returns startedPendingRequest

        val pendingRequest =
            sut.launch(activity, PayPalPaymentAuthRequest.ReadyToLaunch(paymentAuthRequestParams))

        assertTrue(pendingRequest is PayPalPendingRequest.Started)
        assertEquals(
            browserSwitchRequest,
            (pendingRequest as PayPalPendingRequest.Started).request.browserSwitchRequest
        )
    }

    @Test
    fun `launch on error returns pending request failure`() {
        every { paymentAuthRequestParams.browserSwitchOptions } returns options
        val exception = BrowserSwitchException("error")
        every { browserSwitchClient.start(eq(activity), eq(options)) } returns
                BrowserSwitchPendingRequest.Failure(exception)

        val pendingRequest =
            sut.launch(activity, PayPalPaymentAuthRequest.ReadyToLaunch(paymentAuthRequestParams))

        assertTrue(pendingRequest is PayPalPendingRequest.Failure)
        assertSame(exception, (pendingRequest as PayPalPendingRequest.Failure).error)
    }

    @Test
    @Throws(BrowserSwitchException::class)
    fun `launch when device cant perform browser switch returns pending request failure`() {
        every { paymentAuthRequestParams.browserSwitchOptions } returns options
        val exception = BrowserSwitchException("browser switch error")
        every { browserSwitchClient.assertCanPerformBrowserSwitch(
            eq(activity),
            eq(options)) } throws exception

        val pendingRequest =
            sut.launch(activity, PayPalPaymentAuthRequest.ReadyToLaunch(paymentAuthRequestParams))

        assertTrue(pendingRequest is PayPalPendingRequest.Failure)
        assertEquals(
            "AndroidManifest.xml is incorrectly configured or another app " +
                    "defines the same browser switch url as this app. See " +
                    "https://developer.paypal.com/braintree/docs/guides/client-sdk/setup/" +
                    "android/v4#browser-switch-setup " +
                    "for the correct configuration: browser switch error",
            (pendingRequest as PayPalPendingRequest.Failure).error.message
        )
    }

    @Test
    @Throws(JSONException::class)
    fun `handleReturnToAppFromBrowser when result exists returns result`() {
        val result: BrowserSwitchResult = mockk(relaxed = true)
        val browserSwitchPendingRequest = BrowserSwitchPendingRequest.Started(browserSwitchRequest)
        every { browserSwitchClient.parseResult(browserSwitchPendingRequest, intent) } returns result

        val paymentAuthResult = sut.handleReturnToAppFromBrowser(
            PayPalPendingRequest.Started(browserSwitchPendingRequest), intent
        )

        assertNotNull(paymentAuthResult)
        assertSame(result, paymentAuthResult!!.browserSwitchResult)
    }

    @Test
    @Throws(JSONException::class)
    fun `handleReturnToAppFromBrowser when result does not exist returns null`() {
        val browserSwitchPendingRequest = BrowserSwitchPendingRequest.Started(browserSwitchRequest)
        every { browserSwitchClient.parseResult(browserSwitchPendingRequest, intent) } returns null

        val paymentAuthResult = sut.handleReturnToAppFromBrowser(
            PayPalPendingRequest.Started(browserSwitchPendingRequest), intent
        )

        assertNull(paymentAuthResult)
    }
}