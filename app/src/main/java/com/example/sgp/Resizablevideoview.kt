package com.example.sgp

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.VideoView

/**
 * A VideoView that fills its container completely (center-crop) instead of
 * the stock VideoView, which letterboxes/pillarboxes to the video's native
 * aspect ratio and leaves black bars around it.
 *
 * Call setVideoSize() once the real video dimensions are known — normally
 * from MediaPlayer.OnPreparedListener (mp.videoWidth / mp.videoHeight).
 */
class ResizableVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : VideoView(context, attrs) {

    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = View.MeasureSpec.getSize(heightMeasureSpec)

        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()

        val measuredWidth: Int
        val measuredHeight: Int
        if (videoRatio > viewRatio) {
            // Video is relatively wider than the container: match the
            // container's height, let width overflow — the parent
            // (FrameLayout, clipChildren=true by default) crops the excess,
            // same visual result as ImageView.ScaleType.CENTER_CROP.
            measuredHeight = viewHeight
            measuredWidth = (viewHeight * videoRatio).toInt()
        } else {
            measuredWidth = viewWidth
            measuredHeight = (viewWidth / videoRatio).toInt()
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}