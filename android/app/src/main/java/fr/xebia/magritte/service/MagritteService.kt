package fr.xebia.magritte.service

import fr.xebia.magritte.model.MagritteData
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.http.GET

interface MagritteService {

    @GET("data")
    fun getData(): Observable<MagritteData>

    @GET("model")
    fun getModelFile(): Observable<ResponseBody>
}
