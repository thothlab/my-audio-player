package tech.thothlab.dombra.data.remote.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO ответов Subsonic/OpenSubsonic (только используемые поля; ignoreUnknownKeys). */

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String = "failed",
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val error: SubError? = null,
    val albumList2: AlbumList2? = null,
    val artists: ArtistsIndex? = null,
    val album: AlbumWithSongs? = null,
    val searchResult3: SearchResult3? = null,
    val starred2: Starred2? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: PlaylistWithSongs? = null,
)

@Serializable
data class SubError(val code: Int = 0, val message: String = "")

@Serializable
data class AlbumList2(val album: List<SubAlbum> = emptyList())

@Serializable
data class ArtistsIndex(val index: List<SubIndex> = emptyList())

@Serializable
data class SubIndex(val name: String = "", val artist: List<SubArtist> = emptyList())

@Serializable
data class SearchResult3(
    val song: List<SubSong> = emptyList(),
    val artist: List<SubArtist> = emptyList(),
    val album: List<SubAlbum> = emptyList(),
)

@Serializable
data class Starred2(
    val song: List<SubSong> = emptyList(),
    val artist: List<SubArtist> = emptyList(),
    val album: List<SubAlbum> = emptyList(),
)

@Serializable
data class PlaylistsWrapper(val playlist: List<SubPlaylist> = emptyList())

@Serializable
data class PlaylistWithSongs(val id: String, val name: String = "", val entry: List<SubSong> = emptyList())

@Serializable
data class AlbumWithSongs(val id: String, val name: String = "", val song: List<SubSong> = emptyList())

@Serializable
data class SubAlbum(
    val id: String,
    val name: String = "",
    val artist: String = "",
    val artistId: String = "",
    val coverArt: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
)

@Serializable
data class SubArtist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
)

@Serializable
data class SubPlaylist(
    val id: String,
    val name: String = "",
    val songCount: Int = 0,
    val coverArt: String? = null,
)

@Serializable
data class SubSong(
    val id: String,
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val coverArt: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val size: Long = 0,
    val suffix: String? = null,
    val contentType: String? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
)
