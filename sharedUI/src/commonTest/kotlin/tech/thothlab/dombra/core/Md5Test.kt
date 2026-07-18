package tech.thothlab.dombra.core

import kotlin.test.Test
import kotlin.test.assertEquals

class Md5Test {
    @Test fun emptyString() = assertEquals("d41d8cd98f00b204e9800998ecf8427e", Md5.hex(""))
    @Test fun abc() = assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.hex("abc"))
    @Test fun quickBrownFox() =
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", Md5.hex("The quick brown fox jumps over the lazy dog"))

    // Subsonic-схема: t = md5(password + salt).
    @Test fun subsonicToken() =
        assertEquals("26719a1196d2a940705a59634eb18eab", Md5.hex("sesame" + "c19b2d"))
}
