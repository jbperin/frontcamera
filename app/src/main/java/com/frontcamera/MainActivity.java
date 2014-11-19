package com.frontcamera;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ShareActionProvider;
import android.widget.Toast;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, View.OnClickListener, Camera.PictureCallback, SensorEventListener {
    private Camera camera;
    private TextureView textureView;
    private ShareActionProvider shareActionProvider;
    private boolean frozen = false;
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    enum Orientation{
        HorizUpright,
        HorizUpsideDown,
        Vertical
    }
    Orientation latestOrientation = Orientation.Vertical;
    Orientation takenOrientation = Orientation.Vertical;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorManager = (SensorManager) getSystemService(Activity.SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mSensorManager.registerListener(this, mRotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(this);
        textureView.setOnClickListener(this);
        setContentView(textureView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mRotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu); //Inflate the menu items for use in the action bar
        MenuItem item = menu.findItem(R.id.menu_item_share);
        shareActionProvider = (ShareActionProvider) item.getActionProvider();
        return true;
    }

    private Intent createSharingIntent(String screenshotUri) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("image/jpeg");
        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(screenshotUri));
        return sharingIntent;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int x;
        for (x = 0; x < Camera.getNumberOfCameras(); ++x) {
            Camera.getCameraInfo(x, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                break;
            }
        }

        try {
            camera = Camera.open(x);
            camera.setPreviewTexture(surface);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size previewSize = camera.getParameters().getSupportedPreviewSizes().get(0);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        camera.setParameters(parameters);

        camera.setDisplayOrientation(90);
        camera.startPreview();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
        }
        return true;
    }

    @Override
    public void onClick(View view) {
        if(frozen){
            camera.startPreview();
            frozen = false;
        }
        else {
            takenOrientation = latestOrientation;
            camera.takePicture(null, null, this);
        };
    }

    @Override
    public void onBackPressed() {
        if(frozen){
            camera.startPreview();
            frozen = false;
        }
        else {
            finish();
        }
    }

    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Matrix matrix = new Matrix();
        if(latestOrientation == Orientation.Vertical) {
            matrix.preRotate(-90);
        }
        if(latestOrientation == Orientation.HorizUpsideDown){
            matrix.preRotate(180);
        }
        matrix.preScale(1.0f, -1.0f);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        Uri uriTarget = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        OutputStream imageFileOS;
        try {
            imageFileOS = getContentResolver().openOutputStream(uriTarget);
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, imageFileOS);
            imageFileOS.flush();
            imageFileOS.close();
            Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show();
            String imagefileUri = uriTarget.toString();
            shareActionProvider.setShareIntent(createSharingIntent(imagefileUri));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        frozen = true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float pitch = sensorEvent.values[2];
        if (pitch <= -45) {
            latestOrientation = Orientation.HorizUpright;
        } else if(pitch >= 45){
            latestOrientation = Orientation.HorizUpsideDown;
        } else{
            latestOrientation = Orientation.Vertical;
        }
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}