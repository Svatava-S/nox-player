
package com.nox.player

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri, val albumId: Long)

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var songsState = mutableStateOf<List<Song>>(emptyList())
    private var currentSongState = mutableStateOf<Song?>(null)
    private var isPlayingState = mutableStateOf(false)
    private var currentPositionState = mutableStateOf(0)
    private var durationState = mutableStateOf(0)
    private var hasPermissionState = mutableStateOf(false)
    private var isShuffleState = mutableStateOf(false)
    private var isRepeatState = mutableStateOf(false)
    private var currentIndex = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermissionState.value = true
            loadAudioFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermission()

        setContent {
            NoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0C)
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun checkAndRequestPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            hasPermissionState.value = true
            loadAudioFiles()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadAudioFiles() {
        val list = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val cursor: Cursor? = contentResolver.query(collection, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC")
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Unknown"
                val artist = it.getString(artistCol) ?: "Unknown Artist"
                val albumId = it.getLong(albumIdCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list.add(Song(id, title, artist, uri, albumId))
            }
        }
        songsState.value = list
    }

    private fun playSong(index: Int) {
        if (songsState.value.isEmpty()) return
        currentIndex = index
        val song = songsState.value[index]
        currentSongState.value = song

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, song.uri)
            prepare()
            start()
            setOnCompletionListener {
                if (isRepeatState.value) {
                    playSong(currentIndex)
                } else {
                    playNext()
                }
            }
        }
        isPlayingState.value = true
        durationState.value = mediaPlayer?.duration ?: 0
    }

    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlayingState.value = false
            } else {
                it.start()
                isPlayingState.value = true
            }
        } ?: if (songsState.value.isNotEmpty()) {
            playSong(0)
        }
    }

    private fun playNext() {
        if (songsState.value.isEmpty()) return
        val nextIdx = if (isShuffleState.value) {
            songsState.value.indices.random()
        } else {
            (currentIndex + 1) % songsState.value.size
        }
        playSong(nextIdx)
    }

    private fun playPrevious() {
        if (songsState.value.isEmpty()) return
        val prevIdx = if (currentIndex > 0) currentIndex - 1 else songsState.value.size - 1
        playSong(prevIdx)
    }

    @Composable
    fun NoxTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF8A2BE2),
                secondary = Color(0xFF00FFFF),
                background = Color(0xFF0A0A0C),
                surface = Color(0xFF141419)
            ),
            content = content
        )
    }

    @Composable
    fun MainScreen() {
        var showNowPlaying by remember { mutableStateOf(false) }
        val songs by songsState
        val currentSong by currentSongState
        val hasPermission by hasPermissionState

        // Update progress bar ticker
        LaunchedEffect(isPlayingState.value) {
            while (isPlayingState.value) {
                currentPositionState.value = mediaPlayer?.currentPosition ?: 0
                delay(500)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("N O X   P L A Y E R", fontWeight = Bold, letterSpacing = 2.sp, fontSize = 16.sp, color = Color.White) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                if (currentSong != null) {
                    MiniPlayer(currentSong!!) { showNowPlaying = true }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (!hasPermission) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Audio permission required.", color = Color.Gray)
                        Spacer(modifier = Modifier.height(12))
                        Button(onClick = { checkAndRequestPermission() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2))) {
                            Text("Grant Permission")
                        }
                    }
                } else if (songs.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF00FFFF))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(songs) { index, song ->
                            val isSelected = currentSong?.id == song.id
                            SongListItem(song = song, isSelected = isSelected) {
                                playSong(index)
                            }
                        }
                    }
                }

                if (showNowPlaying && currentSong != null) {
                    NowPlayingScreen(currentSong!!) { showNowPlaying = false }
                }
            }
        }
    }

    @Composable
    fun SongListItem(song: Song, isSelected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E24)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00FFFF))
            }
            Spacer(modifier = Modifier.width(14))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) Color(0xFF00FFFF) else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2))
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color(0xFF8A2BE2))
            }
        }
    }

    @Composable
    fun MiniPlayer(song: Song, onClick: () -> Unit) {
        val isPlaying by isPlayingState
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(68.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF16161C),
            border = BorderStroke(1.dp, Color(0xFF8A2BE2).copy(alpha = 0.4))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8A2BE2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Album, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = { togglePlayPause() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00FFFF))
                }
            }
        }
    }

    @Composable
    fun NowPlayingScreen(song: Song, onDismiss: () -> Unit) {
        val isPlaying by isPlayingState
        val isShuffle by isShuffleState
        val isRepeat by isRepeatState
        val currentPos by currentPositionState
        val duration by durationState

        // Spinning Vinyl Animation
        val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0C)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.height(20))
                Text("NOW PLAYING", fontSize = 12.sp, letterSpacing = 3.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(40))

                // Spinning Vinyl Graphic
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .rotate(if (isPlaying) angle else 0f)
                        .shadow(30.dp, CircleShape, spotColor = Color(0xFF8A2BE2))
                        .background(Color.Black, CircleShape)
                        .border(8.dp, Color(0xFF1E1E24), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(230.dp).border(1.dp, Color.White.copy(alpha = 0.08), CircleShape))
                    Box(modifier = Modifier.size(180.dp).border(1.dp, Color.White.copy(alpha = 0.05), CircleShape))
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF00FFFF))),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(18.dp).background(Color(0xFF0A0A0C), CircleShape))
                    }
                }

                Spacer(modifier = Modifier.height(50))
                Text(song.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6))
                Text(song.artist, fontSize = 14.sp, color = Color(0xFF00FFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(30))

                // Slider
                Slider(
                    value = currentPos.toFloat().coerceIn(0f, if (duration > 0) duration.toFloat() else 1f),
                    onValueChange = { media->
                        mediaPlayer?.seekTo(media.toInt())
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF8A2BE2),
                        activeTrackColor = Color(0xFF00FFFF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPos), color = Color.Gray, fontSize = 11.sp)
                    Text(formatTime(duration), color = Color.Gray, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(20))

                // Controls Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isShuffleState.value = !isShuffle }) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (isShuffle) Color(0xFF00FFFF) else Color.Gray)
                    }
                    IconButton(onClick = { playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF00FFFF))), CircleShape)
                            .clickable { togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { playNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { isRepeatState.value = !isRepeat }) {
                        Icon(Icons.Default.Repeat, contentDescription = null, tint = if (isRepeat) Color(0xFF00FFFF) else Color.Gray)
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

