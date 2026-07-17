package tech.thothlab.dombra.core

/** Языковой тег системы, BCP-47-подобный ("ru-RU", "en"). */
internal expect fun systemLocaleTag(): String

/** Имя платформы для диагностики и capability-отчётов. */
internal expect val platformName: String
