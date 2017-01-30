package org.voiddog.zoomabledrawee;

import android.app.Application;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * 缩放手势帮助类
 *
 * @author qigengxin
 * @since 2017-01-11 14:26
 */


public abstract class ZoomableGestureHelper {
    // 用于 sgn 函数
    private static final double EPS = 1e-4;
    // 每一步计算的时间间隔
    private static final double SOLVER_TIMESTEP_SEC = 0.001;
    // 最大的计算时间间隔 dt
    private static final double MAX_DELTA_TIME_SEC = 0.064;
    // reset 位置 0.05 dp/s 0.05dp
    private static final double MIN_RESET_VELOCITY = 0.05;
    private static final double MIN_RESET_POSITION = 0.05;
    // 缩放开始速度
    private static final double AUTO_SCALE_VELOCITY = 10;

    // 系数常数
    // 滑动时候的摩擦力
    private static final double FLING_FRICTION = 1;
    private static final double FRICTION = 12;
    private static final double TENSION = 80;

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void setSingleTapListener(OnSingleTapListener singleTapListener) {
        this.singleTapListener = singleTapListener;
    }

    public interface OnSingleTapListener{
        boolean onSingleTap(MotionEvent event);
    }

    private Context appContext;

    /**
     * 各种手势
     */
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    /**
     * 缩放矩阵
     */
    private Matrix zooomMatrix;
    /**
     * 视图边界和内部边界
     */
    private Rect bounds, innerBounds;
    /**
     * 是否属于手势拖动中
     */
    private boolean isDrag;
    /**
     * 是否处于enable状态
     */
    private boolean isEnabled = true;
    private SpringPhysicsState xPhysicsState, yPhysicsState, scalePhysicsState;

    /**
     * 自动缩放 缩放点
     */
    private boolean autoScale = false;
    private double autoScaleCenterX, autoScaleCenterY;

    /**
     * 时间间隔累计器
     */
    private double timeAccumulator = 0;

    // viewpager 在 requestDisallowTouch 的时候 开始为true 中途返回false，此时会crash,
    // 原因为我多指头的时候，如果index为0的手指抬起来了，但是index为1的手指还在，此时disallow设置为false，会导致
    // viewpager的onIntercept去获取index为0的手指
    private boolean eatRequestDisallow = false;

    /**
     * single tap 监听器
     */
    private OnSingleTapListener singleTapListener;

    public ZoomableGestureHelper(Context context){
        init(context);
    }

    /**
     * 获取缩放的矩阵
     * @return
     */
    public Matrix getZoomMatrix() {
        return zooomMatrix;
    }

