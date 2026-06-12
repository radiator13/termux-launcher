package com.termux.app.terminal.unexpected.vendor;

public final class Config {

    private static Config sGlobalConfig = new Config();

    public float swipe_dist_px = 48f;
    public float slide_step_px = 24f;
    public long longPressTimeout = 500L;
    public long longPressInterval = 60L;
    public boolean keyrepeat_enabled = true;
    public boolean double_tap_lock_shift = true;
    public int circle_sensitivity = 2;

    public static Config globalConfig() {
        return sGlobalConfig;
    }

    public static void setGlobalConfig(Config config) {
        sGlobalConfig = config == null ? new Config() : config;
    }
}
