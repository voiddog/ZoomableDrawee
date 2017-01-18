package org.voiddog.zoomabledrawee;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

/**
 * 文件描述
 *
 * @author qigengxin
 * @since 2016-11-29 17:37
 */


public class ZoomableApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        Fresco.initialize(this);
    }
}
