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
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
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
    public static final int COUNTDOWN_SECONDS = 5;
    private ImageView mIVMain;
    private ArrayList<Bitmap> mBitMaps = new ArrayList<Bitmap>();
    private ReentrantReadWriteLock mRWbitmapsLock = new ReentrantReadWriteLock(false);
    private ReentrantReadWriteLock mRWidsLock = new ReentrantReadWriteLock(false);
    private ArrayList<String> mIds = new ArrayList<String>();
    private volatile boolean mStopSlide = false;
    private volatile boolean mCreatingBitMaps = false;
    private volatile boolean mLoopStarted = false;
    private CountDownLatch mCDLatch= new CountDownLatch(1);
    private CountDownLatch mCDLatchRemovedImage = new CountDownLatch(1);
    private String[] Subredits = {"onoff","gondwild","nsfw","ass","Amateur","cumsluts",
            "PetiteGoneWild","HappyEmbarrassedGirls","redheads","anal","LegalTeens",
            "GWCouples","wifesharing","dirtysmall","Blowjobs","nsfwhardcore","WouldYouFuckMyWife",
            "AmateurArchives","rearpussy","Innie","BonerMaterial","jilling","LipsThatGrip","pussy",
            "LabiaGW","grool","amateurcumsluts","asshole","gwcumsluts","lesbians","blackchickswhitedicks",
            "AnalGW"};
    private Bitmap mRemovedBitmap;

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


        //get access to the image view
        mIVMain = (ImageView) findViewById(R.id.IVMain);

        //create a removed bitmap to check other bitmaps against
        new Thread(new createRemovedBitmap()).start();

        //prevent the screen from turning off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        mStopSlide = false;

        //TODO persist the ids so the process can start quickly
        //at first start up get data from imgur. this will start the whole process
        new Thread(new RetrieveDataFromImgur(getRandomSubredit(), getRandomPage())).run();

    }

    @Override
    protected void onPause() {
        super.onPause();
        mStopSlide = true;
    }

    /*Method to add a bitmap to the arraylist. this is done within a write lock
            * to ensure it remains in sync*/
    private void addToBitmaps(Bitmap bitmap) {
        //wait for the removed image to be created
        try {
            mCDLatchRemovedImage.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(!bitmap.sameAs(mRemovedBitmap)){
        mRWbitmapsLock.writeLock().lock();
        mBitMaps.add(bitmap);
        mRWbitmapsLock.writeLock().unlock();
    } else{
            Log.e("createBitMap", "Image removed");
        }
    }

    /*Method to add a string id to the arraylist of Id's. done with a
    * write lock to remain in sync*/
    private void addToIds(String id) {
        mRWidsLock.writeLock().lock();
        mIds.add(id);
        mRWidsLock.writeLock().unlock();

    }

    /*Method to remove the first bitmap and return it. this is controlled by a
    * write lock to avoid the arraylist coming out of sync*/
    private Bitmap getNextBitmap() {
        //TODO do check to make sure there are bitmaps. consider when screen changes and clears out array
        mRWbitmapsLock.writeLock().lock();
        Bitmap b = mBitMaps.remove(0);

        mRWbitmapsLock.writeLock().unlock();

        return b;
    }

    /*Method to remove the first id from the bitmap and return it. Done within
    * a write lock to keep the arraylist in sync*/
    private String getNextId() {
        mRWidsLock.writeLock().lock();
        String s = mIds.remove(0);
        mRWidsLock.writeLock().unlock();
        return s;
    }

    /*Method to get the length of the Ids array. this is done within a read lock*/
    private int getIdsLengh() {
        int l;
        mRWidsLock.readLock().lock();
        l = mIds.size();
        mRWidsLock.readLock().unlock();

        return l;
    }

    /*Method to get the size of the bitmaps array. this is done within a read lock*/
    private int getBitmapsLength() {
        int l;
        mRWbitmapsLock.readLock().lock();
        l = mBitMaps.size();
        mRWbitmapsLock.readLock().unlock();

        return l;
    }


    /*A class that implements runnable. It takes a String URL and
    * converts it into a bitmap. This bitmap is then added to the arraylist of
    * bitmaps. All will be performed in a background thread.*/
    public class createBitMap implements Runnable {
        private String url;
        private Bitmap bitmap = null;

        createBitMap(String url) {
            this.url = url;
            Log.e("createBitMap","URL is: "+ url);
        }


        @Override
        public void run() {
            try {
                InputStream in = new URL(url).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (bitmap != null) {
                addToBitmaps(bitmap);
                if(mCDLatch.getCount()> 0){
                    mCDLatch.countDown();
                }
            }
        }
    }


    /*Class to retrieve the data from Imgur. it makes a call to the imgur api
    * using retrofit. it obtains the picture id's and puts these in the id array
    * It then generates the bit maps*/
    public class RetrieveDataFromImgur implements Runnable {

        private String subRedit;
        private int page;

        RetrieveDataFromImgur(String subRedit, int page) {
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

            Call<Model> response = imgurService.getSubReditData(subRedit, page);

            response.enqueue(new Callback<Model>() {
                @Override
                public void onResponse(Response<Model> response, Retrofit retrofit) {

                    //check if the call was a succcess and return to avoid error if not
                    if (!response.isSuccess()) {
                        Log.e(TAG, "Imgur retrofit Response failed");
                        Log.e(TAG, response.errorBody().toString());
                        Log.e(TAG, response.raw().toString());
                        return;
                    }

                    //Retrieve the data from the response to give a list of imagedata
                    Model model = response.body();
                    ArrayList<ImageData> data = model.getData();


                    for (ImageData i : data) {
                        addToIds(i.getId());
                    }

                    generateBitmaps();

                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Imgur retrofit onFailure called");
                    return;
                }
            });
        }
    }

    //return a random page number
    private int getRandomPage(){
        return (int) Math.floor(Math.random() * 1000);

    }
    //return a random string
    private String getRandomSubredit(){
        int size = Subredits.length;
        return Subredits[(int) Math.floor(Math.random() * size)];
    }

    /*Method to generate the bitmaps. It generates as many as stated in the refreshpoint
    * if there are not enough id's to make the refreshpoint then more are requested from
    * imgur. This method also starts the countdown loop if not already started*/
    private void generateBitmaps() {
        int idsLength = getIdsLengh();
        int loopTo;
        int refreshPoint = 10;

        //mCreatingBitMaps is a flag to show the method is running. If it is already running then
        //this method just returns.
        if (!mCreatingBitMaps) {
            mCreatingBitMaps = true;

            //if there are more id's than the refreshpoint then loop until the refreshpoint
            if (idsLength > refreshPoint) {
                loopTo = refreshPoint;
            } else {
                //get fresh id's from imgur
                new Thread(new RetrieveDataFromImgur(getRandomSubredit(), getRandomPage())).start();

                //if there are no ids, return immediately. this method will be called
                //again once more ids are available
                if (idsLength == 0) {
                    mCreatingBitMaps = false;
                    return;
                }

                //if there are ids, loop until all current used.
                loopTo = idsLength;
            }

            //Loop to generate the bitmaps. The bitmaps will be created Asynchronously
            for (int i = 0; i < loopTo; i++) {
                String url = "http://i.imgur.com/" + getNextId() + "l.jpg";
                new Thread(new createBitMap(url)).start();
            }

            //If this is the first time the method is run, start the countdown loop
            if (!mLoopStarted) {
                new Thread(new CountDown(COUNTDOWN_SECONDS, true)).start();
                mLoopStarted = true;
            }

            mCreatingBitMaps = false;


        }


    }

    /*Runnable class to create a countdown to loop through the pictures.
    * This sleeps the thread then requests the ui thread to display the next picture.
    * A check is performed on the remaining bitmaps and generates new bitmaps once
    * they get under 5*/
    public class CountDown implements Runnable {

        private int Secs;
        private boolean firstTime;


        public CountDown(int Secs, boolean firstTime) {
            this.Secs = Secs;
            this.firstTime = firstTime;

        }

        @Override
        public void run() {

            //if this is the first time. Display the first picture immediately. then
            //start the countdown
            if (firstTime) {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            //await for the first bitmap to be added to the arraylist
                            try {
                                mCDLatch.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Bitmap b = getNextBitmap();

                            if(b!=null){
                                mIVMain.setImageBitmap(b);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //every second check if the loop has stopped and return without
            //displaying the next picture. This is used for direction controls
            for (int i = 0; i < Secs; i++) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (mStopSlide) {
                    return;
                }

            }


            //show the picture on the UI thread
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        Bitmap b = getNextBitmap();

                        if(b!=null){
                            mIVMain.setImageBitmap(b);
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }


            //once there are less than 5 bitmaps, generate more.
            if (getBitmapsLength() < 5) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        generateBitmaps();
                    }
                }).start();
            }

            //Create a new thread to continue the countdown. This is
            //not the first one so will do full countdown before
            //picture is shown.
            new Thread(new CountDown(COUNTDOWN_SECONDS, false)).start();

        }
    }

    /*Runnable class to createa a removed image. It calls imgur with a fake image id
    * and decodes the removed image into a bitmap. This is used when other bitmaps
    * are created to check they have not been removed*/
    public class createRemovedBitmap implements Runnable{
        Bitmap bitmap = null;
        @Override
        public void run() {
            try {
                InputStream in = new URL("http://i.imgur.com/rrrrrrl.jpg").openStream();
                bitmap = BitmapFactory.decodeStream(in);
                mRemovedBitmap = bitmap;
                mCDLatchRemovedImage.countDown();
                Log.e("createBitMap","Removed Created");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}
