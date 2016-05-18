package org.udoo.blubatterypowertest.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.udoo.blubatterypowertest.BluBatteryApplication;
import org.udoo.blubatterypowertest.MainActivity;
import org.udoo.udooblulib.interfaces.IBleDeviceListener;
import org.udoo.udooblulib.manager.UdooBluManager;
import org.udoo.udooblulib.scan.BluScanCallBack;
import org.udoo.udooblulib.sensor.Constant;
import org.udoo.udooblulib.sensor.UDOOBLESensor;

import java.sql.Date;
import java.util.LinkedList;

/**
 * Created by harlem88 on 11/05/16.
 */

public class BluBatteryJobService extends JobService {
    private static final String TAG = "SyncService";
    private UdooBluManager mUdooBluManager;
    private String mBluAddress;
    private boolean mBluReached;
    public static final String BLUADDRESS = "BLU_ADDRESS";
    private Messenger mClientMessenger;
    private Date mTimePassedForOldConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");
        mUdooBluManager = ((BluBatteryApplication) getApplication()).getBluManager();

        mTimePassedForOldConnection = new Date(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's MainActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth. See "setUiCalback()"
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mClientMessenger = intent.getParcelableExtra("messenger");
        Message m = Message.obtain();
        m.what = MainActivity.MSG_SERVICE_OBJ;
        m.obj = this;
        mBluAddress = intent.getStringExtra(BLUADDRESS);
        try {
            mClientMessenger.send(m);
        } catch (RemoteException e) {
            Log.e(TAG, "Error passing service object back to activity.");
        }
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        jobParamsMap.add(params);
//        if (mActivity != null) {
//            mActivity.onReceivedStartJob(params);
//        }
        Log.i(TAG, "onStartJob: ");
        if (isTimeForConnection()) {
            connection();
        } else {
            ping();
        }

        Log.i(TAG, "on start job: " + params.getJobId());
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "on stop job: " + params.getJobId());
        return true;
    }

    MainActivity mActivity;
    private final LinkedList<JobParameters> jobParamsMap = new LinkedList<JobParameters>();

    public void setUiCallback(MainActivity activity) {
        mActivity = activity;
    }

    /**
     * Send job to the JobScheduler.
     */
    public void scheduleJob(JobInfo t) {
        Log.d(TAG, "Scheduling job");
        JobScheduler tm =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.schedule(t);
    }

    /**
     * Not currently used, but as an exercise you can hook this
     * up to a button in the UI to finish a job that has landed
     * in onStartJob().
     */
    public boolean callJobFinished() {
        JobParameters params = jobParamsMap.poll();
        if (params == null) {
            return false;
        } else {
            jobFinished(params, false);
            return true;
        }
    }

    private void ping() {
        Log.i(TAG, "ping: ");
        mBluReached = false;
        mUdooBluManager.scanLeDevice(true, bluScanCallBack);
    }


    BluScanCallBack bluScanCallBack = new BluScanCallBack() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (!mBluReached && device.getAddress().equals(mBluAddress)) {
                mBluReached = true;
                Message m = Message.obtain();
                m.what = MainActivity.MSG_REACHED;
                try {
                    mClientMessenger.send(m);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onScanFinished() {
            super.onScanFinished();

            if (!mBluReached) {
                Message m = Message.obtain();
                m.what = MainActivity.MSG_REACHED_FAILED;
                try {
                    mClientMessenger.send(m);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void connection() {
        mUdooBluManager.connect(mBluAddress, new IBleDeviceListener() {
            @Override
            public void onDeviceConnected() {
            }

            @Override
            public void onServicesDiscoveryCompleted(String address) {
                Log.i(mBluAddress, "discoverCompleted");

                mTimePassedForOldConnection = new Date(System.currentTimeMillis());

                Message m = Message.obtain();
                m.what = MainActivity.MSG_CONNECT;
                try {
                    mClientMessenger.send(m);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                mUdooBluManager.digitalWrite(mBluAddress, Constant.IOPIN_VALUE.HIGH, Constant.IOPIN.D6);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mUdooBluManager.digitalWrite(mBluAddress, Constant.IOPIN_VALUE.LOW, Constant.IOPIN.D6);
                    }
                }, 10000);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mUdooBluManager.disconnect(mBluAddress);
                    }
                }, 11000);
            }

            @Override
            public void onDeviceDisconnect() {
            }
        });
    }

    public boolean isTimeForConnection() {
        boolean result;
        Date now = new Date(System.currentTimeMillis());
        long different = now.getTime() - mTimePassedForOldConnection.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long hoursInMilli = minutesInMilli * 60;

        long elapsedHours = different / hoursInMilli;
//        different = different % hoursInMilli;

        result = elapsedHours >= 2;

        return result;
    }
}