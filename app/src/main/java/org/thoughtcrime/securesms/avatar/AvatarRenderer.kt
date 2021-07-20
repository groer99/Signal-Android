package org.thoughtcrime.securesms.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.content.res.AppCompatResources
import com.airbnb.lottie.SimpleColorFilter
import com.amulyakhare.textdrawable.TextDrawable
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.libsignal.util.guava.Optional
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.annotation.meta.Exhaustive

/**
 * Renders Avatar objects into Media objects. This can involve creating a Bitmap, depending on the
 * type of Avatar passed to `renderAvatar`
 */
object AvatarRenderer {

  private val DIMENSIONS = AvatarHelper.AVATAR_DIMENSIONS

  fun getTypeface(context: Context): Typeface {
    return Typeface.createFromAsset(context.assets, "fonts/Inter-Medium.otf")
  }

  fun renderAvatar(context: Context, avatar: Avatar, onAvatarRendered: (Media) -> Unit, onRenderFailed: (Throwable?) -> Unit) {
    @Exhaustive
    when (avatar) {
      is Avatar.Resource -> renderResource(context, avatar, onAvatarRendered, onRenderFailed)
      is Avatar.Vector -> renderVector(context, avatar, onAvatarRendered, onRenderFailed)
      is Avatar.Photo -> renderPhoto(context, avatar, onAvatarRendered)
      is Avatar.Text -> renderText(context, avatar, onAvatarRendered, onRenderFailed)
    }
  }

  @JvmStatic
  fun createTextDrawable(
    context: Context,
    avatar: Avatar.Text,
    inverted: Boolean = false,
    size: Int = DIMENSIONS,
    isRect: Boolean = true
  ): Drawable {
    val typeface = getTypeface(context)
    val color: Int = if (inverted) {
      avatar.color.backgroundColor
    } else {
      avatar.color.foregroundColor
    }

    val builder = TextDrawable
      .builder()
      .beginConfig()
      .fontSize(Avatars.getTextSizeForLength(context, avatar.text, size * 0.8f, size * 0.45f).toInt())
      .textColor(color)
      .useFont(typeface)
      .width(size)
      .height(size)
      .endConfig()

    return if (isRect) {
      builder.buildRect(avatar.text, Color.TRANSPARENT)
    } else {
      builder.buildRound(avatar.text, Color.TRANSPARENT)
    }
  }

  private fun renderVector(context: Context, avatar: Avatar.Vector, onAvatarRendered: (Media) -> Unit, onRenderFailed: (Throwable?) -> Unit) {
    renderInBackground(context, onAvatarRendered, onRenderFailed) { canvas ->
      val drawableResourceId = Avatars.getDrawableResource(avatar.key) ?: return@renderInBackground Result.failure(Exception("Drawable resource for key ${avatar.key} does not exist."))
      val vector: Drawable = requireNotNull(AppCompatResources.getDrawable(context, drawableResourceId))
      vector.setBounds(0, 0, DIMENSIONS, DIMENSIONS)

      canvas.drawColor(avatar.color.backgroundColor)
      vector.draw(canvas)
      Result.success(Unit)
    }
  }

  private fun renderText(context: Context, avatar: Avatar.Text, onAvatarRendered: (Media) -> Unit, onRenderFailed: (Throwable?) -> Unit) {
    renderInBackground(context, onAvatarRendered, onRenderFailed) { canvas ->
      val textDrawable = createTextDrawable(context, avatar)

      canvas.drawColor(avatar.color.backgroundColor)
      textDrawable.draw(canvas)
      Result.success(Unit)
    }
  }

  private fun renderPhoto(context: Context, avatar: Avatar.Photo, onAvatarRendered: (Media) -> Unit) {
    SignalExecutors.BOUNDED.execute {
      val blob = BlobProvider.getInstance()
        .forData(AvatarPickerStorage.read(context, PartAuthority.getAvatarPickerFilename(avatar.uri)), avatar.size)
        .createForSingleSessionOnDisk(context)

      onAvatarRendered(createMedia(blob, avatar.size))
    }
  }

  private fun renderResource(context: Context, avatar: Avatar.Resource, onAvatarRendered: (Media) -> Unit, onRenderFailed: (Throwable?) -> Unit) {
    renderInBackground(context, onAvatarRendered, onRenderFailed) { canvas ->
      val resource: Drawable = requireNotNull(AppCompatResources.getDrawable(context, avatar.resourceId))
      resource.colorFilter = SimpleColorFilter(avatar.color.foregroundColor)

      val padding = (DIMENSIONS * 0.2).toInt()
      resource.setBounds(0 + padding, 0 + padding, DIMENSIONS - padding, DIMENSIONS - padding)

      canvas.drawColor(avatar.color.backgroundColor)
      resource.draw(canvas)
      Result.success(Unit)
    }
  }

  private fun renderInBackground(context: Context, onAvatarRendered: (Media) -> Unit, onRenderFailed: (Throwable?) -> Unit, drawAvatar: (Canvas) -> Result<Unit>) {
    SignalExecutors.BOUNDED.execute {
      val canvasBitmap = Bitmap.createBitmap(DIMENSIONS, DIMENSIONS, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(canvasBitmap)

      val drawResult = drawAvatar(canvas)
      if (drawResult.isFailure) {
        canvasBitmap.recycle()
        onRenderFailed(drawResult.exceptionOrNull())
      }

      val outStream = ByteArrayOutputStream()
      val compressed = canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
      canvasBitmap.recycle()

      if (!compressed) {
        onRenderFailed(IOException("Failed to compress bitmap"))
        return@execute
      }

      val bytes = outStream.toByteArray()
      val inStream = ByteArrayInputStream(bytes)
      val uri = BlobProvider.getInstance().forData(inStream, bytes.size.toLong()).createForSingleSessionOnDisk(context)

      onAvatarRendered(createMedia(uri, bytes.size.toLong()))
    }
  }

  private fun createMedia(uri: Uri, size: Long): Media {
    return Media(uri, MediaUtil.IMAGE_JPEG, System.currentTimeMillis(), DIMENSIONS, DIMENSIONS, size, 0, false, false, Optional.absent(), Optional.absent(), Optional.absent())
  }
}
