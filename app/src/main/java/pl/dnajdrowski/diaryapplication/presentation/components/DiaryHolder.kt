package pl.dnajdrowski.diaryapplication.presentation.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.realm.kotlin.ext.realmListOf
import pl.dnajdrowski.diaryapplication.model.Diary
import pl.dnajdrowski.diaryapplication.model.Mood
import pl.dnajdrowski.diaryapplication.ui.theme.Elevation
import pl.dnajdrowski.diaryapplication.util.fetchImagesFromFirebase
import pl.dnajdrowski.diaryapplication.util.toInstant
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun DiaryHolder(
    diary: Diary,
    onClick: (String) -> Unit
) {
    val localDensity = LocalDensity.current
    val context = LocalContext.current
    var componentHeight by remember { mutableStateOf(0.dp) }
    var galleryOpened by remember { mutableStateOf(false) }
    var galleryLoading by remember { mutableStateOf(false) }
    val downloadedImages = remember { mutableStateListOf<Uri>() }

    LaunchedEffect(key1 = galleryOpened) {
        if (galleryOpened && downloadedImages.isEmpty()) {
            galleryLoading = true
            fetchImagesFromFirebase(
                remoteImagePaths = diary.images,
                onImageDownload = { imageUri ->
                    downloadedImages.add(imageUri)
                },
                onImageDownloadFailed = {
                    Toast.makeText(
                        context,
                        "Images not uploaded yet." +
                                "Wait a minute, or try uploading again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    galleryLoading = false
                    galleryOpened = false
                },
                onReadyToDisplay = {
                    galleryLoading = false
                    galleryOpened = true
                }
            )
        }
    }

    Row(modifier = Modifier
        .clickable(
            indication = null,
            interactionSource = remember {
                MutableInteractionSource()
            }
        ) {
            onClick(diary._id.toHexString())
        }
    ) {
        Spacer(modifier = Modifier.width(14.dp))
        Surface(
            modifier = Modifier
                .width(2.dp)
                .height(componentHeight + 14.dp),
            tonalElevation = Elevation.Level1
        ) {}
        Spacer(modifier = Modifier.width(20.dp))
        Surface(
            modifier = Modifier
                .clip(shape = Shapes().medium)
                .onGloballyPositioned {
                    componentHeight = with(localDensity) { it.size.height.toDp() }
                },
            tonalElevation = Elevation.Level1
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DiaryHeader(moodName = diary.mood, time = diary.date.toInstant())
                Text(
                    modifier = Modifier
                        .padding(all = 14.dp),
                    text = diary.description,
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (diary.images.isNotEmpty()) {
                    ShowGalleryButton(
                        galleryOpened = galleryOpened,
                        galleryLoading = galleryLoading,
                        onClick = {
                            galleryOpened = !galleryOpened
                        }
                    )
                }
                AnimatedVisibility(
                    visible = galleryOpened && !galleryLoading,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(all = 14.dp)) {
                        Gallery(
                            images = downloadedImages
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryHeader(
    moodName: String,
    time: Instant
) {
    val mood by remember {
        mutableStateOf(Mood.valueOf(moodName))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(mood.containerColor)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = Modifier.size(18.dp),
                painter = painterResource(id = mood.icon),
                contentDescription = "Mood Icon"
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = mood.name,
                color = mood.contentColor,
                style = TextStyle(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            )
        }
        Text(
            text = SimpleDateFormat("hh:mm a", Locale.US)
                .format(Date.from(time)),
            color = mood.contentColor,
            style = TextStyle(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            )
        )
    }
}

@Composable
fun ShowGalleryButton(
    galleryOpened: Boolean,
    galleryLoading: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = if (galleryOpened)
                if (galleryLoading) "Loading" else "Hide Gallery"
                else "Show Gallery",
            style = TextStyle(
                fontSize = MaterialTheme.typography.bodySmall.fontSize
            )
        )
    }
}

@Composable
@Preview
fun DiaryHolderPreview() {
    DiaryHolder(diary = Diary().apply {
        title = "My Diary"
        description =
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged."
        mood = Mood.Happy.name
        images = realmListOf("", "")
    }, onClick = {})
}