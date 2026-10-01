package com.mudhut.nudge.users.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceLabelTest {

    @Test
    fun `names the common desktop browsers and their platform`() {
        assertEquals(
            "Chrome on macOS",
            DeviceLabel.from(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            ),
        )
        assertEquals(
            "Firefox on Windows",
            DeviceLabel.from("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0"),
        )
    }

    @Test
    fun `names mobile platforms without inventing a model`() {
        // iOS reports "iPhone" and never the model. The stub this replaces
        // claimed "Safari on iPhone 15", which no user agent could produce.
        assertEquals(
            "Safari on iPhone",
            DeviceLabel.from(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            ),
        )
        assertEquals(
            "Chrome on Android",
            DeviceLabel.from(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
            ),
        )
    }

    @Test
    fun `prefers the real browser over the compatibility tokens it impersonates`() {
        // Every Chromium UA contains "Safari" and most contain "Mozilla". Matching
        // in the wrong order labels all of them Safari.
        val edge = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0"
        assertEquals("Edge on Windows", DeviceLabel.from(edge))
    }

    @Test
    fun `falls back rather than guessing`() {
        // A wrong label on a security screen is worse than an absent one: the
        // whole point is recognising your own sessions.
        assertEquals("Unknown device", DeviceLabel.from(null))
        assertEquals("Unknown device", DeviceLabel.from(""))
        assertEquals("Unknown device", DeviceLabel.from("curl/8.4.0"))
    }

    @Test
    fun `names a browser it knows on a platform it does not`() {
        assertEquals(
            "Firefox on Linux",
            DeviceLabel.from("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"),
        )
    }

    @Test
    fun `never returns something longer than the column`() {
        // device_label is varchar(100). A hostile or absurd UA must not blow up
        // the insert.
        val absurd = "Mozilla/5.0 " + "x".repeat(5_000) + " Chrome/140.0.0.0"
        assert(DeviceLabel.from(absurd).length <= 100)
    }
}
