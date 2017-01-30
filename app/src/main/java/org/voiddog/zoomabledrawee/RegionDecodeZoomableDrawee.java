package org.voiddog.zoomabledrawee;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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

    private EncodeBitmapHelper encodeBitmapHelper = new EncodeBitmapHelper(dp2px(150), 20 << 20) {
        @Override
        protected void onBitmapUpdate() {
            System.out.println("postInvalidate");
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
            lazyLoadUri = null;
            super.setImageURI(uri);
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
        testPaint.setAlpha(150);
    }

    protected int dp2px(double dp){
        return (int) (getContext().getResources().getDisplayMetrics().density * dp);
    }

    Rect tmpRect = new Rect();

    RectF tmpRectF = new RectF();

    Paint testPaint = new Paint();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(encodeBitmapHelper.isLoadingData()){
            return;
        }

        getInnerVisibleBounds(tmpRect);
        if(tmpRect.left == tmpRect.right || tmpRect.top == tmpRect.bottom){
            return;
        }

        if(Math.max((tmpRect.right - tmpRect.left) / getWidth(), (tmpRect.bottom - tmpRect.top) / getHeight()) <= 2){
            return;
        }

        if(tmpRect.left >= getWidth() || tmpRect.right <= 0 || tmpRect.top >= getHeight() || tmpRect.bottom <= 0){
            return;
        }

        int originWidth = encodeBitmapHelper.getOriginWidth();
        float scale = originWidth * 1.0f / (tmpRect.right - tmpRect.left);
        int scaleExp = (int) Math.log(scale < 1 ? 1 : scale);
        int originRegionBitmapSize = encodeBitmapHelper.BITMAP_SEG_SIZE << scaleExp;
        float drawBitmapSize = originRegionBitmapSize / scale;

        int column = originWidth / originRegionBitmapSize;
        column += originWidth % originRegionBitmapSize == 0 ? 0 : 1;
        int startX = (int)(Math.max(0, -tmpRect.left) * scale) / originRegionBitmapSize;
        int startY = (int)(Math.max(0, -tmpRect.top) * scale) / originRegionBitmapSize;
        int endX = (int)(Math.min(tmpRect.right - tmpRect.left, getWidth() - tmpRect.left) * scale) / originRegionBitmapSize;
        int endY = (int)(Math.min(tmpRect.bottom - tmpRect.top, getHeight() - tmpRect.top) * scale) / originRegionBitmapSize;
        int x = startX, y = startY;
        float visibleBitmapScale = drawBitmapSize / encodeBitmapHelper.BITMAP_SEG_SIZE;

        while (y <= endY){
            int id = y * column + x;
            int key = encodeBitmapHelper.getKey(scaleExp, id);
            Bitmap drawRegionBitmap = encodeBitmapHelper.getBitmap(key);
            if(drawRegionBitmap == null){
                encodeBitmapHelper.addDecodeRegion(key);
            } else {
                int left = x * originRegionBitmapSize, top = originRegionBitmapSize * y;
                tmpRectF.left = left / scale + tmpRect.left;
                tmpRectF.top = top / scale + tmpRect.top;
                tmpRectF.right = tmpRectF.left + drawRegionBitmap.getWidth() * visibleBitmapScale;
                tmpRectF.bottom = tmpRectF.top + drawRegionBitmap.getHeight() * visibleBitmapScale;
                canvas.drawBitmap(drawRegionBitmap, null, tmpRectF, testPaint);
            }
            ++x;
            if(x > endX){
                x = startX;
                ++y;
            }
        }
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
}
