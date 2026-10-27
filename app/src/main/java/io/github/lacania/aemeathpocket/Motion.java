package io.github.lacania.aemeathpocket;

/** 与 Android 无关的定位约束；横竖屏切换时按可活动区域恢复位置。 */
public final class Motion {
    private Motion() {}
    public static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, Math.max(low, high)));
    }
    public static int restore(float fraction, int low, int high) {
        if (!Float.isFinite(fraction)) fraction = .5f;
        return clamp(Math.round(low + Math.max(0f, Math.min(1f, fraction)) * Math.max(0, high - low)), low, high);
    }
    public static float fraction(int value, int low, int high) {
        if (high <= low) return .5f;
        return Math.max(0f, Math.min(1f, (value - low) / (float) (high - low)));
    }
}
