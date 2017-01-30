package org.voiddog.zoomabledrawee;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.drawee.drawable.ScalingUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件描述
 *
 * @author qigengxin
 * @since 2016-11-14 14:39
 */


public class ZoomableActivity extends AppCompatActivity{

    ViewPager viewPager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zoomable);

        viewPager = (ViewPager) findViewById(R.id.view_pager);
        viewPager.setAdapter(new ZoomViewPagerAdapter(viewPager.getContext()));
    }

    public class ZoomViewPagerAdapter extends PagerAdapter{

        List<View> zoomDrawables = new ArrayList<>();

        public ZoomViewPagerAdapter(Context context){
            ZoomableDrawee zoomableDrawee;
            zoomDrawables.add(zoomableDrawee = new ZoomableDrawee(context));
            zoomableDrawee.setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER);
            zoomableDrawee.setImageURI(Uri.parse("http://voiddog.qiniudn.com/rem.jpg"));
            zoomDrawables.add(zoomableDrawee = new ZoomableDrawee(context));
            zoomableDrawee.setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER);
            zoomableDrawee.setImageURI(Uri.parse("http://voiddog.qiniudn.com/rem.jpg"));
            zoomDrawables.add(zoomableDrawee = new RegionDecodeZoomableDrawee(context));
            zoomableDrawee.setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER);
            zoomableDrawee.setImageURI(Uri.parse("http://voiddog.qiniudn.com/test/WechatIMG4.jpeg"));
            zoomDrawables.add(zoomableDrawee = new RegionDecodeZoomableDrawee(context));
            zoomableDrawee.setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER);
            zoomableDrawee.setImageURI(Uri.parse("file:///storage/emulated/0/DCIM/Camera/PANO_20170122_073013.jpg"));
        }

        @Override
        public int getCount() {
            return zoomDrawables.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(zoomDrawables.get(position), new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return zoomDrawables.get(position);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(zoomDrawables.get(position));
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
