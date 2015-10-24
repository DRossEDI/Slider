package uk.co.derekross.slider;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;
import uk.co.derekross.slider.Retrofit.ImageData;
import uk.co.derekross.slider.Retrofit.Model;
import uk.co.derekross.slider.Retrofit.RetroFitHelper;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private ImageView mIVMain;
    private ArrayList<Bitmap> mBitMaps = new ArrayList<Bitmap>();
    private ReentrantReadWriteLock mRWbitmapsLock = new ReentrantReadWriteLock(false);
    private ReentrantReadWriteLock mRWidsLock = new ReentrantReadWriteLock(false);
    private ArrayList<String> mIds = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });


        mIVMain = (ImageView) findViewById(R.id.IVMain);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(new RetrieveDataFromImgur("gonewild", 5)).run();

    }

    /*Method to add a bitmap to the arraylist. this is done within a write lock
        * to ensure it remains in sync*/
    private void addToBitmaps(Bitmap bitmap){
        mRWbitmapsLock.writeLock().lock();
        mBitMaps.add(bitmap);
        mRWbitmapsLock.writeLock().unlock();
    }

    /*Method to add a string id to the arraylist of Id's. done with a
    * write lock to remain in sync*/
    private void addToIds(String id){
        mRWidsLock.writeLock().lock();
        mIds.add(id);
        mRWidsLock.writeLock().unlock();

    }

    /*Method to remove the first bitmap and return it. this is controlled by a
    * write lock to avoid the arraylist coming out of sync*/
    private Bitmap getNextBitmap() {

        mRWbitmapsLock.writeLock().lock();
        Bitmap b = mBitMaps.remove(0);

        mRWbitmapsLock.writeLock().unlock();

        return b;
    }

    /*Method to remove the first id from the bitmap and return it. Done within
    * a write lock to keep the arraylist in sync*/
    private String getNextId(){
        mRWidsLock.writeLock().lock();
        String s = mIds.remove(0);
        mRWidsLock.writeLock().unlock();
        return s;
    }



    /*A class that implements runnable. It takes a String URL and
    * converts it into a bitmap. This bitmap is then added to the arraylist of
    * bitmaps. All will be performed in a background thread.*/
    public class createBitMap implements Runnable{
        private String url;
        private Bitmap bitmap = null;

        createBitMap(String url){
            this.url = url;
        }


        @Override
        public void run() {
            try {
                InputStream in = new URL(url).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(bitmap != null){
                addToBitmaps(bitmap);
            }
        }
    }

    public class RetrieveDataFromImgur implements Runnable{

        private String subRedit;
        private int page;

        RetrieveDataFromImgur(String subRedit, int page){
            this.subRedit = subRedit;
            this.page = page;
        }

        @Override
        public void run() {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(RetroFitHelper.ImgurEndPoint)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            RetroFitHelper imgurService = retrofit.create(RetroFitHelper.class);

            Call<Model> response = imgurService.getSubReditData("onoff", 5);

            response.enqueue(new Callback<Model>() {
                @Override
                public void onResponse(Response<Model> response, Retrofit retrofit) {

                   //check if the call was a succcess and return to avoid error if not
                    if(!response.isSuccess()){
                        Log.e(TAG, "Imgur retrofit Response failed");
                        Log.e(TAG, response.errorBody().toString());
                        Log.e(TAG, response.raw().toString());
                        return;
                    }

                    //Retrieve the data from the response to give a list of imagedata
                    Model model = response.body();
                    ArrayList<ImageData> data = model.getData();


                    for(ImageData i : data){
                        addToIds(i.getId());
                    }

                    //log 2 ids
                    Log.e(TAG, "first id is: " + getNextId());
                    Log.e(TAG, "Second id is " + getNextId());



                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Imgur retrofit onFailure called");
                    return;
                }
            });
        }
    }


}
