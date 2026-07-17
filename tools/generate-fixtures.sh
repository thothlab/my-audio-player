#!/usr/bin/env bash
# Генерация тестовых аудиофайлов (синтетические сигналы, свободны от авторских прав).
# Требуется ffmpeg. Выход: sharedUI/src/jvmTest/resources/fixtures/
set -euo pipefail

DIR="$(cd "$(dirname "$0")/.." && pwd)/sharedUI/src/jvmTest/resources/fixtures"
mkdir -p "$DIR"
FF="${FFMPEG:-ffmpeg}"

# Обложка 64x64
"$FF" -y -f lavfi -i "color=c=orange:s=64x64:d=1" -frames:v 1 "$DIR/cover.png" 2>/dev/null

# MP3: ID3v2.3 + обложка + ReplayGain TXXX
"$FF" -y -f lavfi -i "sine=frequency=440:duration=3" -i "$DIR/cover.png" \
  -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v copy \
  -id3v2_version 3 -write_id3v1 1 \
  -metadata title="Dombra Fixture" -metadata artist="Test Artist" \
  -metadata album="Test Album" -metadata album_artist="Test Album Artist" \
  -metadata date="2026" -metadata track="3/12" -metadata disc="1/1" \
  -metadata REPLAYGAIN_TRACK_GAIN="-6.50 dB" -metadata REPLAYGAIN_TRACK_PEAK="0.988547" \
  -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
  "$DIR/fixture.mp3" 2>/dev/null

# FLAC: vorbis comments + ReplayGain + PICTURE
"$FF" -y -f lavfi -i "sine=frequency=523:duration=3" -i "$DIR/cover.png" \
  -map 0:a -map 1:v -c:a flac -c:v png \
  -metadata title="Flac Fixture" -metadata artist="Flac Artist" \
  -metadata album="Flac Album" -metadata date="2025" -metadata track="7" \
  -metadata REPLAYGAIN_TRACK_GAIN="-3.25 dB" \
  -disposition:v attached_pic \
  "$DIR/fixture.flac" 2>/dev/null

# FLAC 24-bit
"$FF" -y -f lavfi -i "sine=frequency=660:duration=2" \
  -c:a flac -sample_fmt s32 -bits_per_raw_sample 24 \
  -metadata title="Hi-Res Fixture" \
  "$DIR/24bit.flac" 2>/dev/null

# WAV
"$FF" -y -f lavfi -i "sine=frequency=330:duration=2" \
  -c:a pcm_s16le \
  -metadata title="Wav Fixture" -metadata artist="Wav Artist" \
  "$DIR/fixture.wav" 2>/dev/null

# M4A (AAC)
"$FF" -y -f lavfi -i "sine=frequency=880:duration=2" \
  -c:a aac -b:a 96k \
  -metadata title="M4a Fixture" -metadata artist="M4a Artist" -metadata album="M4a Album" \
  "$DIR/fixture.m4a" 2>/dev/null

# OGG Vorbis (встроенный экспериментальный кодек; libvorbis может отсутствовать)
"$FF" -y -f lavfi -i "sine=frequency=220:duration=2" \
  -c:a vorbis -strict -2 \
  -metadata title="Ogg Fixture" -metadata artist="Ogg Artist" \
  "$DIR/fixture.ogg" 2>/dev/null

# Opus
"$FF" -y -f lavfi -i "sine=frequency=440:duration=2" \
  -c:a libopus -b:a 64k \
  -metadata title="Opus Fixture" -metadata artist="Opus Artist" \
  "$DIR/fixture.opus" 2>/dev/null

# MP3 без тегов
"$FF" -y -f lavfi -i "sine=frequency=550:duration=1" \
  -map_metadata -1 -c:a libmp3lame -b:a 96k -write_id3v1 0 -id3v2_version 0 \
  "$DIR/no-tags.mp3" 2>/dev/null

# Повреждённый файл с валидным расширением
head -c 4096 /dev/urandom > "$DIR/corrupted.mp3"

ls -la "$DIR"
