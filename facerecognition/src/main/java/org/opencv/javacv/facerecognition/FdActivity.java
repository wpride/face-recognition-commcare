package org.opencv.javacv.facerecognition;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

//import java.io.FileNotFoundException;
//import org.opencv.contrib.FaceRecognizer;


public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    public static final int NATIVE_DETECTOR = 1;

    public static final int TRAINING = 0;
    public static final int SEARCHING = 1;
    public static final int IDLE = 2;

    private static final int frontCam = 1;
    private static final int backCam = 2;

//    private int countTrain=0;

    //    private MenuItem               mItemFace50;
//    private MenuItem               mItemFace40;
//    private MenuItem               mItemFace30;
//    private MenuItem               mItemFace20;
//    private MenuItem               mItemType;
//    
    private MenuItem nBackCam;
    private MenuItem mFrontCam;
    private MenuItem mCommCareSync;


    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    //   private DetectionBasedTracker  mNativeDetector;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private int mLikely = 999;

    String mPath = "";

    private Tutorial3View mOpenCvCameraView;
    private int mChooseCamera = backCam;

    TextView textViewState;
    Bitmap mBitmap;
    Handler mHandler;

    PersonRecognizer personRecognizer;
    ToggleButton toggleButtonGrabar;
    ImageButton imCamera;
    Button submitButton;
    Button syncButton;
    com.googlecode.javacv.cpp.opencv_contrib.FaceRecognizer faceRecognizer;

    String action = "";
    String caseId = "";
    String imageReturn[] = new String[3];

    static final long MAXIMG = 10;

    ArrayList<Mat> alimgs = new ArrayList<Mat>();

    int[] labels = new int[(int) MAXIMG];
    int countImages = 0;

    labels labelsFile;

    private static enum State {TRAIN, RECOGNIZE, IDLE}

    ;
    State _s;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        textViewState = (TextView) findViewById(R.id.textViewState);
        toggleButtonGrabar = (ToggleButton) findViewById(R.id.toggleButtonGrabar);
        imCamera = (ImageButton) findViewById(R.id.imageButton1);
        submitButton = (Button) findViewById(R.id.submitButton);
        textViewState.setVisibility(View.VISIBLE);
        syncButton = (Button) findViewById(R.id.syncButton);

        syncButton.setVisibility(View.GONE);

        mOpenCvCameraView = (Tutorial3View) findViewById(R.id.tutorial3_activity_java_surface_view);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mPath = getFilesDir() + "/facerecogOCV/";

        labelsFile = new labels(mPath);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.obj == "IMG") {
                    Canvas canvas = new Canvas();
                    canvas.setBitmap(mBitmap);
                    if (countImages >= MAXIMG - 1) {
                        toggleButtonGrabar.setChecked(false);
                        grabarOnclick();
                    }
                } else {
                    textViewState.setText(msg.obj.toString());
                    textViewState.setVisibility(View.VISIBLE);
                    submitButton.setVisibility(View.INVISIBLE);

                    if (mLikely < 0) ;
                    else if (mLikely < 50) {
                        submitButton.setBackgroundResource(R.drawable.button_green_background);
                        submitButton.setVisibility(View.VISIBLE);
                    } else if (mLikely < 80) {
                        submitButton.setBackgroundResource(R.drawable.button_yellow_background);
                        submitButton.setVisibility(View.VISIBLE);
                    } else {
                        submitButton.setBackgroundResource(R.drawable.button_red_background);
                        submitButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        };


        toggleButtonGrabar.setVisibility(View.VISIBLE);


        textViewState.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((textViewState.getText().toString().length() > 0))
                    toggleButtonGrabar.setVisibility(View.VISIBLE);
                else
                    toggleButtonGrabar.setVisibility(View.INVISIBLE);

                return false;
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                submitResults();
            }
        });


        toggleButtonGrabar.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                grabarOnclick();
            }
        });

        imCamera.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {

                if (mChooseCamera == frontCam) {
                    mChooseCamera = backCam;
                    mOpenCvCameraView.setCamBack();
                } else {
                    mChooseCamera = frontCam;
                    mOpenCvCameraView.setCamFront();

                }
            }
        });

        syncButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                performCommCareSync();
            }
        });

        boolean success = (new File(mPath)).mkdirs();
        if (!success) {
            Log.e("Error", "Error creating directory");
        }

        Bundle mBundle = getIntent().getExtras();

        //this is how we read in values sent by CommCare
        if (mBundle != null) {
            action = mBundle.getString("recognize_action", null);
            System.out.println("action is : " + action);
            if (action.equals("register")) {
                _s = State.TRAIN;
                caseId = mBundle.getString("case_id", null);
                System.out.println("caseId is : " + caseId);
                refreshView();
            } else if (action.equals("lookup")) {
                _s = State.RECOGNIZE;
                refreshView();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Launched from outside CommCare.", Toast.LENGTH_LONG).show();
            _s = State.TRAIN;
            caseId = "case_id_fake";
        }
    }

    private void startSearch() {

        Toast.makeText(getApplicationContext(), "Recognizing...", Toast.LENGTH_LONG).show();

        if (!personRecognizer.canPredict()) {
            //buttonSearch.setChecked(false);
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.SCanntoPredic), Toast.LENGTH_LONG).show();
            return;
        }
        toggleButtonGrabar.setVisibility(View.INVISIBLE);
        textViewState.setVisibility(View.VISIBLE);

    }

    private void startTrain() {

        Toast.makeText(getApplicationContext(), "Training...", Toast.LENGTH_LONG).show();

        textViewState.setVisibility(View.VISIBLE);
        toggleButtonGrabar.setVisibility(View.VISIBLE);

    }

    private void refreshView() {
        if (_s.equals(State.RECOGNIZE)) {

            textViewState.setVisibility(View.VISIBLE);

        } else if (_s.equals(State.TRAIN)) {

            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(caseId);

        }
    }

    private void performCommCareSync(){

        Cursor c = this.managedQuery(Uri.parse("content://org.commcare.dalvik.case/casedb/case"), null, null, null, null);

        c.moveToFirst();

        String[] caseids = new String[c.getCount()];
        int count = 0;

        while (c.isAfterLast() == false) {
            String mCaseId = c.getString(c.getColumnIndex("case_id"));
            caseids[count] = mCaseId;
            count++;
            c.moveToNext();
        }

        c.close();

        for(String caseid: caseids){
            Cursor c2 = this.managedQuery(Uri.parse("content://org.commcare.dalvik.case/casedb/data/"+caseid), null, null, null, null);

            while (c2.isAfterLast() == false) {
                String [] columns = c2.getColumnNames();
                for(String column: columns){
                    System.out.println("column: " + column);
                }
                c2.moveToNext();
            }

            c2.close();
        }

        for(String caseid: caseids){

            System.out.println("checking attachments for: " + caseid);

            Cursor c3 = this.managedQuery(Uri.parse("content://org.commcare.dalvik.case/casedb/attachment/"+caseid), null, null, null, null);

            while (c3.isAfterLast() == false) {
                String [] columns = c3.getColumnNames();
                for(String column: columns){
                    System.out.println("column2: " + column);
                }
                c3.moveToNext();
            }

            c3.close();
        }


    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    //   System.loadLibrary("detection_based_tracker");


                    personRecognizer = new PersonRecognizer(mPath);
                    personRecognizer.load();

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        //                 mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();

                }
                if (_s.equals(State.RECOGNIZE)) {
                    startSearch();
                } else if (_s.equals(State.TRAIN)) {
                    startTrain();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;


            }
        }
    };

    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        //Log.i(TAG, "Instantiated new " + this.getClass());
    }

    void grabarOnclick() {
        if (toggleButtonGrabar.isChecked())
            _s = State.TRAIN;
        else {
            if (_s == State.TRAIN) ;
            // train();
            //fr.train();
            countImages = 0;
            _s = State.IDLE;
        }


    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);


    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            //  mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        } else if (mDetectorType == NATIVE_DETECTOR) {
