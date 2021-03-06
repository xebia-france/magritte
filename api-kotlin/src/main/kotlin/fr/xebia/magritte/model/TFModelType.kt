package fr.xebia.magritte.model

import com.google.gson.annotations.SerializedName

enum class TFModelType(val typeName: String) {
    @SerializedName("tfmobile")
    TF_MOBILE("tfmobile"),

    @SerializedName("tflite")
    TF_LITE("tflite")
}