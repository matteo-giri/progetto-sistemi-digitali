package com.example.sistemidigitali.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.Image
import android.util.Log


/** Utility class for manipulating images.  */
object ImageUtils {
    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    const val kMaxChannelValue = 262143


    private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
        // Adjust and check YUV values
        var y = y
        var u = u
        var v = v
        y = if (y - 16 < 0) 0 else y - 16
        u -= 128
        v -= 128

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        val y1192 = 1192 * y
        var r = y1192 + 1634 * v
        var g = y1192 - 833 * v - 400 * u
        var b = y1192 + 2066 * u

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r =
            if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
        g =
            if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
        b =
            if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b
        return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
    }

    fun convertYUV420ToARGB8888(
            yData: ByteArray,
            uData: ByteArray,
            vData: ByteArray,
            width: Int,
            height: Int,
            yRowStride: Int,
            uvRowStride: Int,
            uvPixelStride: Int,
            out: IntArray
    ) {
        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)
            for (i in 0 until width) {
                val uv_offset = pUV + (i shr 1) * uvPixelStride
                out[yp++] = YUV2RGB(
                        0xff and yData[pY + i].toInt(),
                        0xff and uData[uv_offset].toInt(),
                        0xff and vData[uv_offset].toInt()
                )
            }
        }
    }

    fun byteBufferToBitmap(imageArray: FloatArray, imageDim: Int) : Bitmap {
        val pixels = imageArray.map { it.toInt() }.toIntArray()
        val bitmap = Bitmap.createBitmap(imageDim, imageDim, Bitmap.Config.RGB_565);
        var uno = 0
        var due = 0
        var tre = 0
        var quattro = 0

        for ( i in 0 until imageDim ) {
            for ( j in 0 until imageDim ) {
                val p = pixels[i * imageDim + j]
                if (p >= 250)
                    uno++
                else if (p >= 230)
                    due++
                else if (p >= 210)
                    tre++
                else if (p <= 50)
                    quattro++

                bitmap.setPixel(j, i, Color.rgb(p, p, p))
            }
        }
        Log.e("e", "ABOVE 2: " + uno.toString())
        //Log.e("e","ABOVE 230: " + due.toString())
        //Log.e("e","ABOVE 210: " + tre.toString())
        //Log.e("e","ABOVE 190: " + quattro.toString())
        return bitmap
    }

     fun fillBytes(
             planes: Array<Image.Plane>,
             yuvBytes: Array<ByteArray?>
     ) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

    fun getTransformationMatrix(
            srcWidth: Int,
            srcHeight: Int,
            dstWidth: Int,
            dstHeight: Int,
            applyRotation: Int,
            maintainAspectRatio: Boolean): Matrix? {
        val matrix = Matrix()
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        val transpose = (Math.abs(applyRotation) + 90) % 180 == 0
        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()
            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                val scaleFactor = Math.max(scaleFactorX, scaleFactorY)
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY)
            }
        }
        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }
        return matrix
    }
}

