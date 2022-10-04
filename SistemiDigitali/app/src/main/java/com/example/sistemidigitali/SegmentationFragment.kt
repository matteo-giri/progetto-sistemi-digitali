package com.example.sistemidigitali

import android.graphics.Bitmap
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment

class SegmentationFragment : Fragment(R.layout.segmentation_fragment) {
    private lateinit var mainActivity: MainActivity
    private lateinit var imageview: ImageView
    private lateinit var textView: TextView
    private lateinit var chipsLabels : Map<String, Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chipsLabels = hashMapOf<String, Int>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
        imageview = view.findViewById<ImageView>(R.id.SegmentImageView)
        textView = view.findViewById<TextView>(R.id.textView)
    }

    //cambia l'immagine corrispondente alla scene segmentation con le relative labels
    fun changeImg(img: Bitmap, itemsFound: Map<String, Int>){
        imageview.setImageBitmap(img)
        if (!itemsFound.equals(chipsLabels)) {
            chipsLabels = itemsFound
            setChipsToLogView(itemsFound)
        }
    }

    //crea del testo colorato per ogni label trovata
    private fun setChipsToLogView(itemsFound: Map<String, Int>) {
        var text = ""
        for ((label, color) in itemsFound) {
            text +=
                "<font color=" + color+ ">" + "   " +  label + "   " +  "</font>"
        }
        textView.setText(Html.fromHtml(text,0))

    }

    companion object {
        @JvmStatic
        fun newInstance() =
            SegmentationFragment().apply {
            }
    }
}