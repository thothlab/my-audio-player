package tech.thothlab.dombra.domain.model

import kotlinx.serialization.Serializable

/** Порядок сортировки списка треков (PRD-03 T03, по образцу меню сортировки Cosmos). */
@Serializable
enum class SortOrder(val label: String) {
    MANUAL("Вручную"),
    DATE_ADDED_DESC("Дата добавления · новые"),
    DATE_ADDED_ASC("Дата добавления · старые"),
    TITLE_AZ("Название · А–Я"),
    TITLE_ZA("Название · Я–А"),
    ARTIST_AZ("Исполнитель · А–Я"),
    ARTIST_ZA("Исполнитель · Я–А"),
    SIZE_DESC("Размер · большие"),
    SIZE_ASC("Размер · маленькие"),
}

/** Применяет порядок к списку треков. MANUAL — исходный порядок источника (без ре-сортировки). */
fun List<Track>.sortedByOrder(order: SortOrder): List<Track> = when (order) {
    SortOrder.MANUAL -> this
    SortOrder.DATE_ADDED_DESC -> sortedByDescending { it.addedAt }
    SortOrder.DATE_ADDED_ASC -> sortedBy { it.addedAt }
    SortOrder.TITLE_AZ -> sortedBy { it.title.lowercase() }
    SortOrder.TITLE_ZA -> sortedByDescending { it.title.lowercase() }
    SortOrder.ARTIST_AZ -> sortedBy { it.artistName.lowercase() }
    SortOrder.ARTIST_ZA -> sortedByDescending { it.artistName.lowercase() }
    SortOrder.SIZE_DESC -> sortedByDescending { it.fileSize }
    SortOrder.SIZE_ASC -> sortedBy { it.fileSize }
}
