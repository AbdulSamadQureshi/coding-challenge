package com.bonial.brochure.presentation.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.tooling.preview.Preview
import com.bonial.brochure.presentation.home.CharacterDetailEffect
import com.bonial.brochure.presentation.home.CharacterDetailIntent
import com.bonial.brochure.presentation.home.CharacterDetailViewModel
import com.bonial.brochure.presentation.home.ErrorMessage
import com.bonial.brochure.presentation.home.ImageErrorPlaceholder
import com.bonial.brochure.presentation.model.CharacterDetailUi
import com.bonial.brochure.presentation.theme.CloseLoopWalletTheme
import com.bonial.brochure.presentation.theme.toStatusColorSet
import com.bonial.core.ui.extensions.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    viewModel: CharacterDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect one-shot effects — share text is built by the ViewModel (pure logic),
    // the composable only calls startActivity (Android UI concern).
    LaunchedEffect(viewModel.effect, lifecycleOwner) {
        viewModel.effect
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    is CharacterDetailEffect.Share -> {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, effect.text)
                            }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.character?.name ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.label_back),
                        )
                    }
                },
                actions = {
                    if (state.character != null) {
                        IconButton(
                            onClick = { viewModel.sendIntent(CharacterDetailIntent.ShareCharacter) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.label_share),
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.sendIntent(CharacterDetailIntent.ToggleFavourite) },
                    ) {
                        Icon(
                            imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription =
                                stringResource(
                                    if (state.isFavourite) R.string.content_desc_unfavourite else R.string.content_desc_favourite,
                                ),
                            tint = if (state.isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            val character = state.character
            when {
                state.isLoading -> CharacterDetailShimmer()
                state.error != null ->
                    ErrorMessage(
                        message = state.error,
                        onRetry = { viewModel.sendIntent(CharacterDetailIntent.Retry) },
                    )
                character != null -> CharacterDetailContent(character = character)
            }
        }
    }
}

/**
 * Skeleton loader that mirrors the real detail layout so there's no jarring
 * layout shift when content arrives.
 */
@Composable
internal fun CharacterDetailShimmer() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("detail_shimmer"),
    ) {
        // Hero image placeholder
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shimmerEffect(),
        )
        // Metadata rows placeholder
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CONTENT_PADDING.dp, vertical = CONTENT_SPACING_VERTICAL.dp),
        ) {
            // Name shimmer
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(SHIMMER_NAME_WIDTH_FRACTION)
                        .height(SHIMMER_NAME_HEIGHT.dp)
                        .shimmerEffect(),
            )
            Spacer(modifier = Modifier.height(CONTENT_SPACING_VERTICAL_LARGE.dp))
            // Detail row shimmers
            repeat(SHIMMER_DETAIL_ROW_COUNT) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(SHIMMER_DETAIL_ROW_HEIGHT.dp)
                            .shimmerEffect(),
                )
                Spacer(modifier = Modifier.height(CONTENT_SPACING_VERTICAL_LARGE.dp))
            }
        }
    }
}

