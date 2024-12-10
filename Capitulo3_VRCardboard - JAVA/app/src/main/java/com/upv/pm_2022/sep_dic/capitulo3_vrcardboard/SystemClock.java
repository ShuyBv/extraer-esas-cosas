package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

public class SystemClock implements Clock {
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
