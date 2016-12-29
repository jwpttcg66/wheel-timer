package com.snowcattle.timer;

/**
 * Created by jiangwenping on 16/12/29.
 * 监听器
 */
public interface ExpirationListener<E> {

    void expired(E expireObject);
}
