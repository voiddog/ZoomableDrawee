package org.voiddog.zoomabledrawee;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;
import android.util.SparseArray;

import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.BaseDataSubscriber;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.core.DefaultExecutorSupplier;
import com.facebook.imagepipeline.memory.PooledByteBuffer;
import com.facebook.imagepipeline.memory.PooledByteBufferInputStream;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.imageutils.BitmapUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;

/**
 * 未解码的图片帮助
 *
 * @author qigengxin
 * @since 2017-01-22 14:50
 */


public abstract class EncodeBitmapHelper {
    /**
     * 位图分割大小
     */
    public final int BITMAP_SEG_SIZE;

    /**
     * 当前加载的图片
     */
    private Uri currentUri;

    /**
     * 位图缓存
     */
    private LruSparseCache<Bitmap> bitmapCache;

    /**
     * 未解码的图片
     */
    private CountDownRef<CloseableReference<PooledByteBuffer>> encodeImageRef;

    /**
     * 线程池的提供者
     */
    private DefaultExecutorSupplier executorSupplier;

    /**
     * 原图大小
     */
    private Pair<Integer, Integer> originBitmapSize;

    private SparseArray<DecodeRunnable> decodeRunnableSparseArray;

    private boolean isAttached = false;

    public EncodeBitmapHelper(int bitmapSegSize, long maxSize) {
        BITMAP_SEG_SIZE = bitmapSegSize;
        bitmapCache = new LruSparseCache<>(maxSize);
        executorSupplier = new DefaultExecutorSupplier(getNumberOfCPUCores());
        decodeRunnableSparseArray = new SparseArray<>();
        isAttached = false;
    }

    public synchronized void setUri(Uri uri){
        if(uri == null || uri.equals(currentUri)){
            return;
        }

        currentUri = uri;
        clearAllData();
        attachOrDetach();
    }

    public boolean isLoadingData(){
        return !isAttached || encodeImageRef == null || originBitmapSize == null;
    }

    public int getScaleExp(int key){
        return key >>> 27;
    }

    public int getId(int key) {
        return key & 0x07ffffff;
    }

    public int getKey(int scaleExp, int id){
        return (scaleExp << 27) | (id & 0x07ffffff);
    }

    public Bitmap getBitmap(int key){
        return bitmapCache.get(key);
    }

    public void addDecodeRegion(int key){
        if(isLoadingData() || decodeRunnableSparseArray.get(key) != null){
            // 加载数据中 或者 已近在解码队列中
            return;
        }

        int scaleExp = getScaleExp(key);
        int id = getId(key);
        int bitmapSize = BITMAP_SEG_SIZE << scaleExp;
        int column = getOriginWidth() / bitmapSize;
        column += getOriginWidth() % bitmapSize == 0 ? 0 : 1;
        int left = (id % column) * bitmapSize, top = (id / column) * bitmapSize;
        Rect decodeRect = new Rect(left, top, left + bitmapSize, top + bitmapSize);
        if(left >= getOriginWidth() || top >= getOriginHeight()){
            System.out.println("bad error");
            return;
        }
        DecodeRunnable decodeRunnable = new DecodeRunnable(key, encodeImageRef, decodeRect, scaleExp, currentUri);
        decodeRunnableSparseArray.append(key, decodeRunnable);
        executorSupplier.forDecode().execute(decodeRunnable);
    }

    public int getBitmapSegSize(int scaleExp){
        return BITMAP_SEG_SIZE << scaleExp;
    }

    public synchronized Uri getCurrentUri(){
        return currentUri;
    }

    public void attachToWindow(){
        if(isAttached){
            return;
        }
        isAttached = true;
        attachOrDetach();
    }

    public void detachFromWindow(){
        if(!isAttached){
            return;
        }
        isAttached = false;
        attachOrDetach();
    }

    protected void clearAllData(){
        decodeRunnableSparseArray.clear();
        bitmapCache.clear();
        if(encodeImageRef != null){
            encodeImageRef.release();
            encodeImageRef = null;
        }
    }

    public synchronized int getOriginWidth(){
        return originBitmapSize == null ? 0 : originBitmapSize.first;
    }

    public synchronized int getOriginHeight(){
        return originBitmapSize == null ? 0 : originBitmapSize.second;
    }

    protected abstract void onBitmapUpdate();

