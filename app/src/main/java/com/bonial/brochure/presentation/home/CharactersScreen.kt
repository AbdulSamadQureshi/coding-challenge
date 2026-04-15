package com.bonial.brochure.presentation.home

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.placeholder
import com.bonial.brochure.R
import com.bonial.brochure.presentation.model.CharacterUi
import com.bonial.brochure.presentation.theme.CloseLoopWalletTheme
import com.bonial.brochure.presentation.theme.toStatusColorSet
import com.bonial.core.ui.extensions.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    viewModel: CharactersViewModel,
    onCharacterClick: (Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyGridState = rememberLazyGridState()

    // Reset scroll position when search query changes (including clearing search)
    LaunchedEffect(state.searchQuery) {
        lazyGridState.scrollToItem(0)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val shouldLoadMore by remember(lazyGridState) {
        derivedStateOf {
            val lastVisible = lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = lazyGridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 4 && totalItems > 0
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.sendIntent(CharactersIntent.LoadNextPage)
    }

    LaunchedEffect(viewModel.effect, lifecycleOwner) {
        viewModel.effect
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    is CharactersEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                }
            }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("characters_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar — always visible so user can search even after pagination.
            // Top padding offsets the status bar so the bar never sits behind it.
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.sendIntent(CharactersIntent.Search(it)) },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        enabled = !state.isInitialLoading,
                        placeholder = { Text(stringResource(R.string.hint_search)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.sendIntent(CharactersIntent.Search("")) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_search))
                                }
                            }
                        },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 8.dp,
                    ),
            ) {}

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading || (state.isInitialLoading && state.characters.isEmpty()) -> {
                        CharactersLoadingGrid(bottomPadding = innerPadding.calculateBottomPadding())
                    }
                    state.error != null && state.characters.isEmpty() -> ErrorMessage(
                        message = state.error,
                        onRetry = { viewModel.sendIntent(CharactersIntent.LoadCharacters) },
                    )
                    state.characters.isEmpty() && state.searchQuery.isNotBlank() -> EmptySearchState(query = state.searchQuery)
                    state.characters.isEmpty() -> EmptyState()
                    else -> CharactersGrid(
                        characters = state.characters,
                        isLoadingNextPage = state.isLoadingNextPage,
                        lazyGridState = lazyGridState,
                        onCharacterClick = onCharacterClick,
                        onFavouriteClick = { character ->
                            viewModel.sendIntent(CharactersIntent.ToggleFavourite(character))
                        },
                        bottomPadding = innerPadding.calculateBottomPadding(),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🪐", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_state_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_state_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptySearchState(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🔍", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_search_title, query),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_search_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CharactersLoadingGrid(bottomPadding: Dp = 0.dp) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(10) { CharacterShimmerItem() }
    }
}

@Composable
fun CharactersGrid(
    characters: List<CharacterUi>,
    isLoadingNextPage: Boolean,
    lazyGridState: LazyGridState,
    onCharacterClick: (Int) -> Unit,
    onFavouriteClick: (CharacterUi) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomPadding),
        modifier = modifier
            .fillMaxSize()
            .testTag("characters_grid"),
    ) {
        items(items = characters, key = { it.id }) { character ->
            CharacterItem(
                name = character.name,
                status = character.status,
                imageUrl = character.imageUrl,
                isFavourite = character.isFavourite,
                onClick = { onCharacterClick(character.id) },
                onFavouriteClick = { onFavouriteClick(character) },
            )
        }

        if (isLoadingNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("next_page_loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(
    message: String?,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message ?: stringResource(R.string.error_generic),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.label_retry))
            }
        }
    }
}

@Composable
fun CharacterItem(
    name: String?,
    status: String?,
    imageUrl: String?,
    isFavourite: Boolean = false,
    onClick: () -> Unit = {},
    onFavouriteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "card_scale",
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .testTag("character_item"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
        ) {
            CharacterImage(
                imageUrl = imageUrl,
                contentDescription = name,
                onStateChanged = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    isError = state is AsyncImagePainter.State.Error
                },
            )
            if (isLoading) Box(modifier = Modifier.fillMaxSize().shimmerEffect())
            if (isError) ImageErrorPlaceholder()
            if (!status.isNullOrBlank() && !isLoading && !isError) {
                StatusBadge(
                    status = status,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            FavouriteButton(
                isFavourite = isFavourite,
                onToggle = onFavouriteClick,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            if (!name.isNullOrBlank() && !isLoading && !isError) {
                CharacterNameOverlay(
                    name = name,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val colors = status.toStatusColorSet()
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(colors.dot))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = status,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun FavouriteButton(
    isFavourite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavourite) R.string.content_desc_unfavourite else R.string.content_desc_favourite,
            ),
            tint = if (isFavourite) MaterialTheme.colorScheme.error else Color.White,
        )
    }
}

@Composable
fun CharacterImage(
    imageUrl: String?,
    contentDescription: String?,
    onStateChanged: (AsyncImagePainter.State) -> Unit,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription ?: stringResource(R.string.content_desc_character_image),
        onState = onStateChanged,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun ImageErrorPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("error_placeholder")
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = R.drawable.placeholder_error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.error_image_unavailable),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun CharacterNameOverlay(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    startY = 0f,
                ),
            )
            .padding(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CharacterShimmerItem(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(8.dp).fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shimmerEffect(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CharactersGridPreview() {
    CloseLoopWalletTheme {
        val mockData = listOf(
            CharacterUi(id = 1, name = "Rick Sanchez", status = "Alive", species = "Human", imageUrl = null, isFavourite = false),
            CharacterUi(id = 2, name = "Morty Smith", status = "Dead", species = "Human", imageUrl = null, isFavourite = true),
        )
        CharactersGrid(
            characters = mockData,
            isLoadingNextPage = false,
            lazyGridState = rememberLazyGridState(),
            onCharacterClick = {},
            onFavouriteClick = {},
        )
    }
}
