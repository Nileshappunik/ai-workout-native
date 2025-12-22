package com.aiworkout.nativeplugin;

import com.getcapacitor.Logger;

public class AIWorkoutNative {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
