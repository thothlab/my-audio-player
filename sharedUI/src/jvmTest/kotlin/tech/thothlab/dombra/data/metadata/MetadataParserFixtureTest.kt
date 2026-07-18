package tech.thothlab.dombra.data.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tech.thothlab.dombra.domain.model.AudioFormat

/** Парсеры на реальных файлах (сгенерированы tools/generate-fixtures.sh). */
class MetadataParserFixtureTest {

    private fun fixture(name: String): ByteArray {
        val url = checkNotNull(javaClass.classLoader.getResource("fixtures/$name")) {
            "fixture $name not found — run tools/generate-fixtures.sh"
        }
        return url.readBytes()
    }

    private fun parse(name: String): TrackMetadata? {
        val bytes = fixture(name)
        val head = bytes.copyOfRange(0, minOf(bytes.size, StableId.CHUNK))
        val tail = if (bytes.size > StableId.CHUNK) {
            bytes.copyOfRange(bytes.size - StableId.CHUNK, bytes.size)
        } else bytes
        val full = if (MetadataParser.needsFullRead(head)) bytes else null
        return MetadataParser.parse(head, tail, bytes.size.toLong(), full)
    }

    @Test
    fun mp3FullTags() {
        val meta = assertNotNull(parse("fixture.mp3"))
        assertEquals(AudioFormat.MP3, meta.format)
        assertEquals("Dombra Fixture", meta.title)
        assertEquals("Test Artist", meta.artist)
        assertEquals("Test Album", meta.album)
        assertEquals("Test Album Artist", meta.albumArtist)
        assertEquals(2026, meta.year)
        assertEquals(3, meta.trackNo)
        assertEquals(1, meta.discNo)
        assertEquals(44100, meta.sampleRate)
        assertNotNull(meta.durationMs)
        assertTrue(meta.durationMs!! in 2500..3500, "duration=${meta.durationMs}")
        assertEquals(-6.5, meta.replayGain.trackGainDb)
        assertNotNull(meta.artwork, "APIC artwork must be extracted")
    }

    @Test
    fun flacFullTags() {
        val meta = assertNotNull(parse("fixture.flac"))
        assertEquals(AudioFormat.FLAC, meta.format)
        assertEquals("Flac Fixture", meta.title)
        assertEquals("Flac Artist", meta.artist)
        assertEquals("Flac Album", meta.album)
        assertEquals(2025, meta.year)
        assertEquals(7, meta.trackNo)
        assertEquals(44100, meta.sampleRate)
        assertEquals(16, meta.bitDepth)
        assertEquals(1, meta.channels)
        assertTrue(meta.durationMs!! in 2500..3500)
        assertEquals(-3.25, meta.replayGain.trackGainDb)
        assertNotNull(meta.artwork, "FLAC PICTURE must be extracted")
    }

    @Test
    fun flac24Bit() {
        val meta = assertNotNull(parse("24bit.flac"))
        assertEquals(24, meta.bitDepth)
        assertEquals("Hi-Res Fixture", meta.title)
    }

    @Test
    fun wavInfo() {
        val meta = assertNotNull(parse("fixture.wav"))
        assertEquals(AudioFormat.WAV, meta.format)
        assertEquals(44100, meta.sampleRate)
        assertEquals(16, meta.bitDepth)
        assertTrue(meta.durationMs!! in 1500..2500)
    }

    @Test
    fun m4aTags() {
        val meta = assertNotNull(parse("fixture.m4a"))
        assertEquals(AudioFormat.M4A, meta.format)
        assertEquals("M4a Fixture", meta.title)
        assertEquals("M4a Artist", meta.artist)
        assertEquals("M4a Album", meta.album)
        assertTrue(meta.durationMs!! in 1500..2500, "duration=${meta.durationMs}")
    }

    @Test
    fun alacM4aTags() {
        // ALAC в контейнере m4a — часть библиотеки пользователя (failed=8 при индексации)
        val meta = assertNotNull(parse("fixture_alac.m4a"), "ALAC m4a должен парситься")
        assertEquals(AudioFormat.M4A, meta.format)
        assertEquals("Alac Fixture", meta.title)
        assertEquals("Test Artist", meta.artist)
        assertEquals("Test Album", meta.album)
    }


    @Test
    fun oggVorbis() {
        val meta = assertNotNull(parse("fixture.ogg"))
        assertEquals(AudioFormat.OGG_VORBIS, meta.format)
        assertEquals("Ogg Fixture", meta.title)
        assertEquals(44100, meta.sampleRate)
        assertTrue(meta.durationMs!! in 1500..2500, "duration=${meta.durationMs}")
    }

    @Test
    fun opus() {
        val meta = assertNotNull(parse("fixture.opus"))
        assertEquals(AudioFormat.OPUS, meta.format)
        assertEquals("Opus Fixture", meta.title)
        assertEquals(48000, meta.sampleRate)
        assertTrue(meta.durationMs!! in 1500..2500, "duration=${meta.durationMs}")
    }

    @Test
    fun noTagsGivesNulls() {
        val meta = assertNotNull(parse("no-tags.mp3"))
        assertNull(meta.title)
        assertNull(meta.artist)
        assertNotNull(meta.durationMs)
    }

    @Test
    fun corruptedIsRejected() {
        // Мусор с расширением .mp3: формат не распознан → null → индексатор пометит failed
        assertNull(parse("corrupted.mp3"))
    }
}
