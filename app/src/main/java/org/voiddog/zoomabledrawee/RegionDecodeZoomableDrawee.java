package org.voiddog.zoomabledrawee;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

/**
 * 局部解码
 *
 * @author qigengxin
 * @since 2017-01-19 16:44
 */


public class RegionDecodeZoomableDrawee extends ZoomableDrawee{

    /**
     * 懒加载 uri
     */
    private Uri lazyLoadUri;

    private EncodeBitmapHelper encodeBitmapHelper = new EncodeBitmapHelper(dp2px(150), getMemoryCacheSize(getContext())) {
        @Override
        protected void onBitmapUpdate() {
            postInvalidate();
        }
    };

    public RegionDecodeZoomableDrawee(Context context) {
        super(context);
        init(context, null, 0);
    }

    public RegionDecodeZoomableDrawee(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public RegionDecodeZoomableDrawee(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        encodeBitmapHelper.attachToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        encodeBitmapHelper.detachFromWindow();
    }

    @Override
    public void setImageURI(Uri uri) {
        if(uri == null || isGif(uri)){
            super.setImageURI(uri);
            lazyLoadUri = null;
            return;
        }

        if(getWidth() == 0 || getHeight() == 0){
            // 懒加载
            lazyLoadUri = uri;
            return;
        } else {
            lazyLoadUri = null;
        }

        encodeBitmapHelper.setUri(uri);

        // 设置图片数据源
        ImageRequest imageRequest = ImageRequestBuilder.newBuilderWithSource(uri)
                .setRotationOptions(RotationOptions.autoRotate())
                .setResizeOptions(new ResizeOptions(getWidth(), getHeight()))
                .build();

        DraweeController controller = Fresco.newDraweeControllerBuilder()
                .setControllerListener(getZoomableControllerListener())
                .setOldController(getController())
                .setImageRequest(imageRequest)
                .build();
        setController(controller);
    }

    /**
     * 初始化
     * @param context
     * @param attrs
     * @param defStyle
     */
    protected void init(Context context, AttributeSet attrs, int defStyle){
    }

    protected int dp2px(double dp){
        return (int) (getContext().getResources().getDisplayMetrics().density * dp);
    }

    RectF tmpRect = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(encodeBitmapHelper.isLoadingData()){
            return;
        }

        if(!needDisplayDecodeBitmap()){
            return;
        }

        getInnerVisibleBounds(tmpRect);

        int originWidth = encodeBitmapHelper.getOriginWidth();
        float scale = originWidth * 1.0f / (tmpRect.right - tmpRect.left);
        int scaleExp = (int) Math.ceil(Math.log(scale < 1 ? 1 : scale)/Math.log(2));
        int originRegionBitmapSize = encodeBitmapHelper.getBitmapSegSize(scaleExp);

        int column = originWidth / originRegionBitmapSize;
        column += originWidth % originRegionBitmapSize == 0 ? 0 : 1;
        int startX = (int)(Math.max(0, -tmpRect.left) * scale) / originRegionBitmapSize;
        int startY = (int)(Math.max(0, -tmpRect.top) * scale) / originRegionBitmapSize;
        int endX = (int)(Math.min(tmpRect.right - tmpRect.left, getWidth() - tmpRect.left) * scale) / originRegionBitmapSize;
        int endY = (int)(Math.min(tmpRect.bottom - tmpRect.top, getHeight() - tmpRect.top) * scale) / originRegionBitmapSize;
        int x = startX, y = startY;

        int count = canvas.save();
        canvas.translate(tmpRect.left, tmpRect.top);
        float visibleScale = (1 << scaleExp) / scale;
        canvas.scale(visibleScale, visibleScale);
        while (y <= endY){
            int id = y * column + x;
            int key = encodeBitmapHelper.getKey(scaleExp, id);
            Bitmap drawRegionBitmap = encodeBitmapHelper.getBitmap(key);
            if(drawRegionBitmap == null){
                encodeBitmapHelper.addDecodeRegion(key);
            } else {
                int size = encodeBitmapHelper.getBitmapSegSize(0);
                int left = size * x, top = size * y;
                canvas.drawBitmap(drawRegionBitmap, left, top, null);
            }
            ++x;
            if(x > endX){
                x = startX;
                ++y;
            }
        }
        canvas.restoreToCount(count);
    }

    protected boolean needDisplayDecodeBitmap(){
        getInnerVisibleBounds(tmpRect);
        if (tmpRect.left >= tmpRect.right || tmpRect.top >= tmpRect.bottom){
            // 视图大小错误
            return false;
        }

        if(Math.max((tmpRect.right - tmpRect.left) / getWidth(), (tmpRect.bottom - tmpRect.top) / getHeight()) < 2){
            // 可视缩放小于2
            return false;
        }

        if(tmpRect.left >= getWidth() || tmpRect.right <= 0 || tmpRect.top >= getHeight() || tmpRect.bottom <= 0){
            return false;
        }

        getInnerOriginBounds(tmpRect);
        if(encodeBitmapHelper.getOriginWidth() / (tmpRect.right - tmpRect.left) < 2){
            return false;
        }

        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if(getWidth() != 0 && getHeight() != 0 && lazyLoadUri != null){
            setImageURI(lazyLoadUri);
        }
    }

    protected boolean isGif(Uri uri){
        return uri != null && (uri.toString().endsWith(".gif")
                || uri.toString().endsWith(".GIF"));
    }

    /**
     * @description
     *
     * @param context
     * @return 得到需要分配的缓存大小，这里用八分之一的大小来做
     */
    public int getMemoryCacheSize(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int height = context.getResources().getDisplayMetrics().heightPixels;
        return width * height * 4 * 2;
    }
}
