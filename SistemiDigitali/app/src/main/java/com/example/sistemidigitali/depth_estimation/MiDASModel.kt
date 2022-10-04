/*
 * Copyright 2021 Shubham Panchal
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.sistemidigitali.depth_estimation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.sistemidigitali.utils.ImageUtils
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat

// Helper class per il modello di depth estimation MiDAS
class MiDASModel( context: Context , useGPU: Boolean = false) {

    private val modelFileName = "midas_depth.tflite"
    private var interpreter : Interpreter
    private val NUM_THREADS = 4

    private val inputImageDim = 256
    private val mean = floatArrayOf( 123.675f ,  116.28f ,  103.53f )
    private val std = floatArrayOf( 58.395f , 57.12f ,  57.375f )

    // Input tensor processor for MiDAS
    // 1. Resize the image to ( 256 , 256 )
    // 2. Normalize using the given mean and std for each channel.
    private val inputTensorProcessor = ImageProcessor.Builder()
            .add( ResizeOp( inputImageDim , inputImageDim , ResizeOp.ResizeMethod.BILINEAR ) )
            .add( NormalizeOp( mean , std ) )
            .build()

    // Output tensor processor for MiDAS
    // Perform min-max scaling for the outputs. See `MinMaxScalingOp` class.
    private val outputTensorProcessor = TensorProcessor.Builder()
            .add( MinMaxScalingOp() )
            .build()



    init {
        // Initialize TFLite Interpreter
        val interpreterOptions = Interpreter.Options().apply {
            // Add the GPU Delegate if supported.
            if ( useGPU ) {
                addDelegate( GpuDelegate( CompatibilityList().bestOptionsForThisDevice ))
            }
            else {
                // Number of threads for computation
                setNumThreads( NUM_THREADS )
            }
        }
        interpreter = Interpreter(FileUtil.loadMappedFile( context, modelFileName ) , interpreterOptions )
        Log.i( "MIDAS","TFLite interpreter created." )
    }


    fun getDepthMap( inputImage : Bitmap ) : Bitmap {
        var inputTensor = TensorImage.fromBitmap( inputImage )

        val t1 = System.currentTimeMillis()
        inputTensor = inputTensorProcessor.process( inputTensor )

        // Output tensor of shape ( 256 , 256 , 1 ) and data type float32
        var outputTensor = TensorBufferFloat.createFixedSize(
                intArrayOf( inputImageDim , inputImageDim , 1 ) , DataType.FLOAT32 )

        // Perform inference on the MiDAS model
        interpreter.run( inputTensor.buffer, outputTensor.buffer )

        // Perform operations on the output tensor as described by `outputTensorProcessor`.
        outputTensor = outputTensorProcessor.process( outputTensor )

        Log.i("MIDAS", "MiDaS inference speed: ${System.currentTimeMillis() - t1}")

        // Create a Bitmap from the depth map which will be displayed on the screen.
        return ImageUtils.byteBufferToBitmap( outputTensor.floatArray , inputImageDim )
    }

    // Post processing operation for MiDAS
    // Apply min-max scaling to the outputs of the model and bring them in the range [ 0 , 255 ].
    // Also, we apply a transformation which changes the data type from `int` to `uint` in Python.
    // As unsigned integers aren't supported in Java, we add 255 + pixel if pixel < 0
    class MinMaxScalingOp : TensorOperator {
        override fun apply( input : TensorBuffer?): TensorBuffer {
            val values = input!!.floatArray
            // Compute min and max of the output
            val max = values.maxOrNull()!!
            val min = values.minOrNull()!!
            for ( i in values.indices ) {
                // Normalize the values and scale them by a factor of 255
                var p = ((( values[ i ] - min ) / ( max - min )) * 255).toInt()
                if ( p < 0 ) {
                    p += 255
                }
                values[ i ] = p.toFloat()
            }
            // Convert the normalized values to the TensorBuffer and load the values in it.
            val output = TensorBufferFloat.createFixedSize( input.shape , DataType.FLOAT32 )
            output.loadArray( values )
            return output
        }
    }
}