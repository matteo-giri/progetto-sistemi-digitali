 package com.example.sistemidigitali

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Size
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.utils.widget.MockView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.sistemidigitali.depth_estimation.MiDASModel
import com.example.sistemidigitali.object_detection.Detector
import com.example.sistemidigitali.object_detection.MultiBoxTracker
import com.example.sistemidigitali.object_detection.OverlayView
import com.example.sistemidigitali.object_detection.OverlayView.DrawCallback
import com.example.sistemidigitali.object_detection.TFLiteObjectDetectionAPIModel
import com.example.sistemidigitali.segmentation.ImageSegmentationModelExecutor
import com.example.sistemidigitali.segmentation.ModelExecutionResult
import com.example.sistemidigitali.speech_recognition.SpeechRecognitionListener
import com.example.sistemidigitali.utils.ImageUtils
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.switchmaterial.SwitchMaterial
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity(), ImageReader.OnImageAvailableListener {


    // valori per object detection
    private val TF_OD_API_INPUT_SIZE = 300
    private val TF_OD_API_IS_QUANTIZED = true
    private val TF_OD_API_MODEL_FILE = "detection_model.tflite"
    private val TF_OD_API_LABELS_FILE = "labelmap.txt"
    private val MINIMUM_CONFIDENCE_TF_OD_API = 0.6f // minima confidenza per far si che un oggetto venga riquadrato
    private var MINIMUM_DISTANCE_TRIGGER = 0.3f //distanza affinchè il suono si attivi

    //variabili per object detection
    private lateinit var objectDetectorModel: Detector
    private lateinit var tracker: MultiBoxTracker
    private lateinit var trackingOverlay: OverlayView
    private lateinit var odSwitch: SwitchMaterial //switch che regola la visualizzazione dell'object detection

    //variabili per il suono
    private var soundTrigger = false //è vera se l'oggetto è abbastanza vicino alla fotocamera da far attivare l'allarme
    private var soundVolume = true //è vera se il suono dell'allarme non è mutato
    private var isSoundPlaying = false //è vera se l'allarme è già attivo
    private lateinit var soundJob: Job //job nel quale viene gestito il suono dell'allarme
    private lateinit var alarmSound: MediaPlayer //suono dell'allarme
    private var frequence: Frequence = Frequence.LOW //frequenza con cui il suono viene attivato (più l'oggetto è vicino alla camera più la frequenza è elevata)
    private lateinit var soundIn: MediaPlayer //suono di inizio speech recognition
    private lateinit var soundOut: MediaPlayer //suono di fine speech recognition
    private var volume = 0.1f

    //variabili per la speech recognition
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var pressableView: MockView
    private lateinit var hiddenView: ConstraintLayout
    private lateinit var blurView: BlurView


    var previewHeight = 0
    var previewWidth = 0
    var sensorOrientation = 0

    private lateinit var frameText: TextView //testo per gli fps

    private lateinit var imageSegmentationModel: ImageSegmentationModelExecutor //modello che si occupa della segmentazione della scena
    private lateinit var depthEstimationModel : MiDASModel //modello che si occupa della depth estimation

    //fragments
    private val depth_fragment = DepthFragment.newInstance()
    private val segmentation_fragment = SegmentationFragment.newInstance()
    private val camera_fragment = CameraConnectionFragment.newInstance(
            object :
                    CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    sensorOrientation = cameraRotation - getScreenOrientation()
                }
            },
            this,
            R.layout.camera_fragment,
            Size(640, 480)
    )


    private lateinit var currentFragment: Fragment //variabile che indica il fragment in cui si è al momento
    private lateinit var latest_image_bitmap : Bitmap //bitmap dell'ultima immagine acquisita dalla fotocamera

    //variabili per il processamento delle immagini della fotocamera
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null

    private var isProcessingFrame = false //flag per il processamento delle immagini della fotocamera
    private var isDepthProcessing = false //flag per il processamento delle depth maps
    private var isSegmentProcessing = false //flag per il processamento della scene segmentation
    private var isDetectionProcessing = false //flag per il processamento della object detection

    enum class Frequence {
        HIGH, MEDIUM, LOW
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmSound = MediaPlayer.create(this@MainActivity, R.raw.alarmsound)
        alarmSound.setVolume(volume, volume)
        soundIn = MediaPlayer.create(this@MainActivity, R.raw.soundin)
        soundIn.setVolume(0.1f, 0.1f)
        soundOut = MediaPlayer.create(this@MainActivity, R.raw.soundout)
        soundOut.setVolume(0.1f, 0.1f)

        imageSegmentationModel = ImageSegmentationModelExecutor(this, false)
        depthEstimationModel = MiDASModel(this, true)
        objectDetectorModel = TFLiteObjectDetectionAPIModel.create(this, TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE, TF_OD_API_IS_QUANTIZED)
        tracker = MultiBoxTracker(this)
        tracker.setFrameConfiguration(480, 640, sensorOrientation)

        frameText = findViewById(R.id.frameText)
        hiddenView = findViewById(R.id.hiddenLayout)
        blurView = findViewById<BlurView>(R.id.blurView)
        blurBackground()
        pressableView = findViewById(R.id.pressableView)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")

        speechRecognizer.setRecognitionListener(SpeechRecognitionListener(this))

        val handler = Handler(Looper.getMainLooper()) //handler per realizzare la gesture del long press
        var longPress = false
        val runnable = Runnable { // runnable eseguito quando si fa un long press sullo schermo
            longPress = true
            Log.i("speech recogonizer", "I'M PRESSING")
            speechRecognizer.startListening(speechRecognizerIntent)
            soundIn.start()
            val anim = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 1000
            hiddenView.setVisibility(View.VISIBLE)
            hiddenView.setAlpha(1F)
            hiddenView.startAnimation(anim)
        }
        pressableView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View?, motionEvent: MotionEvent): Boolean {
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    handler.removeCallbacks(runnable)
                    if (longPress) {
                        longPress = false
                        Log.i("speech recogonizer", "I RELEASED")
                        speechRecognizer.stopListening()
                        soundOut.start()
                        val anim = AlphaAnimation(hiddenView.alpha, 0.0f)
                        anim.duration = 300
                        hiddenView.startAnimation(anim)
                        hiddenView.setVisibility(View.GONE)
                    }
                    return false
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    //esegue il runnable solo dopo un secondo, in questo modo si può realizzare un long touch
                    handler.postDelayed(runnable, 1000);
                    return true
                }
                return false
            }
        })


        //chiede il permesso per l'utilizzo della camera e del microfono
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_DENIED) {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            requestPermissions(permissions, 1122)
        } else {
            //mostra l'applicazione
            setFragment()
        }

        findViewById<NavigationBarView>(R.id.bottom_navigation).setOnItemSelectedListener { item ->
            when(item.itemId){
                R.id.page_1 -> {
                    replaceFragment(camera_fragment)
                    true
                }
                R.id.page_2 -> {
                    replaceFragment(depth_fragment)
                    true
                }
                R.id.page_3 -> {
                    replaceFragment(segmentation_fragment)
                    true
                }
                else -> false
            }
        }

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        soundTrigger = false
        isSoundPlaying = false

        trackingOverlay = findViewById(R.id.tracking_overlay) as OverlayView
        odSwitch = findViewById(R.id.ODSwitch) as SwitchMaterial
        odSwitch.isChecked = false
        trackingOverlay.addCallback(
                object : DrawCallback {
                    override fun drawCallback(canvas: Canvas?) {
                        tracker.draw(canvas)
                    }
                })
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        soundTrigger = false
        isSoundPlaying = false
    }

    //funzione chiamata dopo la richiesta dei permessi
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //se il permesso è stato garantito mostra l'applicazione
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            setFragment()
        } else {
            finish()
        }
    }

    //funzione che fa la transizione tra fragments
    private fun replaceFragment(fragment: Fragment){
        val transaction = supportFragmentManager.beginTransaction()
        when(fragment){
            camera_fragment -> {
                currentFragment = camera_fragment
                transaction.show(camera_fragment)
                transaction.hide(depth_fragment)
                transaction.hide(segmentation_fragment)
            }
            depth_fragment -> {
                currentFragment = depth_fragment
                transaction.show(depth_fragment)
                transaction.hide(camera_fragment)
                transaction.hide(segmentation_fragment)
            }
            segmentation_fragment -> {
                currentFragment = segmentation_fragment
                transaction.show(segmentation_fragment)
                transaction.hide(depth_fragment)
                transaction.hide(camera_fragment)
            }
        }
        transaction.commit()
    }

    //funzione che inizializza i fragments
    private fun addFragments(){
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.container, camera_fragment)
        transaction.add(R.id.container, depth_fragment)
        transaction.add(R.id.container, segmentation_fragment)
        transaction.commit()
    }


    //funzione che setta il fragment della fotocamera come inziale
    protected fun setFragment() {
        val manager =
                getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        camera_fragment.setCamera(cameraId)
        addFragments()
        replaceFragment(camera_fragment)
    }

    //funzione che ritorna la rotazione dello schermo
    @Suppress("DEPRECATION")
    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    //callback richiamata ogni volta che un'immagine viene acquisita falla fotocamera
    override fun onImageAvailable(reader: ImageReader) {
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) { //se un'altra immagina sta già venendo processata l'ultima immagine viene scartata
                image.close()
                return
            }
            isProcessingFrame = true
            val planes = image.planes
            ImageUtils.fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0]!!,
                        yuvBytes[1]!!,
                        yuvBytes[2]!!,
                        previewWidth,
                        previewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
            processForFragment()
        } catch (e: Exception) {
            Log.e("processing error", e.stackTrace.toString())
            return
        }

        checkSoundTrigger()
    }

    //crea la bitmap dell'ultima immagine aquisita con il converter e i valori inizializzati in onImageAvailable
    private fun processImage() {
        imageConverter!!.run()
        latest_image_bitmap = Bitmap.createBitmap(
                previewWidth,
                previewHeight,
                Bitmap.Config.ARGB_8888
        )
        latest_image_bitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        latest_image_bitmap = rotateBitmap(latest_image_bitmap)
        postInferenceCallback!!.run()
    }

    //processa la latest image bitmap in base al fragment in cui ci troviamo
    private fun processForFragment(){
        // se siamo nel framgent della segmentation utilizza la latest image bitmap per effettuare la scene segmentation
        if (currentFragment == segmentation_fragment) {
            if (isSegmentProcessing == false) {
                CoroutineScope(Dispatchers.Main).launch {
                    val startTime = SystemClock.elapsedRealtime()
                    val segmentedFrame = semanticSegmentation(latest_image_bitmap)
                    segmentation_fragment.changeImg(segmentedFrame.first, segmentedFrame.second)
                    val endTime = SystemClock.elapsedRealtime()
                    val time = endTime-startTime
                    val fps = (1000.0 / time).roundToInt()
                    frameText.setText("FPS: " + fps.toString())
                }
            }
        }
        //se siamo nel fragment della depth estimation utilizza la latest image bitmap per effettuare la depth estimation
        else if (currentFragment == depth_fragment) {
            if (isDepthProcessing == false) {
                CoroutineScope(Dispatchers.Main).launch {
                    val startTime = SystemClock.elapsedRealtime()
                    val depthFrame = depthEstimation(latest_image_bitmap)
                    depth_fragment.changeImg(depthFrame)
                    val endTime = SystemClock.elapsedRealtime()
                    val time = endTime-startTime
                    val fps = (1000.0 / time).roundToInt()
                    frameText.setText("FPS: " + fps.toString())
                }
            }
        }
        //se siamo nel fragment della fotocamera utilizza la latest image bitmap per effettuare l'object detection
        else if (currentFragment == camera_fragment) {
            if (isDetectionProcessing == false) {
                CoroutineScope(Dispatchers.Main).launch {
                    val startTime = SystemClock.elapsedRealtime()
                    objectDetection(latest_image_bitmap)
                    val endTime = SystemClock.elapsedRealtime()
                    val time = endTime-startTime
                    val fps = (1000.0 / time).roundToInt()
                    frameText.setText("FPS: " + fps.toString())
                }
            }
        }
    }

    //funzione che serve per ruotare la bitmap nel caso in cui sia ruotata in modo sbagliato
    fun rotateBitmap(input: Bitmap): Bitmap {
        Log.d("trySensor", sensorOrientation.toString() + "     " + getScreenOrientation())
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(sensorOrientation.toFloat())
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, rotationMatrix, true)
    }

    //funzione che crea semantic segmentation del frame passato in input
    suspend fun semanticSegmentation(bitmap: Bitmap):Pair<Bitmap, Map<String, Int>>{
        lateinit var result: ModelExecutionResult
        withContext(Dispatchers.Main) {
            isSegmentProcessing = true
            result = imageSegmentationModel.execute(bitmap)
            isSegmentProcessing = false
        }
        return Pair(result.bitmapResult, result.itemsFound)
    }

    //funzione che fa la depth estimation del frame passato in input
    suspend fun depthEstimation(bitmap: Bitmap):Bitmap{
        lateinit var scaled_depthMap : Bitmap
        withContext(Dispatchers.Main) {
            isDepthProcessing = true
            val depthMap = depthEstimationModel.getDepthMap(bitmap)
            scaled_depthMap = Bitmap.createScaledBitmap(depthMap, 480, 640, false)
            isDepthProcessing = false
        }
        return scaled_depthMap
    }

    //funzione che esegue la object detection del frame passato in input
    suspend fun objectDetection(bitmap: Bitmap){
        isDetectionProcessing = true
        trackingOverlay.postInvalidate()
        withContext(Dispatchers.Main) {
                    val results: List<Detector.Recognition> = objectDetectorModel.recognizeImage(bitmap)
                    var minimumConfidence: Float = MINIMUM_CONFIDENCE_TF_OD_API
                    val mappedRecognitions: MutableList<Detector.Recognition> = ArrayList<Detector.Recognition>()
                    val distances = ArrayList<Float>()
                    for (result in results) {
                        if (result.getConfidence() >= minimumConfidence) {
                            mappedRecognitions.add(result)
                            val distance = calculateDistance(result.location)
                            distances.add(distance)
                        }
                    }

                    val minDistance = distances.minOrNull()
                    soundTrigger = !(minDistance == null || minDistance > MINIMUM_DISTANCE_TRIGGER)

                    //dà un valore alla frequenza del suono in base alla distanza minore
                    if (minDistance != null && minDistance <= MINIMUM_DISTANCE_TRIGGER/3)
                        frequence = Frequence.HIGH
                    else if (minDistance != null && minDistance <= (MINIMUM_DISTANCE_TRIGGER*2)/3)
                        frequence = Frequence.MEDIUM
                    else
                        frequence = Frequence.LOW

                    //se lo switch della object detection è attivo il tracker processa i risultati e li mostra a schermo
                    if (odSwitch.isChecked) {
                        tracker.processResults(mappedRecognitions)
                        trackingOverlay.postInvalidate()
                    }
                    else{
                        val dummyList: MutableList<Detector.Recognition> = ArrayList<Detector.Recognition>()
                        tracker.processResults(dummyList)
                        trackingOverlay.postInvalidate()
                    }
                    isDetectionProcessing = false
                }
    }

    //funzione che calcola un'approssimazione della distanza di un oggetto dalla fotocamera usando la box della object detection
    private fun calculateDistance(location: RectF): Float{
        val maxArea = 640 * 480
        val area = location.width() * location.height()
        val ratio: Float = if (area/maxArea <= 1) area/maxArea else 1F
        val distance = 1F - ratio
        return distance
    }

    //funzione che attiva la coroutine del suono nel caso in cui soundTrigger sia true e la routine non sia già attiva
    @ObsoleteCoroutinesApi
    private fun checkSoundTrigger(){
        if (soundTrigger && !isSoundPlaying){
            soundJob = CoroutineScope(newSingleThreadContext("SoundThread")).launch{ playSound()}
        }
    }

    //coroutine che attiva il suono in base alla frequenza assegnata
    private suspend fun playSound(){
        isSoundPlaying = true
        while(soundTrigger){
            if (soundVolume) {
                when (frequence) {
                    Frequence.HIGH -> {
                        alarmSound.playbackParams = alarmSound.playbackParams.setSpeed(2.0f)
                        alarmSound.start()
                        delay(50)
                    }
                    Frequence.MEDIUM -> {
                        alarmSound.playbackParams = alarmSound.playbackParams.setSpeed(1.5f)
                        alarmSound.start()
                        delay(500)
                    }
                    Frequence.LOW -> {
                        alarmSound.playbackParams = alarmSound.playbackParams.setSpeed(1.0f)
                        alarmSound.start()
                        delay(1000)
                    }
                }
            }
        }
        isSoundPlaying = false
        Log.i("speech recognizer", "Sound Coroutine canceled")
    }

    //funzione che crea un effetto blur sul background quando uso i messaggi vocali
    fun blurBackground(){
        val radius = 5f
        val decorView = window.decorView
        val rootView = decorView.findViewById<View>(android.R.id.content) as ViewGroup
        val windowBackground = decorView.background
        blurView.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(radius)
            .setBlurAutoUpdate(true)
            .setHasFixedTransformationMatrix(true)

    }

    //funzione che esegue il comando vocale ricevuto in input
    fun executeCommand(data: String){
        if (data.contains("disattiva allarme", ignoreCase = true)){
            soundVolume = false
            Log.i("speech recognizer", "ALLARME DISATTIVATO CON COMANDO VOCALE")
        }
        else if (data.contains("attiva allarme", ignoreCase = true)){
            soundVolume = true
            Log.i("speech recognizer", "ALLARME ATTIVATO CON COMANDO VOCALE")
        }
        else if (data.contains("aumenta volume", ignoreCase = true)){
            volume += 0.1f
            alarmSound.setVolume(volume, volume)
            Log.i("speech recognizer", "VOLUME AUMENTATO CON COMANDO VOCALE: " + volume.toString())
        }
        else if (data.contains("diminuisci volume", ignoreCase = true)){
            volume -= 0.1f
            alarmSound.setVolume(volume, volume)
            Log.i("speech recognizer", "VOLUME DIMINUITO CON COMANDO VOCALE:" + volume.toString())
        }
        else if (data.contains("aumenta distanza", ignoreCase = true)){
            MINIMUM_DISTANCE_TRIGGER += 0.05f
            Log.i("speech recognizer", "DISTANZA DI TRIGGERING AUMENTATA CON COMANDO VOCALE:" + MINIMUM_DISTANCE_TRIGGER.toString())
        }
        else if (data.contains("diminuisci distanza", ignoreCase = true)){
            MINIMUM_DISTANCE_TRIGGER -= 0.05f
            Log.i("speech recognizer", "DISTANZA DI TRIGGERING DIMINUITA CON COMANDO VOCALE: " + MINIMUM_DISTANCE_TRIGGER.toString())
        }
    }
}