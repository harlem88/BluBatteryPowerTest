package org.udoo.blubatterypowertest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.udoo.blubatterypowertest.databinding.ActivityMainBinding;
import org.udoo.blubatterypowertest.service.BluBatteryJobService;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    public static final int MSG_SERVICE_OBJ = 0;
    public static final int MSG_REACHED = 1;
    public static final int MSG_REACHED_FAILED = 2;

    public static final int MSG_CONNECT = 3;
    public static final int MSG_CONNECT_FAILED = 4;

    private static final String TAG = "MAIN_ACTIVITY";

    private ActivityMainBinding mainBinding;
    private long count;
    private ComponentName mServiceComponent;
    private BluBatteryJobService mBluBatteryJobService;
    private static int kJobId = 0;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private Timer mTime;
    private long mPingCount;
    private long mConCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTime = new Timer();
        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mServiceComponent = new ComponentName(this, BluBatteryJobService.class);
        mPingCount = mConCount = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                        }
                    }
                });
                builder.show();
            } else {
//                getFragmentManager().beginTransaction()
//                        .add(R.id.container, new ScanMultipleBluFragment()).commit();
            }
        } else if (savedInstanceState == null) {
//            getFragmentManager().beginTransaction()
//                    .add(R.id.container, new ScanMultipleBluFragment()).commit();

            Intent startServiceIntent = new Intent(this, BluBatteryJobService.class);
            startServiceIntent.putExtra("messenger", new Messenger(mHandler));
            startServiceIntent.putExtra(BluBatteryJobService.BLUADDRESS, "B0:B4:48:C3:B1:81");
            startService(startServiceIntent);
        } else {

            Intent startServiceIntent = new Intent(this, BluBatteryJobService.class);
            startServiceIntent.putExtra("messenger", new Messenger(mHandler));
            startServiceIntent.putExtra(BluBatteryJobService.BLUADDRESS, "B0:B4:48:C3:B1:81");
            startService(startServiceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "BT ON", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(this, "BT OFF", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "Unknown request code");
                break;
        }
    }

    private Handler mHandler = new Handler(/* default looper */) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SERVICE_OBJ:
                    mBluBatteryJobService = (BluBatteryJobService) msg.obj;
                    scheduleJob();
                    startTime();
                    break;
                case MSG_REACHED:
                    incPing();
                    break;
                case MSG_REACHED_FAILED:
                    stopTime();
                    break;
                case MSG_CONNECT:
                    incConnect();
                    break;
                case MSG_CONNECT_FAILED:
                    stopTime();
                    break;

            }
        }


    };

    private void incConnect() {
        mainBinding.connectionCount.setText(""+ ++mConCount);
    }

    private void incPing() {
        mainBinding.pingCount.setText(""+ ++mPingCount);
    }


    private String print(long different) {
            String value = "";

            long minutesInMilli = 60;
            long hoursInMilli = minutesInMilli * 60;
            long daysInMilli = hoursInMilli * 24;

            long elapsedDays = different / daysInMilli;
            different = different % daysInMilli;

            long elapsedHours = different / hoursInMilli;
            different = different % hoursInMilli;

            long elapsedMinutes = different / minutesInMilli;
            different = different % minutesInMilli;

            long elapsedSeconds = different;

            value = elapsedDays + ":D " + elapsedHours + ":HH " + elapsedMinutes + ":mm " + elapsedSeconds + ":s";

            return value;
        }

        private boolean ensureBluBatteryJobService() {
            if (mBluBatteryJobService == null) {
                Toast.makeText(MainActivity.this, "Service null, never got callback?",
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }

        /**
         * UI onclick listener to schedule a job. What this job is is defined in
         * TestJobService#scheduleJob().
         */
        public void scheduleJob() {
            if (!ensureBluBatteryJobService()) {
                return;
            }

            JobInfo.Builder builder = new JobInfo.Builder(kJobId++, mServiceComponent);
            builder.setPeriodic(61000);
            //builder.setPeriodic(600000);
            mBluBatteryJobService.scheduleJob(builder.build());

        }

        public void cancelAllJobs(View v) {
            JobScheduler tm =
                    (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            tm.cancelAll();
        }

        /**
         * UI onclick listener to call jobFinished() in our service.
         */
        public void finishJob(View v) {
            if (!ensureBluBatteryJobService()) {
                return;
            }
            mBluBatteryJobService.callJobFinished();
        }

        /**
         * Receives callback from the service when a job has landed
         * on the app. Colours the UI and post a message to
         * uncolour it after a second.
         */
        public void onReceivedStartJob(JobParameters params) {
//        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_START);
//        mHandler.sendMessageDelayed(m, 1000L); // uncolour in 1 second.

            startTime();
        }

        /**
         * Receives callback from the service when a job that
         * previously landed on the app must stop executing.
         * Colours the UI and post a message to uncolour it after a
         * second.
         */
        public void onReceivedStopJob() {
//        Message m = Message.obtain(mHandler, MSG_UNCOLOUR_STOP);
//        mHandler.sendMessageDelayed(m, 2000L); // uncolour in 1 second.
            stopTime();
        }

        private void startTime() {
            mTime.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mainBinding.timeView.setText(print(count++));
                        }
                    });
                }
            }, 1000, 1000);

        }

        private void stopTime() {
            mTime.cancel();
            mainBinding.timeView.setTextColor(Color.RED);
        }


}
