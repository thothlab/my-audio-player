package tech.thothlab.dombra.domain.model

import tech.thothlab.dombra.data.repo.testTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class SortOrderTest {
    // a: Beta/Zeta, added 100, size 300 · b: Alpha/Yield, added 300, size 100 · c: Gamma/Xray, added 200, size 200
    private val tracks = listOf(
        testTrack("a", "Beta", artist = "Zeta", addedAt = 100).copy(fileSize = 300),
        testTrack("b", "Alpha", artist = "Yield", addedAt = 300).copy(fileSize = 100),
        testTrack("c", "Gamma", artist = "Xray", addedAt = 200).copy(fileSize = 200),
    )

    private fun ids(order: SortOrder) = tracks.sortedByOrder(order).map { it.stableId }

    @Test fun manualKeepsSourceOrder() = assertEquals(listOf("a", "b", "c"), ids(SortOrder.MANUAL))

    @Test fun titleAz() = assertEquals(listOf("b", "a", "c"), ids(SortOrder.TITLE_AZ))
    @Test fun titleZa() = assertEquals(listOf("c", "a", "b"), ids(SortOrder.TITLE_ZA))

    @Test fun artistAz() = assertEquals(listOf("c", "b", "a"), ids(SortOrder.ARTIST_AZ))
    @Test fun artistZa() = assertEquals(listOf("a", "b", "c"), ids(SortOrder.ARTIST_ZA))

    @Test fun dateNewestFirst() = assertEquals(listOf("b", "c", "a"), ids(SortOrder.DATE_ADDED_DESC))
    @Test fun dateOldestFirst() = assertEquals(listOf("a", "c", "b"), ids(SortOrder.DATE_ADDED_ASC))

    @Test fun sizeLargestFirst() = assertEquals(listOf("a", "c", "b"), ids(SortOrder.SIZE_DESC))
    @Test fun sizeSmallestFirst() = assertEquals(listOf("b", "c", "a"), ids(SortOrder.SIZE_ASC))
}
