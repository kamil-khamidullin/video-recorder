package com.uip.VideoRecorder;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.Button;
import android.widget.Toast;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Author: Khamidullin Kamil
 * Date: 02.07.13
 * Time: 12:01
 */
public class RecorderActivity extends Activity implements SurfaceHolder.Callback,
        View.OnClickListener, Camera.PreviewCallback {
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private SurfaceView preview;
    private VideoRecorder recorder;

    private Button saveBtn;
    private Button recordBtn;
    private Boolean isRecording = false;

    private static final int IDM_PREF = 101;
    private static final int IDM_EXIT = 102;

    private int videoBitrate        = 100000;
    private int videoFramerate      = 15;
    private int audioBitrate        = 8000;
    private int audioSamplingrate   = 8000;
    private int audioChannels       = 1;
    private int videoWidth          = 640;
    private int videoHeight         = 480;
    private int videoMaxDuration    = 60000;  //1 min
    private int videoMaxFileSize    = 5000000;  //50 mb
    protected GPSTracker gpsTracker;

    protected final static String RECORDER_DIR = "VideoRecorder";

    SimpleDateFormat filenameDateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");
    SimpleDateFormat subtitleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    SimpleDateFormat timeFormat         = new SimpleDateFormat("HH:mm:ss,SSS");

    protected long currVideoStart;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // если хотим, чтобы приложение постоянно имело портретную ориентацию
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // если хотим, чтобы приложение было полноэкранным
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // и без заголовка
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main);

        // наше SurfaceView имеет имя SurfaceView01
        preview = (SurfaceView) findViewById(R.id.SurfaceView01);

        surfaceHolder = preview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // кнопка имеет имя Button01
        saveBtn = (Button) findViewById(R.id.saveBtn);
        saveBtn.setText("Save");
        saveBtn.setEnabled(false);
        saveBtn.setOnClickListener(this);

        recordBtn = (Button) findViewById(R.id.recordBtn);
        recordBtn.setText("Start");
        recordBtn.setOnClickListener(this);

        gpsTracker  = new GPSTracker(this);
        recorder    = new VideoRecorder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera = Camera.open();
        recorder.open();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gpsTracker.stopUsingGPS();
        recorder.close();

        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, IDM_PREF, Menu.NONE, "Settings");
        menu.add(Menu.NONE, IDM_EXIT, Menu.NONE, "Exit");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        float aspect = (float) previewSize.width / previewSize.height;

        int previewSurfaceWidth = preview.getWidth();
        int previewSurfaceHeight = preview.getHeight();

        ViewGroup.LayoutParams lp = preview.getLayoutParams();

        // здесь корректируем размер отображаемого preview, чтобы не было
        // искажений

        if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            // портретный вид
            camera.setDisplayOrientation(90);
            lp.height = previewSurfaceHeight;
            lp.width = (int) (previewSurfaceHeight / aspect);
        } else {
            // ландшафтный
            camera.setDisplayOrientation(0);
            lp.width = previewSurfaceWidth;
            lp.height = (int) (previewSurfaceWidth / aspect);
        }

        preview.setLayoutParams(lp);
        camera.startPreview();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onClick(View v) {
        if (v == saveBtn) {
            stopRecording();
            startRecording();
            Toast.makeText(this, "video saved", Toast.LENGTH_SHORT).show();
        } else if (v == recordBtn) {
            if (isRecording) {
                stopRecording();

                // снова включаем preview камеры
                camera.startPreview();

                recordBtn.setText("Start");

                // включаем кнопку фотосъемки
                saveBtn.setEnabled(false);
            } else {
                // выключаем кнопку фотосъемки
                saveBtn.setEnabled(true);

                // останавливаем preview камеры (иначе будет ошибка)
                camera.stopPreview();

                startRecording();
                recordBtn.setText("Stop");
            }
        }
    }

    protected void startRecording() {
        // разрешаем общий доступ к камере
        camera.unlock();

        // рекордер использует уже созданную камеру
        recorder.setCamera(camera);

        // задаем параметры, preview, имя файла и включаем запись
        recorder.setRecorderParams(videoBitrate, audioBitrate, audioSamplingrate, audioChannels, videoFramerate, videoWidth, videoHeight, videoMaxDuration, videoMaxFileSize);
        recorder.setPreview(surfaceHolder.getSurface());
        recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording();
                    startRecording();
                }
            }
        });

        isRecording = true;

        currVideoStart = System.currentTimeMillis();
        File sdPath = Environment.getExternalStorageDirectory();
        sdPath = new File(sdPath.getAbsolutePath() + "/" + RECORDER_DIR);
        if(!sdPath.exists()) {
            sdPath.mkdirs();
        }

        recorder.start(String.format("/sdcard/%s/%s.mp4", RECORDER_DIR, filenameDateFormat.format(new Date(currVideoStart))));

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    gpsTracker.getLocation();

                    File sdPath = Environment.getExternalStorageDirectory();
                    sdPath = new File(sdPath.getAbsolutePath() + "/" + RECORDER_DIR);

                    File sdFile         = new File(sdPath, String.format("%s.srt", filenameDateFormat.format(new Date(currVideoStart))));
                    StringBuilder sb    = new StringBuilder();

                    int i = 1;
                    while (isRecording) {

                        //"Time: 12.07.2013 12:30:38 Position: 42.811522,-105.036163\n"
                        sb.append(String.format("%s\n", i));
                        sb.append(String.format("%s -->", timeFormat.format(new Date())));


                        //bw.write(String.format("%s -->", timeFormat.format(new Date())));
                        String time = subtitleDateFormat.format(new Date());
                        try {
                            Thread.sleep(1000);
                            sb.append(String.format("  %s\n", timeFormat.format(new Date())));
                            sb.append(String.format("Time: %s Position: %s, %s\n", time, gpsTracker.getLatitude(), gpsTracker.getLongitude()));

                        } catch (InterruptedException ex) {}
                        i++;
                    }

                    BufferedWriter bw   = new BufferedWriter(new FileWriter(sdFile));
                    bw.write(sb.toString());
                    bw.close();

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException exx) {
                    exx.printStackTrace();
                }

                return null;
            }

        }.execute();
    }

    protected void stopRecording() {
        isRecording = false;
        recorder.stop();

        try {
            // запрещаем общий доступ к камере
            camera.reconnect();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    @Override
    public void onPreviewFrame(byte[] paramArrayOfByte, Camera paramCamera) {
        // здесь можно обрабатывать изображение, показываемое в preview
    }
}
