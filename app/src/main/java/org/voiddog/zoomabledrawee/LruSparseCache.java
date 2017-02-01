package org.voiddog.zoomabledrawee;

import android.graphics.Bitmap;
import android.util.SparseArray;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Key为int的 Lru queueCache
 *
 * @author qigengxin
 * @since 2017-01-22 10:02
 */


public class LruSparseCache<T> {

    private static class InnerSparseStruct<T>{
        int lastAccessTime;
        int key;
        T value;
    }

    /**
     * 下一次使用的时间
     */
    private int nextTimeCount = 0;

    /**
     * 最大缓存大小，byte count
     */
    private long maxSize;

    /**
     * 当前缓存占用大小
     */
    private long size;

    /**
     * key 到 value 的映射
     */
    private SparseArray<InnerSparseStruct<T>> keyToValue;

    /**
     *
     */
    private SparseArray<InnerSparseStruct<T>> timeToCountValue;

    public LruSparseCache(long maxSize){
        if(maxSize <= 0){
            throw new IllegalArgumentException("queueCache size must be positive");
        }

        this.maxSize = maxSize;
        keyToValue = new SparseArray<>();
        timeToCountValue = new SparseArray<>();
    }

    public synchronized void put(int key, T value){
        InnerSparseStruct<T> innerValue = keyToValue.get(key);
        T preValue = null;
        if(innerValue != null){
            timeToCountValue.remove(innerValue.lastAccessTime);
            preValue = innerValue.value;
        } else {
            innerValue = new InnerSparseStruct<>();
        }
        innerValue.value = value;
        innerValue.key = key;
        innerValue.lastAccessTime = nextTimeCount++;

        if(preValue != null){
            size -= sizeOf(key, preValue);
        }

        if(value == null){
            keyToValue.remove(key);
            return;
        }

        size += sizeOf(key, value);
        keyToValue.put(key, innerValue);
        timeToCountValue.put(innerValue.lastAccessTime, innerValue);

        trimToSize(maxSize);
    }

    public synchronized void clear(){
        keyToValue.clear();
        timeToCountValue.clear();
        nextTimeCount = 0;
        size = 0;
    }

    public synchronized T get(int key){
        InnerSparseStruct<T> ret = keyToValue.get(key);
        if(ret != null){
            timeToCountValue.remove(ret.lastAccessTime);
            ret.lastAccessTime = nextTimeCount++;
            timeToCountValue.append(ret.lastAccessTime, ret);
            return ret.value;
        }
        return null;
    }

    public synchronized void remove(int key){
        InnerSparseStruct<T> remove = keyToValue.get(key);
        if (remove != null){
            size -= sizeOf(key, remove.value);
            keyToValue.remove(key);
            timeToCountValue.remove(remove.lastAccessTime);
        }

        trimToSize(maxSize);
    }

    protected int sizeOf(int key, T value){
        if(value == null){
            return 0;
        }

        if(value instanceof Bitmap){
            System.out.println("size of bitmap: " + ((Bitmap) value).getWidth() + ", " + ((Bitmap) value).getByteCount());
            return ((Bitmap) value).getByteCount();
        }

        return 0;
    }

    private synchronized void trimToSize(long maxSize){
        for(;;){
            if (size < 0 || (keyToValue.size() == 0 && size != 0)){
                throw new IllegalArgumentException(".sizeOf() is reporting inconsistent results!");
            }

            if(keyToValue.size() != timeToCountValue.size()){
                throw new IllegalArgumentException("the size of two sparseArray not same");
            }

            if (size < maxSize || keyToValue.size() == 0){
                break;
            }

            System.out.println("value count: " + keyToValue.size() + ", total size: " + size);

            InnerSparseStruct<T> lastValue = timeToCountValue.valueAt(0);
            size -= sizeOf(lastValue.key, lastValue.value);
            keyToValue.remove(lastValue.key);
            timeToCountValue.remove(lastValue.lastAccessTime);
            System.out.println("release id: " + (lastValue.key & 0x07ffffff) + ", release value size: " + sizeOf(lastValue.key, lastValue.value));
        }
    }
}
