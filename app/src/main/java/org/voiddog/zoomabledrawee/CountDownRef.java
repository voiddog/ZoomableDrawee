package org.voiddog.zoomabledrawee;

/**
 * 模拟引用计数
 *
 * @author qigengxin
 * @since 2017-01-22 11:08
 */


public class CountDownRef<T> {
    /**
     * 计数器
     */
    int count;

    /**
     * 需要清理的数值
     */
    T valueRef;

    /**
     * 创建后引用计数为1
     * @param valueRef
     */
    public CountDownRef(T valueRef){
        this.valueRef = valueRef;
        count = 1;
    }

    public synchronized T get(){
        return valueRef;
    }

    public synchronized void retian(){
        if(count == 0){
            throw new IllegalArgumentException("ref has been released");
        }

        count++;
    }

    public synchronized void release(){
        if(count == 0){
            throw new IllegalArgumentException("ref has been released");
        }

        count--;

        if (count == 0){
            releaseData(valueRef);
        }
    }

    public boolean isReleased(){
        return count == 0;
    }

    protected void releaseData(T value){}
}
