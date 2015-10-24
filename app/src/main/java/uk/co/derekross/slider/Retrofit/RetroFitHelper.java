package uk.co.derekross.slider.Retrofit;

import retrofit.Call;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;

/**
 * Created by derek on 19/10/15.
 */
public interface RetroFitHelper {
    public String ImgurEndPoint = "https://api.imgur.com";

        @Headers("Authorization: Client-ID xxxxxxxxxxxxx" )
        @GET("/3/gallery/r/{subReddit}/{page}")
        Call<Model> getSubReditData(@Path("subReddit") String subReddit, @Path("page") int page);


}
