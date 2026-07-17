package tech.thothlab.dombra.data.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StableIdTest {

    private val head = ByteArray(1000) { (it % 251).toByte() }
    private val tail = ByteArray(1000) { (it % 13).toByte() }

    @Test
    fun deterministic() {
        assertEquals(
            StableId.compute(12345L, head, tail),
            StableId.compute(12345L, head.copyOf(), tail.copyOf()),
        )
    }

    @Test
    fun sizeAffectsId() {
        assertNotEquals(
            StableId.compute(12345L, head, tail),
            StableId.compute(12346L, head, tail),
        )
    }

    @Test
    fun contentAffectsId() {
        val other = head.copyOf().also { it[0] = 99 }
        assertNotEquals(
            StableId.compute(12345L, head, tail),
            StableId.compute(12345L, other, tail),
        )
    }

    @Test
    fun formatIs64Hex() {
        val id = StableId.compute(1L, head, tail)
        assertEquals(64, id.length)
        assertEquals(true, id.all { it in "0123456789abcdef" })
    }
}
