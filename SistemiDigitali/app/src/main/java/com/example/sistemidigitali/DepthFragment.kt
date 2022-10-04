package com.example.sistemidigitali

import android.R
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment

class DepthFragment : Fragment(com.example.sistemidigitali.R.layout.depth_fragment) {

    private lateinit var mainActivity: MainActivity
    private lateinit var imageview: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
        imageview = view.findViewById<ImageView>(com.example.sistemidigitali.R.id.DepthImageView)
    }

    //cambia la bitmap corrispondente alla depth map
    fun changeImg(img: Bitmap){
        imageview.setImageBitmap(img)
    }

    companion object {
        @JvmStatic
        fun newInstance() =
                DepthFragment().apply {
                }
    }
}