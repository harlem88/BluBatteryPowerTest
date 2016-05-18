package org.udoo.blubatterypowertest;

import android.app.Application;

import org.udoo.udooblulib.manager.UdooBluManager;

/**
 * Created by harlem88 on 16/05/16.
 */
public class BluBatteryApplication extends Application {
    private UdooBluManager mUdooBluManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mUdooBluManager = new UdooBluManager(this);

    }

    public UdooBluManager getBluManager() {
        return mUdooBluManager;
    }

}
