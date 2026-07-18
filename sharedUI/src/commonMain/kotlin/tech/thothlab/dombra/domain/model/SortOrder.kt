package tech.thothlab.dombra.domain.model

import kotlinx.serialization.Serializable

/** Порядок сортировки списка треков (PRD-03 T03, по образцу меню сортировки Cosmos). */
@Serializable
enum class SortOrder(val label: String) {
    MANUAL("Ручной порядок"),
    DATE_ADDED_DESC("Дата добавления (сначала новые)"),
    DATE_ADDED_ASC("Дата добавления (сначала старые)"),
    TITLE_AZ("Название (A-Z)"),
    TITLE_ZA("Название (Z-A)"),
    ARTIST_AZ("Исполнитель (A-Z)"),
    ARTIST_ZA("Исполнитель (Z-A)"),
    SIZE_DESC("Размер (сначала большие)"),
    SIZE_ASC("Размер (сначала маленькие)"),
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
