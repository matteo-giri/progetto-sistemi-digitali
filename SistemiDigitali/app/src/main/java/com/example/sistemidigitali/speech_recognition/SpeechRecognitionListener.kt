package com.example.sistemidigitali.speech_recognition

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.sistemidigitali.MainActivity

/*
* Listener per gestire la speech recognition
*/
class SpeechRecognitionListener constructor(mainA: MainActivity) : RecognitionListener{

    private val main = mainA
    override fun onReadyForSpeech(bundle: Bundle) {}
    override fun onBeginningOfSpeech() {
        Log.i("speech recogonizer", "beginning to record")
    }

    override fun onRmsChanged(v: Float) {}
    override fun onBufferReceived(bytes: ByteArray) {}
    override fun onEndOfSpeech() {}
    override fun onError(i: Int) {}
    override fun onResults(bundle: Bundle) {
        val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (data != null) {
            Log.i("speech recogonizer", data.toString())
        }
        data?.get(0)?.let { main.executeCommand(it.toString()) }
    }

    override fun onPartialResults(bundle: Bundle) {}
    override fun onEvent(i: Int, bundle: Bundle) {}
}