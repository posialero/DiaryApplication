package pl.dnajdrowski.diaryapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pl.dnajdrowski.diaryapplication.data.database.entity.ImageToUpload

@Dao
interface ImagesToUploadDao {

    @Query("SELECT * FROM images_to_upload_table ORDER BY id ASC")
    suspend fun getAllImages(): List<ImageToUpload>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImageToUpload(imageToUpload: ImageToUpload)

    @Query("DELETE FROM images_to_upload_table WHERE id=:imageId")
    suspend fun cleanupImage(imageId: Int)
}