    private synchronized void attachOrDetach(){
        if(!isAttached || currentUri == null){
            clearAllData();
            return;
        }

        // 获取未解码的图片
        ImageRequest encodeRequest = ImageRequestBuilder.newBuilderWithSource(currentUri)
                .setRotationOptions(RotationOptions.autoRotate())
                .build();
        DataSource<CloseableReference<PooledByteBuffer>> dataSource = Fresco.getImagePipeline().fetchEncodedImage(encodeRequest, this);
        dataSource.subscribe(new EncodeDataSubscriber(currentUri), executorSupplier.forBackgroundTasks());
    }

    private static int sCpuSize = 0;

    /**
     * 获取 cpu 的个数
     * @return
     */
    private static int getNumberOfCPUCores() {
        if(sCpuSize != 0){
            return sCpuSize;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
            return sCpuSize = 1;
        }
        try {
            sCpuSize = new File("/sys/devices/system/cpu/").listFiles(CPU_FILTER).length;
        } catch (SecurityException e) {
            sCpuSize = 1;
        } catch (NullPointerException e) {
            sCpuSize = 1;
        }
        return sCpuSize;
    }

    private static final FileFilter CPU_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            String path = pathname.getName();
            //regex is slow, so checking char by char.
            if (path.startsWith("cpu")) {
                for (int i = 3; i < path.length(); i++) {
                    if (path.charAt(i) < '0' || path.charAt(i) > '9') {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    };

    private class EncodeDataSubscriber extends BaseDataSubscriber<CloseableReference<PooledByteBuffer>>{

        private Uri encodeUri;

        public EncodeDataSubscriber(Uri uri){
            encodeUri = uri;
        }

        @Override
        protected void onNewResultImpl(DataSource<CloseableReference<PooledByteBuffer>> dataSource) {
            if(!dataSource.isFinished()){
                return;
            }

            synchronized (EncodeBitmapHelper.this) {
                if (encodeUri.equals(currentUri)) {
                    encodeImageRef = new CountDownRef<CloseableReference<PooledByteBuffer>>(dataSource.getResult()){
                        @Override
                        protected void releaseData(CloseableReference<PooledByteBuffer> value) {
                            System.out.println("release: " + encodeUri);
                            CloseableReference.closeSafely(value);
                        }
                    };
                    try {
                        InputStream is = new PooledByteBufferInputStream(encodeImageRef.get().get());
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        originBitmapSize = BitmapUtil.decodeDimensions(is);
                    } catch (Exception ignore){}
                }
            }

            onBitmapUpdate();
        }

        @Override
        protected void onFailureImpl(DataSource<CloseableReference<PooledByteBuffer>> dataSource) {}
    }

    private class DecodeRunnable implements Runnable{

        private int key;

        private CountDownRef<CloseableReference<PooledByteBuffer>> encodeRef;

        private Rect decodeRect;

        private int decodeScaleExp;

        private Uri uri;

        private DecodeRunnable(int key, CountDownRef<CloseableReference<PooledByteBuffer>> encodeRef, Rect decodeRect, int decodeScaleExp, Uri uri) {
            this.key = key;
            this.encodeRef = encodeRef;
            this.decodeRect = decodeRect;
            this.decodeScaleExp = decodeScaleExp;
            this.uri = uri;
        }

        @Override
        public void run() {
            synchronized (EncodeBitmapHelper.this){
                if(!isCurrentDecode()){
                    return;
                }
                encodeRef.retian();
            }

            Bitmap res = null;
            try {
                InputStream is;
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inSampleSize = 1 << decodeScaleExp;
                is = new PooledByteBufferInputStream(encodeRef.get().get());
                decodeRect.right = decodeRect.right >= getOriginWidth() ? getOriginWidth()-1 : decodeRect.right;
                decodeRect.bottom = decodeRect.bottom >= getOriginHeight() ? getOriginHeight()-1 : decodeRect.bottom;
                res = BitmapRegionDecoder.newInstance(is, true).decodeRegion(decodeRect, op);
            } catch (IOException e) {
                e.printStackTrace();
            }

            synchronized (EncodeBitmapHelper.this){
                decodeRunnableSparseArray.remove(key);
                if(isCurrentDecode()){
                    bitmapCache.put(key, res);
                }
                encodeRef.release();
            }

            onBitmapUpdate();
        }

        /**
         * 判断是否是当前需要的解码结果
         * @return
         */
        private boolean isCurrentDecode(){
            return uri.equals(currentUri) && isAttached;
        }
    }
}
