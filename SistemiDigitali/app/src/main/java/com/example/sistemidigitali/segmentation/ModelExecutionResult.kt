/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.sistemidigitali.segmentation

import android.graphics.Bitmap

//classe che modella il risultato della scene segmentation
data class ModelExecutionResult(
  val bitmapResult: Bitmap, //sovrapposizione tra immagine originale e maschere della scena
  val bitmapOriginal: Bitmap, //immagine originale
  val bitmapMaskOnly: Bitmap, //maschere della scena
  val executionLog: String,
  // A map between labels and colors of the items found.
  val itemsFound: Map<String, Int> //oggetti riconosciuti
)
