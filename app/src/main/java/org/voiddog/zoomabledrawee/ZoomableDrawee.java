package org.voiddog.zoomabledrawee;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.image.ImageInfo;

/**
 * 文件描述
 *
 * @author qigengxin
 * @since 2016-11-14 14:44
 */


public class ZoomableDrawee extends SimpleDraweeView{
    /**
     * 原图高度
     */
    private int imgWidth;
    /**
     * 原图宽度
     */
    private int imgHeight;

    /**
     * 最后一次更新的时间
     */
    private long lastComputeTime;
    /**
     * 图片缩放类型
     */
    private ScalingUtils.ScaleType scaleType;

    /**
     * 原先的controller监听器
     */
    private ControllerListener srcControllerListener;

    public ZoomableDrawee(Context context) {
        super(context);
        init(context, null);
    }

    public ZoomableDrawee(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ZoomableDrawee(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs){
        scaleType = getHierarchy().getActualImageScaleType();
        getHierarchy().setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER);
        zoomableGestureHelper.setEnabled(false);
    }

    public void setControllerListener(ControllerListener controllerListener) {
        this.srcControllerListener = controllerListener;
    }

    public ControllerListener getZoomableControllerListener(){
        return controllerListener;
    }

    @Override
    public void setImageURI(Uri uri, Object callerContext) {
        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setCallerContext(callerContext)
                .setUri(uri)
                .setControllerListener(controllerListener)
                .setOldController(getController())
                .build();
        setController(draweeController);
    }

    /**
     *
     * @param scaleType 支持以下类型参数
     *                  centerCrop 保持宽高比缩小或放大，使得两边都大于或等于显示边界。居中显示
     *                  fitCenter 保持宽高比，缩小或者放大，使得图片完全显示在显示边界内。居中显示
     */
    public void setActualImageScaleType(ScalingUtils.ScaleType scaleType){
        this.scaleType = scaleType;
        invalidate();
    }

    public void setOnTapListener(ZoomableGestureHelper.OnSingleTapListener tapListener){
        if(zoomableGestureHelper != null){
            zoomableGestureHelper.setSingleTapListener(tapListener);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        lastComputeTime = SystemClock.uptimeMillis();
        return zoomableGestureHelper.processTouchEvent(event) || super.onTouchEvent(event);
    }

    Rect drawRect = new Rect();
    private RectF tmpRectF = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        int count = canvas.save();
        if(zoomableGestureHelper != null && zoomableGestureHelper.getZoomMatrix() != null) {
            canvas.concat(zoomableGestureHelper.getZoomMatrix());
        }
        super.onDraw(canvas);
        canvas.restoreToCount(count);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        reset();
    }

    @Override
    public void computeScroll() {
        if(compute()){
            postInvalidate();
        }
    }

    /**
     * 计算下一帧
     * @return
     */
    protected boolean compute(){
        boolean res = false;
        long currentTime = SystemClock.uptimeMillis();
        if(zoomableGestureHelper.compute((currentTime - lastComputeTime) / 1000.0f)){
            res = true;
        }
        lastComputeTime = currentTime;
        return res;
    }

    /**
     * 重置
     */
    private void reset(){
        lastComputeTime = SystemClock.uptimeMillis();
        zoomableGestureHelper.reset();
    }

    private ControllerListener controllerListener = new BaseControllerListener<ImageInfo>(){
        @Override
        public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
            if(srcControllerListener != null){
                srcControllerListener.onFinalImageSet(id, imageInfo, animatable);
            }
            if(imageInfo == null){
                return;
            }
            imgWidth = imageInfo.getWidth();
            imgHeight = imageInfo.getHeight();
            zoomableGestureHelper.setEnabled(true);
            reset();
        }

        @Override
        public void onSubmit(String id, Object callerContext) {
            if(srcControllerListener != null){
                srcControllerListener.onSubmit(id, callerContext);
            }
            super.onSubmit(id, callerContext);
        }

        @Override
        public void onIntermediateImageSet(String id, ImageInfo imageInfo) {
            if(srcControllerListener != null){
                srcControllerListener.onIntermediateImageSet(id, imageInfo);
            }
            super.onIntermediateImageSet(id, imageInfo);
        }

        @Override
        public void onIntermediateImageFailed(String id, Throwable throwable) {
            if(srcControllerListener != null){
                srcControllerListener.onIntermediateImageFailed(id, throwable);
            }
            super.onIntermediateImageFailed(id, throwable);
        }

        @Override
        public void onFailure(String id, Throwable throwable) {
            if(srcControllerListener != null){
                srcControllerListener.onFailure(id, throwable);
            }
            super.onFailure(id, throwable);
        }

        @Override
        public void onRelease(String id) {
            if(srcControllerListener != null){
                srcControllerListener.onRelease(id);
            }
            super.onRelease(id);
        }
    };

    protected ZoomableGestureHelper zoomableGestureHelper = new ZoomableGestureHelper(getContext()) {
        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if(getParent() instanceof ViewGroup){
                ViewGroup viewGroup = (ViewGroup) getParent();
                viewGroup.requestDisallowInterceptTouchEvent(disallow);
            }
        }

        @Override
        public Rect getBounds(Rect rect) {
            rect.left = 0;
            rect.right = getWidth();
            rect.top = 0;
            rect.bottom = getHeight();
            return rect;
        }

        @Override
        public Rect getInnerBounds(Rect rect) {
            Drawable topDrawable = getTopLevelDrawable();
            if(topDrawable == null){
                rect.set(0, 0, 0, 0);
                return rect;
            }

            getInnerVisibleBounds(rect);

            // 防止缩放变为0
            rect.right = rect.right == rect.left ? rect.left + 1 : rect.right;
            rect.bottom = rect.bottom == rect.top ? rect.top + 1 : rect.bottom;
            return rect;
        }

        @Override
        public void invalidate() {
            ZoomableDrawee.this.invalidate();
        }
    };

    public Drawable getTopLevelDrawable(){
        return getHierarchy() == null ? null : getHierarchy().getTopLevelDrawable();
    }

    public Rect getInnerVisibleBounds(Rect rect){
        getInnerOriginBounds(rect);
        tmpRectF.set(rect);
        zoomableGestureHelper.getZoomMatrix().mapRect(tmpRectF);
        tmpRectF.round(rect);
        return rect;
    }

    public Rect getInnerOriginBounds(Rect rect){
        int width = imgWidth == 0 ? getWidth() : imgWidth;
        int height = imgHeight == 0 ? getHeight() : imgHeight;
        rect.set(0, 0, 0, 0);
        if(width == 0 || height == 0){
            return rect;
        }

        if(scaleType == ScalingUtils.ScaleType.CENTER_CROP) {
            float scale = Math.max(getWidth() * 1.0f / width, getHeight() * 1.0f / height);
            width *= scale;
            height *= scale;
        } else {
            float ratio = width * 1.0f / height;
            width = (int) Math.min(getWidth(), getHeight() * ratio);
            height = (int) Math.min(getHeight(), getWidth() / ratio);
        }
        int l, t;
        l = (getWidth() - width) >> 1;
        t = (getHeight() - height) >> 1;
        rect.set(l, t, l + width, t + height);
        return rect;
    }
}
