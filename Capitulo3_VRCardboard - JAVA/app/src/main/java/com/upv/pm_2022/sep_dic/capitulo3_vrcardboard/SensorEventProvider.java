package com.upv.pm_2022.sep_dic.capitulo3_vrcardboard;

import android.hardware.*;

public interface SensorEventProvider {
    void start();
    
    void stop();
    
    void registerListener(SensorEventListener p0);
    
    void unregisterListener(SensorEventListener p0);
}
