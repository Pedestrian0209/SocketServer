package com.zk.server;

/**
 * Created by dell on 2017/9/18.
 */

public class PointItem {
    public int event;
    public int x;
    public int y;

    public PointItem(int event, int x, int y) {
        this.event = event;
        this.x = x;
        this.y = y;
    }

    public String toString() {
        return "PointItem event = " + event + " x = " + x + " y = " + y;
    }
}
