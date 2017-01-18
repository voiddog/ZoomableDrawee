package org.voiddog.zoomabledrawee;

/**
 * 模仿现实中的物理弹簧效果
 * 力的公式
 * F = tension * dx - friction * v
 * 其中 tension 为弹性系数，friction 为摩擦力系数，dx 为距离目的地的位移，v 为速度
 * 公式的近似计算采用四阶龙格库塔近似计算
 *
 * @author qigengxin
 * @since 2017-01-18 14:22
 */

public class SpringPhysicsState {
    // 速度
    private double velocity;
    // 拉力弹性系数
    private double tension;
    // 摩擦力系数
    private double friction;

    // ------------------------------- 构造函数 ---------------------------------

    /**
     * 默认数值 tension = 40, friction = 12;
     */
    public SpringPhysicsState(){
        init(40, 12);
    }

    public SpringPhysicsState(double tension, double friction){
        init(tension, friction);
    }

    public double computeNextPosition(double startPosition, double endPosition, double dt){
        double position;

        double tempPosition, tempVelocity;
        double aVelocity, aAcceleration;
        double bVelocity, bAcceleration;
        double cVelocity, cAcceleration;
        double dVelocity, dAcceleration;

        position = startPosition;
        tempPosition = startPosition;

        // 龙格库塔 4阶
        aVelocity = velocity;
        aAcceleration = (tension * (endPosition - tempPosition)) - friction * velocity;

        tempPosition = position + aVelocity * dt * 0.5f;
        tempVelocity = velocity + aAcceleration * dt * 0.5f;
        bVelocity = tempVelocity;
        bAcceleration = (tension * (endPosition - tempPosition)) - friction * tempVelocity;

        tempPosition = position + bVelocity * dt * 0.5f;
        tempVelocity = velocity + bAcceleration * dt * 0.5f;
        cVelocity = tempVelocity;
        cAcceleration = (tension * (endPosition - tempPosition)) - friction * tempVelocity;

        tempPosition = position + cVelocity * dt;
        tempVelocity = velocity + cAcceleration * dt;
        dVelocity = tempVelocity;
        dAcceleration = (tension * (endPosition - tempPosition)) - friction * tempVelocity;

        // Take the weighted sum of the 4 derivatives as the final output.
        double dxdt = 1.0f/6.0f * (aVelocity + 2.0f * (bVelocity + cVelocity) + dVelocity);
        double dvdt = 1.0f/6.0f * (aAcceleration + 2.0f * (bAcceleration + cAcceleration) + dAcceleration);

        position += dxdt * dt;
        velocity += dvdt * dt;

        return position;
    }

    // ------------------------------- setter and getter ---------------------------------

    public double getVelocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = velocity;
    }

    public double getTension() {
        return tension;
    }

    public void setTension(double tension) {
        this.tension = tension;
    }

    public double getFriction() {
        return friction;
    }

    public void setFriction(double friction) {
        this.friction = friction;
    }

    // ------------------------------- 私有函数 ---------------------------------

    private void init(double tension, double friction){
        this.velocity = 0;
        this.friction = friction;
        this.tension = tension;
    }

}