    public boolean processTouchEvent(MotionEvent event){
        if (!isEnabled){
            return false;
        }

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                isDrag = true;
                eatRequestDisallow = false;
                clearPhysicsState();
                requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                isDrag = false;
                requestDisallowInterceptTouchEvent(false);
                invalidate();
                break;
            default:
                eatRequestDisallow = true;
                break;
        }
        boolean res = gestureDetector.onTouchEvent(event);
        res |= scaleGestureDetector.onTouchEvent(event);
        return res;
    }

    /**
     * 重置所有状态，包括矩阵缩放所保存的数值
     */
    public void reset(){
        clearPhysicsState();
        zooomMatrix.reset();
    }

    public boolean compute(double realDeltaTime){
        if(isDrag || !isEnabled){
            return false;
        }

        double adjustTime = realDeltaTime;
        if (adjustTime > MAX_DELTA_TIME_SEC){
            adjustTime = MAX_DELTA_TIME_SEC;
        }

        // 更新 bounds 信息
        getBounds(bounds);
        getInnerBounds(innerBounds);

        if(getWidth(bounds) == 0 || getWidth(innerBounds) == 0
                || getHeight(bounds) == 0 || getHeight(innerBounds) == 0){
            // 有一个为0则不进行操作
            return false;
        }

        // 计时
        timeAccumulator += adjustTime;

        /**
         * 以下移动的计算
         */
        double xPosition = 0, xEndPosition = 0;
        double yPosition = 0, yEndPosition = 0;
        xPhysicsState.setFriction(FRICTION * getDensity());
        yPhysicsState.setFriction(FRICTION * getDensity());

        // 计算x轴方向
        if (getWidth(bounds) > getWidth(innerBounds)){
            xPosition = (innerBounds.right + innerBounds.left) / 2.0f;
            xEndPosition = (bounds.right + bounds.left) / 2.0f;
        } else {
            if (innerBounds.left > bounds.left){
                xPosition = innerBounds.left;
                xEndPosition = bounds.left;
            } else if (innerBounds.right < bounds.right){
                xPosition = innerBounds.right;
                xEndPosition = bounds.right;
            } else {
                xPhysicsState.setFriction(FLING_FRICTION * getDensity());
            }
        }

        // 计算y轴方向
        if (getHeight(bounds) > getHeight(innerBounds)){
            yPosition = (innerBounds.bottom + innerBounds.top) / 2.0f;
            yEndPosition = (bounds.top + bounds.bottom) / 2.0f;
        } else {
            if (innerBounds.top > bounds.top){
                yPosition = innerBounds.top;
                yEndPosition = bounds.top;
            } else if (innerBounds.bottom < bounds.bottom){
                yPosition = innerBounds.bottom;
                yEndPosition = bounds.bottom;
            } else {
                yPhysicsState.setFriction(FLING_FRICTION * getDensity());
            }
        }

        double newXPosition = xPosition;
        double newYPosition = yPosition;
        int whileTimes = 0;

        while(timeAccumulator >= SOLVER_TIMESTEP_SEC){
            ++whileTimes;
            timeAccumulator -= SOLVER_TIMESTEP_SEC;
            newXPosition = xPhysicsState.computeNextPosition(newXPosition, xEndPosition, SOLVER_TIMESTEP_SEC);
            newYPosition = yPhysicsState.computeNextPosition(newYPosition, yEndPosition, SOLVER_TIMESTEP_SEC);
        }

        if(isAtReset(xPhysicsState, newXPosition - xEndPosition)){
            newXPosition = xEndPosition;
            xPhysicsState.setVelocity(0);
        }

        if(isAtReset(yPhysicsState, newYPosition - yEndPosition)){
            newYPosition = yEndPosition;
            yPhysicsState.setVelocity(0);
        }

        zooomMatrix.postTranslate((float) (newXPosition - xPosition), (float) (newYPosition - yPosition));

        /**
         * 以下缩放的计算
         */
        double scale = Math.max(getWidth(innerBounds) * 1.0 / getWidth(bounds), getHeight(innerBounds) * 1.0 / getHeight(bounds));
        double endScale = 1, startScale = scale;
        if(scale >= 1){
            endScale = scale;
        }

        while(whileTimes --> 0){
            scale = scalePhysicsState.computeNextPosition(scale, endScale, SOLVER_TIMESTEP_SEC);
        }

        if(isAtReset(scalePhysicsState, (scale - endScale) * getDensity() * 10)){
            scale = endScale;
            scalePhysicsState.setVelocity(0);
        }

        if(sgn(scale - startScale) != 0) {
            double x = autoScale ? autoScaleCenterX : (innerBounds.right + innerBounds.left) / 2.0;
            double y = autoScale ? autoScaleCenterY : (innerBounds.bottom + innerBounds.top) / 2.0;
            zooomMatrix.postScale((float)(scale / startScale), (float)(scale / startScale),
                    (float)x, (float)y);
        }

        return sgn(newXPosition - xEndPosition) != 0 || sgn(xPhysicsState.getVelocity()) != 0
                || sgn(newYPosition - yEndPosition) != 0 || sgn(yPhysicsState.getVelocity()) != 0
                || sgn(scale - startScale) != 0;
    }

    public abstract void requestDisallowInterceptTouchEvent(boolean disallow);

    /**
     * 获取到外部容器的范围
     * @return
     */
    public abstract Rect getBounds(Rect rect);

    /**
     * 内部滚动视图的范围
     * @return
     */
    public abstract Rect getInnerBounds(Rect rect);

    /**
     * 刷新界面
     */
    public abstract void invalidate();

    private boolean isAtReset(SpringPhysicsState physicsState, double positionDis){
        return Math.abs(physicsState.getVelocity()) < getDensity() * MIN_RESET_VELOCITY &&
                (Math.abs(positionDis) < getDensity() * MIN_RESET_POSITION || sgn(physicsState.getTension()) == 0);
    }

    private double tmp_density = -1;
    private double getDensity(){
        if(tmp_density == -1){
            tmp_density = appContext.getResources().getDisplayMetrics().density;
        }
        return tmp_density;
    }

    private int sgn(double x){
        return (x > -EPS ? 1 : 0) - (x < EPS ? 1 : 0);
    }

    /**
     * 让各个物理状态变为0
     */
    private void clearPhysicsState(){
        xPhysicsState.setVelocity(0);
        yPhysicsState.setVelocity(0);
        scalePhysicsState.setVelocity(0);
        autoScale = false;
    }

    /**
     * 初始化
     * @param context
     */
    private void init(Context context){
        if(context instanceof Application){
            appContext = context;
        } else {
            appContext = context.getApplicationContext();
        }
        gestureDetector = new GestureDetector(null, gestureListener);
        gestureDetector.setOnDoubleTapListener(doubleTapListener);
        scaleGestureDetector = new ScaleGestureDetector(appContext, scaleGestureListener);

        bounds = new Rect();
        innerBounds = new Rect();
        zooomMatrix = new Matrix();

        double tension = TENSION * getDensity(), friction = FRICTION * getDensity();
        xPhysicsState = new SpringPhysicsState(tension, friction);
        yPhysicsState = new SpringPhysicsState(tension, friction);
        scalePhysicsState = new SpringPhysicsState(TENSION, FRICTION);
    }

    private GestureDetector.OnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener(){

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

            getBounds(bounds);
            getInnerBounds(innerBounds);

            // 计算如果超过了边界
            if((innerBounds.left >= bounds.left && distanceX < 0)){
                distanceX /= 2;
                requestDisallowInterceptTouchEvent(eatRequestDisallow);
            } else if (innerBounds.right <= bounds.right && distanceX > 0){
                distanceX /= 2;
                requestDisallowInterceptTouchEvent(eatRequestDisallow);
            }

            if (innerBounds.top >= bounds.top && distanceY < 0){
                distanceY /= 2;
            } else if (innerBounds.bottom <= bounds.bottom && distanceY > 0){
                distanceY /= 2;
            }

            zooomMatrix.postTranslate(-distanceX, -distanceY);

            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            xPhysicsState.setVelocity(velocityX);
            yPhysicsState.setVelocity(velocityY);
            return true;
        }
    };

    private GestureDetector.OnDoubleTapListener doubleTapListener = new GestureDetector.OnDoubleTapListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if(singleTapListener != null){
                return singleTapListener.onSingleTap(e);
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            autoScaleCenterX = e.getX();
            autoScaleCenterY = e.getY();
            autoScale = true;

            getBounds(bounds);
            getInnerBounds(innerBounds);

            double scale = Math.max(getWidth(innerBounds) * 1.0 / getWidth(bounds), getHeight(innerBounds) * 1.0f / getHeight(bounds));
            if(scale > 3){
                scalePhysicsState.setVelocity(-3*AUTO_SCALE_VELOCITY);
            } else {
                scalePhysicsState.setVelocity(AUTO_SCALE_VELOCITY);
            }
            invalidate();

            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return true;
        }
    };

    private int getWidth(Rect rect){
        if(rect == null){
            return 0;
        }
        return rect.right - rect.left;
    }

    private int getHeight(Rect rect){
        if(rect == null){
            return 0;
        }

        return rect.bottom - rect.top;
    }

    private ScaleGestureDetector.OnScaleGestureListener scaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zooomMatrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {}
    };
}
