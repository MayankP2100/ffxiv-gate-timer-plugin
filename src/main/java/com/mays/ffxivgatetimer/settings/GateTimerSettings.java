package com.mays.ffxivgatetimer.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

@Service(Service.Level.APP)
@State(name = "GateTimerSettings", storages = @Storage("ffxiv-gate-timer.xml"))
public final class GateTimerSettings implements PersistentStateComponent<GateTimerSettings.State> {
    public static final class State {
        public boolean desktopNotificationsEnabled = false;
        public boolean notifyAtStartEnabled = true;
        public boolean notifyTenMinutesEnabled = false;
        public boolean notifyFiveMinutesEnabled = false;
        public boolean notifyOneMinuteEnabled = false;
        public String customNotifyBeforeMinutesCsv = "";
        public int warningThresholdSeconds = 60;
        public boolean soundCueEnabled = false;
        public String clockDisplayMode = "BOTH";
        public boolean focusFfxivWindowEnabled = false;
        public boolean focusAtStartEnabled = false;
        public boolean focusBeforeStartEnabled = false;
        public int focusBeforeStartMinutes = 1;
        public String focusWindowTitleQuery = "FINAL FANTASY XIV, FFXIV";
    }

    private State state = new State();

    public static GateTimerSettings getInstance() {
        return ApplicationManager.getApplication().getService(GateTimerSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        sanitize();
    }

    public boolean isDesktopNotificationsEnabled() {
        return state.desktopNotificationsEnabled;
    }

    public void setDesktopNotificationsEnabled(boolean desktopNotificationsEnabled) {
        state.desktopNotificationsEnabled = desktopNotificationsEnabled;
    }

    public boolean isNotifyAtStartEnabled() {
        return state.notifyAtStartEnabled;
    }

    public void setNotifyAtStartEnabled(boolean notifyAtStartEnabled) {
        state.notifyAtStartEnabled = notifyAtStartEnabled;
    }

    public boolean isNotifyTenMinutesEnabled() {
        return state.notifyTenMinutesEnabled;
    }

    public void setNotifyTenMinutesEnabled(boolean notifyTenMinutesEnabled) {
        state.notifyTenMinutesEnabled = notifyTenMinutesEnabled;
    }

    public boolean isNotifyFiveMinutesEnabled() {
        return state.notifyFiveMinutesEnabled;
    }

    public void setNotifyFiveMinutesEnabled(boolean notifyFiveMinutesEnabled) {
        state.notifyFiveMinutesEnabled = notifyFiveMinutesEnabled;
    }

    public boolean isNotifyOneMinuteEnabled() {
        return state.notifyOneMinuteEnabled;
    }

    public void setNotifyOneMinuteEnabled(boolean notifyOneMinuteEnabled) {
        state.notifyOneMinuteEnabled = notifyOneMinuteEnabled;
    }

    public @NotNull List<Integer> getCustomNotifyBeforeMinutes() {
        return parseThresholds(state.customNotifyBeforeMinutesCsv);
    }

    public void setCustomNotifyBeforeMinutes(@NotNull Collection<Integer> customNotifyBeforeMinutes) {
        state.customNotifyBeforeMinutesCsv = serializeThresholds(customNotifyBeforeMinutes);
    }

    public int getWarningThresholdSeconds() {
        return clampWarningThreshold(state.warningThresholdSeconds);
    }

    public void setWarningThresholdSeconds(int warningThresholdSeconds) {
        state.warningThresholdSeconds = clampWarningThreshold(warningThresholdSeconds);
    }

    public int getWarningThresholdMinutes() {
        int seconds = clampWarningThreshold(state.warningThresholdSeconds);
        return Math.max(1, Math.min(59, (int) Math.ceil(seconds / 60.0)));
    }

    public void setWarningThresholdMinutes(int warningThresholdMinutes) {
        int minutes = Math.max(1, Math.min(59, warningThresholdMinutes));
        state.warningThresholdSeconds = minutes * 60;
    }

    public boolean isSoundCueEnabled() {
        return state.soundCueEnabled;
    }

    public void setSoundCueEnabled(boolean soundCueEnabled) {
        state.soundCueEnabled = soundCueEnabled;
    }

    public boolean isFocusFfxivWindowEnabled() {
        return state.focusFfxivWindowEnabled;
    }

    public void setFocusFfxivWindowEnabled(boolean focusFfxivWindowEnabled) {
        state.focusFfxivWindowEnabled = focusFfxivWindowEnabled;
    }

    public boolean isFocusAtStartEnabled() {
        return state.focusAtStartEnabled;
    }

    public void setFocusAtStartEnabled(boolean focusAtStartEnabled) {
        state.focusAtStartEnabled = focusAtStartEnabled;
    }

    public boolean isFocusBeforeStartEnabled() {
        return state.focusBeforeStartEnabled;
    }

    public void setFocusBeforeStartEnabled(boolean focusBeforeStartEnabled) {
        state.focusBeforeStartEnabled = focusBeforeStartEnabled;
    }

    public int getFocusBeforeStartMinutes() {
        return clampFocusBeforeMinutes(state.focusBeforeStartMinutes);
    }

    public void setFocusBeforeStartMinutes(int focusBeforeStartMinutes) {
        state.focusBeforeStartMinutes = clampFocusBeforeMinutes(focusBeforeStartMinutes);
    }

    public @NotNull String getFocusWindowTitleQuery() {
        return sanitizeFocusWindowTitleQuery(state.focusWindowTitleQuery);
    }

    public void setFocusWindowTitleQuery(@Nullable String focusWindowTitleQuery) {
        state.focusWindowTitleQuery = sanitizeFocusWindowTitleQuery(focusWindowTitleQuery);
    }

    public @NotNull String getClockDisplayMode() {
        return sanitizeClockDisplayMode(state.clockDisplayMode);
    }

    public void setClockDisplayMode(@Nullable String clockDisplayMode) {
        state.clockDisplayMode = sanitizeClockDisplayMode(clockDisplayMode);
    }

    private void sanitize() {
        state.customNotifyBeforeMinutesCsv = serializeThresholds(parseThresholds(state.customNotifyBeforeMinutesCsv));
        state.warningThresholdSeconds = clampWarningThreshold(state.warningThresholdSeconds);
        state.clockDisplayMode = sanitizeClockDisplayMode(state.clockDisplayMode);
        state.focusBeforeStartMinutes = clampFocusBeforeMinutes(state.focusBeforeStartMinutes);
        state.focusWindowTitleQuery = sanitizeFocusWindowTitleQuery(state.focusWindowTitleQuery);
    }

    private static int clampWarningThreshold(int value) {
        return Math.max(10, Math.min(300, value));
    }

    private static int clampFocusBeforeMinutes(int value) {
        return Math.max(1, Math.min(19, value));
    }

    private static @NotNull String sanitizeFocusWindowTitleQuery(@Nullable String value) {
        if (value == null) {
            return "FINAL FANTASY XIV, FFXIV";
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? "FINAL FANTASY XIV, FFXIV" : trimmed;
    }

    private static @NotNull String sanitizeClockDisplayMode(@Nullable String value) {
        if (value == null) {
            return "BOTH";
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "DIGITAL", "ANALOG", "BOTH" -> normalized;
            default -> "BOTH";
        };
    }

    private static List<Integer> parseThresholds(@Nullable String csv) {
        ArrayList<Integer> thresholds = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return thresholds;
        }

        for (String raw : csv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }

            try {
                int value = Integer.parseInt(token);
                if (value >= 1 && value <= 19 && !thresholds.contains(value)) {
                    thresholds.add(value);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid entries.
            }
        }

        thresholds.sort(Integer::compareTo);
        return thresholds;
    }

    private static String serializeThresholds(@NotNull Collection<Integer> thresholds) {
        ArrayList<Integer> normalized = new ArrayList<>();
        for (Integer threshold : thresholds) {
            if (threshold == null) {
                continue;
            }
            int value = threshold;
            if (value >= 1 && value <= 19 && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        normalized.sort(Integer::compareTo);

        StringJoiner joiner = new StringJoiner(",");
        for (Integer value : normalized) {
            joiner.add(Integer.toString(value));
        }
        return joiner.toString();
    }
}