//            if (mNativeDetector != null)
//                mNativeDetector.detect(mGray, faces);
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();

        if ((facesArray.length == 1) && (_s == State.TRAIN) && (countImages < MAXIMG) && (!(caseId == null && caseId.equals("")))) {


            Mat m = new Mat();
            Rect r = facesArray[0];


            m = mRgba.submat(r);
            mBitmap = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);


            Utils.matToBitmap(m, mBitmap);

            Message msg = new Message();
            String textTochange = "IMG";
            msg.obj = textTochange;
            mHandler.sendMessage(msg);

            if (countImages < MAXIMG) {

                personRecognizer.add(m, textViewState.getText().toString());
                String imagePath = saveImage(mBitmap, caseId, countImages);

                if (countImages < imageReturn.length) {
                    imageReturn[countImages] = imagePath;
                }

                countImages++;
            }

        } else if ((facesArray.length > 0) && (_s == State.RECOGNIZE)) {
            Mat m = new Mat();
            m = mGray.submat(facesArray[0]);
            mBitmap = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);


            Utils.matToBitmap(m, mBitmap);
            Message msg = new Message();
            String textTochange = "IMG";
            msg.obj = textTochange;
            mHandler.sendMessage(msg);

            textTochange = personRecognizer.predict(m);
            caseId = textTochange;
            mLikely = personRecognizer.getProb();
            msg = new Message();
            msg.obj = textTochange;
            mHandler.sendMessage(msg);

        } else{
            if(textViewState.getText().toString().isEmpty()){
                Toast.makeText(getApplicationContext(), "Your case_id field is empty and must be set.", Toast.LENGTH_LONG).show();
            }
        }
        for (int i = 0; i < facesArray.length; i++)
            Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

        return mRgba;
    }

    public String saveImage(Bitmap bitmap, String caseId, int count) {

        System.out.println("saving image");

        File sdCardDirectory = Environment.getExternalStorageDirectory();

        File image = new File(sdCardDirectory, "opencv-" + caseId + "-" + count + ".jpg");

        System.out.println("Saving image: " + image.getAbsolutePath());

        // Encode the file as a PNG image.
        FileOutputStream outStream;
        try {

            outStream = new FileOutputStream(image);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
        /* 100 to keep full quality of the image */

            outStream.flush();
            outStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return image.getAbsolutePath();

    }

    public void submitResults() {
        Intent data = new Intent();
        Bundle responses = new Bundle();
        responses.putString("match_id", caseId);

        for (int i = 0; i < imageReturn.length; i++) {
            responses.putString("image_" + i, imageReturn[i]);
            System.out.println("Putting image: " + imageReturn[i]);
        }

        data.putExtra("odk_intent_bundle", responses);
        // this is the value that CommCare will use as the result of the intent question
        data.putExtra("odk_intent_data", caseId);
        setResult(Activity.RESULT_OK, data);
        finish();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        if (mOpenCvCameraView.numberCameras() > 1) {
            nBackCam = menu.add(getResources().getString(R.string.SFrontCamera));
            mFrontCam = menu.add(getResources().getString(R.string.SBackCamera));
            mCommCareSync = menu.add("CommCare Sync");
        } else {
            imCamera.setVisibility(View.INVISIBLE);
        }
        //mOpenCvCameraView.setAutofocus();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        nBackCam.setChecked(false);
        mFrontCam.setChecked(false);
        if (item == nBackCam) {
            mOpenCvCameraView.setCamFront();
            mChooseCamera = frontCam;
        }
        else if (item == mFrontCam) {
            mChooseCamera = backCam;
            mOpenCvCameraView.setCamBack();
        } else if(item == mCommCareSync){
            performCommCareSync();
        }

        item.setChecked(true);

        return true;
    }
}