package com.music.vivi.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import com.music.vivi.ui.component.NavigationTitle
import com.music.vivi.viewmodels.SwipeViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The main screen for the Swipe Mode feature.
 * Displays a stack of swipeable song cards and handles the top-level UI logic.
 *
 * @param navController The navigation controller for navigating back.
 * @param viewModel The view model that manages the song stack and swipe actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    navController: NavController,
    viewModel: SwipeViewModel = hiltViewModel()
) {
    val stack by viewModel.swipeStack.collectAsState()
    
    // UI Layout...
    
    Scaffold(
        topBar = {
            NavigationTitle(
                title = "Discover",
                onClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (stack.isEmpty()) {
                CircularProgressIndicator() // Or empty state
            } else {
                // Show reversed stack so top item (index 0) is rendered last (on top)
                // Actually, we usually render them in order but user sees the LAST one as top if using Box?
                // Box z-order: last child is on top.
                // So if stack is [A, B, C], A is top card in logic.
                // We should render C, B, A.
                
                val visibleCards = stack.take(3).reversed()
                visibleCards.forEachIndexed { index, song ->
                    // Use key to ensure state (like drag offset) is tied to the specific song,
                    // not the position in the list. This prevents state recycling bugs.
                    androidx.compose.runtime.key(song.id) {
                        val isTopCard = (song == stack.first())

                        SwipeableCard(
                            song = song,
                            isTopCard = isTopCard,
                            onSwipeLeft = { viewModel.swipeLeft(song) },
                            onSwipeRight = { viewModel.swipeRight(song) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A reusable composable representing a single song card that can be swiped.
 * Handles drag gestures, calculates rotation/opacity based on swipe distance,
 * and displays visual feedback overlays (Like/Dislike).
 *
 * @param song The song item to display.
 * @param isTopCard Whether this card is on top of the stack (only the top card is draggable).
 * @param onSwipeLeft Callback triggered when the card is swiped to the left (Dislike).
 * @param onSwipeRight Callback triggered when the card is swiped to the right (Like).
 */
@Composable
fun SwipeableCard(
    song: SongItem,
    isTopCard: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val threshold = screenWidthPx * 0.3f
    val scope = rememberCoroutineScope()

    val rotation = (offsetX / screenWidthPx) * 15f
    // Alpha for crossfade effect if needed, though card is usually opaque
    // val alpha = 1f 

    // Overlay icons opacity
    val likeAlpha = (offsetX / threshold).coerceIn(0f, 1f)
    val dislikeAlpha = (-offsetX / threshold).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .rotate(rotation)
            .fillMaxWidth(0.9f) // Cards are slightly smaller than screen
            .padding(16.dp)
            .let { m ->
                if (isTopCard) {
                    m.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            offsetX += delta
                        },
                        onDragStopped = {
                            if (offsetX > threshold) {
                                // Swipe Right
                                scope.launch { onSwipeRight() }
                            } else if (offsetX < -threshold) {
                                // Swipe Left
                                scope.launch { onSwipeLeft() }
                            } else {
                                // Reset
                                offsetX = 0f
                            }
                        }
                    )
                } else {
                    m
                }
            }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxSize(0.8f) // Square-ish?
        ) {
            Box {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Gradient or overlay for text
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        ))
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = song.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(text = song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha=0.7f))
                }

                // Dislike Overlay
                if (dislikeAlpha > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = dislikeAlpha * 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dislike",
                            modifier = Modifier.size(100.dp).alpha(dislikeAlpha),
                            tint = Color.White
                        )
                    }
                }
                
                // Like Overlay
                if (likeAlpha > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Green.copy(alpha = likeAlpha * 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like",
                            modifier = Modifier.size(100.dp).alpha(likeAlpha),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
