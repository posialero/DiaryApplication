package pl.dnajdrowski.diaryapplication.presentation.screens.write

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import pl.dnajdrowski.diaryapplication.data.database.ImageToDeleteDao
import pl.dnajdrowski.diaryapplication.data.database.ImageToUploadDao
import pl.dnajdrowski.diaryapplication.data.database.entity.ImageToDelete
import pl.dnajdrowski.diaryapplication.data.database.entity.ImageToUpload
import pl.dnajdrowski.diaryapplication.data.repository.MongoDB
import pl.dnajdrowski.diaryapplication.model.Diary
import pl.dnajdrowski.diaryapplication.model.GalleryImage
import pl.dnajdrowski.diaryapplication.model.GalleryState
import pl.dnajdrowski.diaryapplication.model.Mood
import pl.dnajdrowski.diaryapplication.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import pl.dnajdrowski.diaryapplication.model.RequestState
import pl.dnajdrowski.diaryapplication.util.fetchImagesFromFirebase
import pl.dnajdrowski.diaryapplication.util.toRealmInstant
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imagesToUploadDao: ImageToUploadDao,
    private val imageToDeleteDao: ImageToDeleteDao
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    val galleryState = GalleryState()

    init {
        getDiaryIdArgument()
        fetchSelectedDiary()
    }

    private fun getDiaryIdArgument() {
        uiState = uiState.copy(
            selectedDiaryId = savedStateHandle.get<String>(
                key = WRITE_SCREEN_ARGUMENT_KEY
            )
        )
    }

    private fun fetchSelectedDiary() {
        if (uiState.selectedDiaryId != null) {
            viewModelScope.launch {
                MongoDB.getSelectedDiary(diaryId = ObjectId.invoke(uiState.selectedDiaryId!!))
                    .catch {
                        emit(RequestState.Error(Exception("Diary is already delete.")))
                    }
                    .collect { diary ->
                        if (diary is RequestState.Success) {
                            setSelectedDiary(diary = diary.data)
                            setTitle(title = diary.data.title)
                            setDescription(description = diary.data.description)
                            setMood(mood = Mood.valueOf(diary.data.mood))
                            fetchImagesFromFirebase(
                                remoteImagePaths = diary.data.images,
                                onImageDownload = { downloadedImage ->
                                    galleryState.addImage(
                                        GalleryImage(
                                            image = downloadedImage,
                                            remoteImagePath = extractRemoteImagePath(
                                                fullImageUrl = downloadedImage.toString()
                                            )
                                        )
                                    )
                                }
                            )
                        } else {
                            Log.d("DanielTest", diary.toString())
                        }
                    }
            }
        }
    }

    private fun extractRemoteImagePath(fullImageUrl: String): String {
        val chunks = fullImageUrl.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${FirebaseAuth.getInstance().currentUser?.uid}/$imageName"

    }

    private fun insertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MongoDB.insertDiary(diary = diary.apply {
                if (uiState.updatedDateTime != null) {
                    date = uiState.updatedDateTime!!
                }
            })
            if (result is RequestState.Success) {
                uploadImagesToFirebase()
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } else if (result is RequestState.Error) {
                withContext(Dispatchers.Main) {
                    onError(result.error.message.toString())
                }
            }
        }
    }

    private fun updateDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = MongoDB.updateDiary(
                diary = diary.apply {
                    _id = ObjectId.invoke(uiState.selectedDiaryId!!)
                    date = if (uiState.updatedDateTime != null) {
                        uiState.updatedDateTime!!
                    } else {
                        uiState.selectedDiary!!.date
                    }
                }
            )
            if (result is RequestState.Success) {
                uploadImagesToFirebase()
                deleteImagesFromFirebase()
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } else if (result is RequestState.Error) {
                withContext(Dispatchers.Main) {
                    onError(result.error.message.toString())
                }
            }
        }
    }

    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (uiState.selectedDiaryId != null) {
            updateDiary(diary = diary, onSuccess = onSuccess, onError = onError)
        } else {
            insertDiary(diary = diary, onSuccess = onSuccess, onError = onError)
        }

    }

    fun deleteDiary(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedDiaryId != null) {
                val result =
                    MongoDB.deleteDiary(diaryId = ObjectId.invoke(uiState.selectedDiaryId!!))
                if (result is RequestState.Success) {
                    withContext(Dispatchers.Main) {
                        uiState.selectedDiary?.let { deleteImagesFromFirebase(images = it.images) }
                        onSuccess()
                    }
                } else if (result is RequestState.Error) {
                    onError(result.error.message.toString())
                }
            }
        }
    }

    private fun setSelectedDiary(diary: Diary) {
        uiState = uiState.copy(
            selectedDiary = diary
        )
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(
            title = title
        )
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(
            description = description
        )
    }

    private fun setMood(mood: Mood) {
        uiState = uiState.copy(
            mood = mood
        )
    }

    fun updateDateTime(zonedDateTime: ZonedDateTime?) {
        uiState = uiState.copy(
            updatedDateTime = zonedDateTime?.toInstant()?.toRealmInstant()
        )
    }

    fun addImage(image: Uri, imageType: String) {
        val remoteImagePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"
        Log.d("WriteViewModel", remoteImagePath)
        galleryState.addImage(
            GalleryImage(
                image = image,
                remoteImagePath = remoteImagePath
            )
        )
    }

    private fun uploadImagesToFirebase() {
        val storage = FirebaseStorage.getInstance().reference
        galleryState.images.forEach {  galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)
            imagePath.putFile(galleryImage.image)
                .addOnProgressListener {
                    val sessionUri = it.uploadSessionUri
                    if (sessionUri != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            imagesToUploadDao.addImageToUpload(
                                ImageToUpload(
                                    remoteImagePath = galleryImage.remoteImagePath,
                                    imageUri = galleryImage.image.toString(),
                                    sessionUri = sessionUri.toString()
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null) {
        val storage = FirebaseStorage.getInstance().reference
        if (images != null) {
            images.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(
                                    remoteImagePath = remotePath
                                )
                            )
                        }
                    }
            }
        } else {
            galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(
                                    remoteImagePath = remotePath
                                )
                            )
                        }
                    }
            }
        }
    }
}

data class UiState(
    val selectedDiaryId: String? = null,
    val selectedDiary: Diary? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: RealmInstant? = null
)