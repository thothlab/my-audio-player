package tech.thothlab.dombra.data.store

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf

/**
 * Библиотека в browser storage (localStorage через kstore) — эквивалент
 * Room-хранилища других платформ (T03, specs/storage «Перезапуск вкладки»).
 * После создания вызвать [PersistentLibraryStore.load].
 */
fun webLibraryStore(key: String = "dombra-library"): PersistentLibraryStore {
    val store: KStore<LibrarySnapshot> = storeOf(key = key)
    return PersistentLibraryStore(
        persist = { store.set(it) },
        restore = { store.get() },
    )
}