@Composable
internal fun CharacterDetailContent(character: CharacterDetailUi) {
    var imageLoaded by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        // Hero image
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            // Shimmer shown while image is in-flight
            if (!imageLoaded && !imageError) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .shimmerEffect(),
                )
            }

            // Actual image — placeholder_image shown by Coil while network is loading;
            // we track Success/Error to hide the shimmer and show the error overlay.
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(character.imageUrl)
                        .placeholder(R.drawable.placeholder_image)
                        .crossfade(IMAGE_CROSSFADE_MS)
                        .build(),
                contentDescription = character.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            imageLoaded = true
                            imageError = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            imageError = true
                            imageLoaded = false
                        }
                        else -> Unit
                    }
                },
            )

            // Error overlay — shown when Coil could not load the image
            if (imageError) {
                ImageErrorPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }

        // Content card slides up after image
        AnimatedVisibility(
            visible = contentVisible,
            enter =
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(durationMillis = ANIMATION_DURATION_MS),
                ) + fadeIn(animationSpec = tween(durationMillis = ANIMATION_DURATION_MS)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = CONTENT_PADDING.dp, vertical = CONTENT_SPACING_VERTICAL.dp),
            ) {
                // Name + status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = character.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    character.status?.let {
                        StatusChip(status = it)
                    }
                }

                Spacer(modifier = Modifier.height(CONTENT_SPACING_VERTICAL_LARGE.dp))

                character.species?.let { DetailRow(label = "Species", value = it) }
                character.gender?.let { DetailRow(label = "Gender", value = it) }
                character.origin?.let { DetailRow(label = "Origin", value = it) }
                character.location?.let { DetailRow(label = "Last known location", value = it) }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    val colors = status.toStatusColorSet()
    Row(
        modifier =
            modifier
                .background(color = colors.background, shape = CircleShape)
                .padding(
                    horizontal = STATUS_CHIP_HORIZONTAL_PADDING.dp,
                    vertical = STATUS_CHIP_VERTICAL_PADDING.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(STATUS_CHIP_DOT_SIZE.dp)
                    .clip(CircleShape)
                    .background(colors.dot),
        )
        Spacer(modifier = Modifier.width(STATUS_CHIP_SPACING.dp))
        Text(
            text = status,
            fontSize = STATUS_CHIP_FONT_SIZE.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.label,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = DETAIL_ROW_VERTICAL_PADDING.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = DETAIL_ROW_LABEL_FONT_SIZE.sp,
            )
            Text(
                text = value,
                fontSize = DETAIL_ROW_VALUE_FONT_SIZE.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
    }
}

private const val IMAGE_CROSSFADE_MS = 400
private const val ANIMATION_DURATION_MS = 400
private const val CONTENT_PADDING = 20
private const val CONTENT_SPACING_VERTICAL = 16
private const val CONTENT_SPACING_VERTICAL_LARGE = 20
private const val SHIMMER_NAME_WIDTH_FRACTION = 0.55f
private const val SHIMMER_NAME_HEIGHT = 24
private const val SHIMMER_DETAIL_ROW_COUNT = 4
private const val SHIMMER_DETAIL_ROW_HEIGHT = 14
private const val STATUS_CHIP_HORIZONTAL_PADDING = 10
private const val STATUS_CHIP_VERTICAL_PADDING = 5
private const val STATUS_CHIP_DOT_SIZE = 8
private const val STATUS_CHIP_SPACING = 5
private const val STATUS_CHIP_FONT_SIZE = 12
private const val DETAIL_ROW_VERTICAL_PADDING = 10
private const val DETAIL_ROW_LABEL_FONT_SIZE = 14
private const val DETAIL_ROW_VALUE_FONT_SIZE = 14
private const val DIVIDER_ALPHA = 0.5f

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "CharacterDetail – shimmer/loading", showBackground = true)
@Composable
private fun PreviewCharacterDetailShimmer() {
    CloseLoopWalletTheme(dynamicColor = false) {
        CharacterDetailShimmer()
    }
}

@Preview(name = "CharacterDetail – content", showBackground = true)
@Composable
private fun PreviewCharacterDetailContent() {
    CloseLoopWalletTheme(dynamicColor = false) {
        CharacterDetailContent(
            character =
                CharacterDetailUi(
                    id = 1,
                    name = "Rick Sanchez",
                    status = "Alive",
                    species = "Human",
                    gender = "Male",
                    origin = "Earth (C-137)",
                    location = "Citadel of Ricks",
                    imageUrl = null,
                ),
        )
    }
}

@Preview(name = "CharacterDetail – error", showBackground = true)
@Composable
private fun PreviewCharacterDetailError() {
    CloseLoopWalletTheme(dynamicColor = false) {
        ErrorMessage(
            message = "The server is having trouble right now. Please try again later.",
            onRetry = {},
        )
    }
}
