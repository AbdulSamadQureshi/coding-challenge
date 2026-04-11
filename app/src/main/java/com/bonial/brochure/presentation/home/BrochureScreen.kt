package com.bonial.brochure.presentation.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.bonial.brochure.R
import com.bonial.brochure.presentation.model.BrochureUi
import com.bonial.brochure.presentation.theme.CloseLoopWalletTheme
import com.bonial.core.ui.UiState
import com.bonial.core.ui.extensions.shimmerEffect

/**
 * Main screen for displaying brochures.
 * Passes state and callbacks down — stateless composables below this point.
 */
@Composable
fun BrochuresScreen(
    viewModel: BrochuresViewModel,
    onBrochureClick: (BrochureUi) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BrochuresContent(
        uiState = state.brochuresUiState,
        onBrochureClick = onBrochureClick,
        onFavouriteClick = { brochure ->
            viewModel.sendIntent(BrochuresIntent.ToggleFavourite(brochure))
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("brochure_screen")
            .background(MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun BrochuresContent(
    uiState: UiState<List<BrochureUi>>,
    onBrochureClick: (BrochureUi) -> Unit,
    onFavouriteClick: (BrochureUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            is UiState.Loading -> BrochuresLoadingGrid()
            is UiState.Success -> BrochuresGrid(
                brochures = uiState.data,
                onBrochureClick = onBrochureClick,
                onFavouriteClick = onFavouriteClick,
            )
            is UiState.Error -> ErrorMessage(message = uiState.message)
            else -> {}
        }
    }
}

@Composable
fun ErrorMessage(message: String?) {
    Text(
        text = message ?: stringResource(R.string.error_generic),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
fun BrochuresLoadingGrid() {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(10) {
            BrochureShimmerItem()
        }
    }
}

@Composable
fun BrochuresGrid(
    brochures: List<BrochureUi>,
    onBrochureClick: (BrochureUi) -> Unit = {},
    onFavouriteClick: (BrochureUi) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("brochures_grid"),
    ) {
        items(
            items = brochures,
            key = { item -> item.coverUrl ?: item.hashCode() },
        ) { brochure ->
            BrochureItem(
                title = brochure.title,
                imageUrl = brochure.coverUrl,
                publisherName = brochure.publisherName,
                isFavourite = brochure.isFavourite,
                onClick = { onBrochureClick(brochure) },
                onFavouriteClick = { onFavouriteClick(brochure) },
            )
        }
    }
}

/**
 * Atomic brochure card — independent of [BrochureUi], receives only primitive values.
 */
@Composable
fun BrochureItem(
    title: String?,
    imageUrl: String?,
    publisherName: String?,
    isFavourite: Boolean = false,
    onClick: () -> Unit = {},
    onFavouriteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .testTag("brochure_item")
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
        ) {
            BrochureImage(
                imageUrl = imageUrl,
                contentDescription = publisherName,
                onStateChanged = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    isError = state is AsyncImagePainter.State.Error
                },
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().shimmerEffect())
            }

            if (isError) {
                ImageErrorPlaceholder()
            }

            if (!title.isNullOrBlank() && !isLoading && !isError) {
                BrochureTitleOverlay(
                    title = title,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            FavouriteButton(
                isFavourite = isFavourite,
                onToggle = onFavouriteClick,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun FavouriteButton(
    isFavourite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier,
    ) {
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
fun BrochureImage(
    imageUrl: String?,
    contentDescription: String?,
    onStateChanged: (AsyncImagePainter.State) -> Unit,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription ?: stringResource(R.string.content_desc_brochure_image),
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
fun BrochureTitleOverlay(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    startY = 0f,
                ),
            )
            .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BrochureShimmerItem(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
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
private fun BrochuresGridPreview() {
    CloseLoopWalletTheme {
        val mockData = listOf(
            BrochureUi(id = 1, title = "Veganuary Rezepte", publisherName = "Publisher 1", coverUrl = null, distance = 0.5, isFavourite = false),
            BrochureUi(id = 2, title = "Premium Offer", publisherName = "Publisher 2", coverUrl = null, distance = 1.2, isFavourite = true),
        )
        BrochuresGrid(brochures = mockData)
    }
}
