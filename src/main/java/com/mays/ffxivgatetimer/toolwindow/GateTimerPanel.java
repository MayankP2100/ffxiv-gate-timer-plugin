package com.mays.ffxivgatetimer.toolwindow;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.mays.ffxivgatetimer.settings.GateTimerSettings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GateTimerPanel implements Disposable {
    private enum ClockDisplayMode {
        DIGITAL,
        ANALOG,
        BOTH;

        static ClockDisplayMode fromPersisted(String value) {
            return switch (value) {
                case "DIGITAL" -> DIGITAL;
                case "ANALOG" -> ANALOG;
                default -> BOTH;
            };
        }
    }

    private static final String NOTIFICATION_GROUP_ID = "FFXIV Gate Timer Notifications";
    private static final String OPTIONS_BUTTON_TEXT = "Options";
    private static final DateTimeFormatter NEXT_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final Color WARNING_COLOR = new Color(0xD32F2F);
    private static final Color ACCENT_COLOR = new Color(0x2D7D46);

    private final Project project;
    private final GateTimerSettings settings;
    private final JPanel rootPanel;
    private final Timer updateTimer;

    private JLabel digitalCountdownLabel;
    private JLabel digitalDetailLabel;
    private JLabel currentTimeLabel;
    private AnalogClockPanel analogClockPanel;
    private JPanel digitalPanel;
    private JPanel analogContainerPanel;

    private JComboBox<String> displayModeCombo;
    private JCheckBox notificationsEnabledCheck;
    private JCheckBox notifyAtStartCheck;
    private JCheckBox notifyTenMinutesCheck;
    private JCheckBox notifyFiveMinutesCheck;
    private JCheckBox notifyOneMinuteCheck;
    private JTextField customMinutesField;
    private JButton applyCustomMinutesButton;
    private JLabel customMinutesStatusLabel;
    private JSpinner warningMinutesSpinner;
    private JCheckBox soundCueCheck;
    private JCheckBox focusWindowCheck;
    private JCheckBox focusAtStartCheck;
    private JCheckBox focusBeforeCheck;
    private JSpinner focusBeforeMinutesSpinner;
    private JTextField focusWindowQueryField;
    private JButton testFocusButton;
    private JLabel focusStatusLabel;

    private Color defaultCountdownColor;
    private Instant lastObservedWindowStart;
    private long previousSecondsRemaining = -1;
    private TrayIcon trayIcon;
    private final AtomicBoolean focusRequestInProgress = new AtomicBoolean(false);

    public GateTimerPanel(Project project) {
        this.project = project;
        this.settings = GateTimerSettings.getInstance();

        rootPanel = new JPanel(new BorderLayout(0, 8));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JToggleButton optionsToggle = new JToggleButton(OPTIONS_BUTTON_TEXT);
        optionsToggle.setFocusPainted(false);
        optionsToggle.setHorizontalTextPosition(SwingConstants.LEFT);
        optionsToggle.setToolTipText("Show or hide options");

        JPanel headerPanel = buildHeader(optionsToggle);
        JPanel centerPanel = buildCenterPanel();
        JPanel optionsPanel = buildOptionsPanel();
        optionsPanel.setVisible(false);

        updateOptionsToggleIcon(optionsToggle, false);
        optionsToggle.addActionListener(event -> {
            boolean expanded = optionsToggle.isSelected();
            optionsPanel.setVisible(expanded);
            updateOptionsToggleIcon(optionsToggle, expanded);
            rootPanel.revalidate();
            rootPanel.repaint();
        });

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(centerPanel, BorderLayout.CENTER);
        rootPanel.add(optionsPanel, BorderLayout.SOUTH);

        registerSettingsListeners();
        applyDisplayModeFromSettings();
        updateFocusControlsEnabledState();

        updateTimer = new Timer(1000, event -> updateTimerText());
        updateTimer.setInitialDelay(0);
        updateTimer.start();
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    private JPanel buildHeader(JToggleButton optionsToggle) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Gate countdown");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 1f));
        titleLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(optionsToggle, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel buildCenterPanel() {
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        digitalPanel = new JPanel();
        digitalPanel.setLayout(new BoxLayout(digitalPanel, BoxLayout.Y_AXIS));
        digitalPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        digitalPanel.setOpaque(false);

        digitalCountdownLabel = new JLabel("--", SwingConstants.CENTER);
        Font base = digitalCountdownLabel.getFont();
        digitalCountdownLabel.setFont(new Font("Monospaced", Font.BOLD, Math.max(34, base.getSize() + 20)));
        defaultCountdownColor = UIManager.getColor("Label.foreground");
        if (defaultCountdownColor == null) {
            defaultCountdownColor = Color.BLACK;
        }
        digitalCountdownLabel.setAlignmentX(0.5f);
        digitalCountdownLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        digitalCountdownLabel.setOpaque(false);
        digitalCountdownLabel.setForeground(defaultCountdownColor);
        digitalPanel.add(digitalCountdownLabel);

        digitalDetailLabel = new JLabel("", SwingConstants.CENTER);
        digitalDetailLabel.setForeground(defaultCountdownColor);
        digitalDetailLabel.setAlignmentX(0.5f);
        Font smallFont = new Font(digitalDetailLabel.getFont().getName(), Font.PLAIN, digitalDetailLabel.getFont().getSize() - 2);
        digitalDetailLabel.setFont(smallFont);
        digitalPanel.add(digitalDetailLabel);

        currentTimeLabel = new JLabel("", SwingConstants.CENTER);
        currentTimeLabel.setForeground(defaultCountdownColor);
        currentTimeLabel.setAlignmentX(0.5f);
        currentTimeLabel.setFont(smallFont);
        digitalPanel.add(currentTimeLabel);

        analogContainerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        analogClockPanel = new AnalogClockPanel();
        analogContainerPanel.add(analogClockPanel);

        centerPanel.add(digitalPanel);
        centerPanel.add(analogContainerPanel);
        return centerPanel;
    }

    private JPanel buildOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        // Display section
        JPanel displayRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        displayRow.add(new JLabel("Display:"));
        displayModeCombo = new JComboBox<>(new String[]{"Digital", "Analog", "Both"});
        displayRow.add(displayModeCombo);
        panel.add(displayRow);
        panel.add(Box.createVerticalStrut(8));

        // Alerts section
        JPanel notifyMasterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        notificationsEnabledCheck = new JCheckBox("Enable alerts");
        notificationsEnabledCheck.setSelected(settings.isDesktopNotificationsEnabled());
        notifyMasterRow.add(notificationsEnabledCheck);
        panel.add(notifyMasterRow);

        JPanel notifySubPanel = new JPanel();
        notifySubPanel.setLayout(new BoxLayout(notifySubPanel, BoxLayout.Y_AXIS));
        notifySubPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        JPanel notifyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        notifyAtStartCheck = new JCheckBox("at start");
        notifyAtStartCheck.setSelected(settings.isNotifyAtStartEnabled());
        notifyRow.add(notifyAtStartCheck);
        notifyTenMinutesCheck = new JCheckBox("10min before");
        notifyTenMinutesCheck.setSelected(settings.isNotifyTenMinutesEnabled());
        notifyRow.add(notifyTenMinutesCheck);
        notifyFiveMinutesCheck = new JCheckBox("5min before");
        notifyFiveMinutesCheck.setSelected(settings.isNotifyFiveMinutesEnabled());
        notifyRow.add(notifyFiveMinutesCheck);
        notifyOneMinuteCheck = new JCheckBox("1min before");
        notifyOneMinuteCheck.setSelected(settings.isNotifyOneMinuteEnabled());
        notifyRow.add(notifyOneMinuteCheck);
        notifySubPanel.add(notifyRow);

        JPanel customLabelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        customLabelRow.add(new JLabel("Add custom reminders (in minutes):"));
        notifySubPanel.add(customLabelRow);

        JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        customMinutesField = new JTextField(14);
        customMinutesField.setText(formatCustomThresholds(settings.getCustomNotifyBeforeMinutes()));
        if (formatCustomThresholds(settings.getCustomNotifyBeforeMinutes()).isEmpty()) {
            customMinutesField.setToolTipText("e.g., 2, 7, 13");
        }
        customRow.add(customMinutesField);
        applyCustomMinutesButton = new JButton("Add");
        customRow.add(applyCustomMinutesButton);
        notifySubPanel.add(customRow);

        JPanel customStatusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        customMinutesStatusLabel = new JLabel(" ");
        Color mutedColor = UIManager.getColor("Label.disabledForeground");
        customMinutesStatusLabel.setForeground(mutedColor != null ? mutedColor : Color.GRAY);
        customStatusRow.add(customMinutesStatusLabel);
        notifySubPanel.add(customStatusRow);

        panel.add(notifySubPanel);
        panel.add(Box.createVerticalStrut(8));

        // Focus section
        JPanel focusMasterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        focusWindowCheck = new JCheckBox("Focus FFXIV window");
        focusWindowCheck.setSelected(settings.isFocusFfxivWindowEnabled());
        focusMasterRow.add(focusWindowCheck);
        panel.add(focusMasterRow);

        JPanel focusSubPanel = new JPanel();
        focusSubPanel.setLayout(new BoxLayout(focusSubPanel, BoxLayout.Y_AXIS));
        focusSubPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        JPanel focusTimingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        focusAtStartCheck = new JCheckBox("at start");
        focusAtStartCheck.setSelected(settings.isFocusAtStartEnabled());
        focusTimingRow.add(focusAtStartCheck);
        focusBeforeCheck = new JCheckBox("before");
        focusBeforeCheck.setSelected(settings.isFocusBeforeStartEnabled());
        focusTimingRow.add(focusBeforeCheck);
        focusBeforeMinutesSpinner = new JSpinner(new SpinnerNumberModel(settings.getFocusBeforeStartMinutes(), 1, 19, 1));
        focusTimingRow.add(focusBeforeMinutesSpinner);
        focusTimingRow.add(new JLabel("min"));
        focusSubPanel.add(focusTimingRow);

        JPanel focusQueryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        focusQueryRow.add(new JLabel("Window title hints:"));
        focusWindowQueryField = new JTextField(16);
        focusWindowQueryField.setText(settings.getFocusWindowTitleQuery());
        focusQueryRow.add(focusWindowQueryField);
        testFocusButton = new JButton("Test focus");
        focusQueryRow.add(testFocusButton);
        focusSubPanel.add(focusQueryRow);

        focusStatusLabel = new JLabel(" ");
        Color muted = UIManager.getColor("Label.disabledForeground");
        focusStatusLabel.setForeground(muted != null ? muted : Color.GRAY);
        focusSubPanel.add(focusStatusLabel);

        panel.add(focusSubPanel);
        panel.add(Box.createVerticalStrut(8));

        // Visual/Sound section
        JPanel visualRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        visualRow.add(new JLabel("Warning when <="));
        warningMinutesSpinner = new JSpinner(new SpinnerNumberModel(settings.getWarningThresholdMinutes(), 1, 59, 1));
        visualRow.add(warningMinutesSpinner);
        visualRow.add(new JLabel("min"));
        soundCueCheck = new JCheckBox("Sound on alert");
        soundCueCheck.setSelected(settings.isSoundCueEnabled());
        visualRow.add(soundCueCheck);
        panel.add(visualRow);

        return panel;
    }

    private void registerSettingsListeners() {
        displayModeCombo.addActionListener(event -> {
            settings.setClockDisplayMode(selectedDisplayMode().name());
            applyDisplayModeFromSettings();
            rootPanel.revalidate();
            rootPanel.repaint();
        });

        notificationsEnabledCheck.addActionListener(event -> settings.setDesktopNotificationsEnabled(notificationsEnabledCheck.isSelected()));
        notifyAtStartCheck.addActionListener(event -> settings.setNotifyAtStartEnabled(notifyAtStartCheck.isSelected()));
        notifyTenMinutesCheck.addActionListener(event -> settings.setNotifyTenMinutesEnabled(notifyTenMinutesCheck.isSelected()));
        notifyFiveMinutesCheck.addActionListener(event -> settings.setNotifyFiveMinutesEnabled(notifyFiveMinutesCheck.isSelected()));
        notifyOneMinuteCheck.addActionListener(event -> settings.setNotifyOneMinuteEnabled(notifyOneMinuteCheck.isSelected()));

        applyCustomMinutesButton.addActionListener(event -> applyCustomThresholdsFromField());
        customMinutesField.addActionListener(event -> applyCustomThresholdsFromField());

        focusWindowCheck.addActionListener(event -> {
            settings.setFocusFfxivWindowEnabled(focusWindowCheck.isSelected());
            updateFocusControlsEnabledState();
        });
        focusAtStartCheck.addActionListener(event -> settings.setFocusAtStartEnabled(focusAtStartCheck.isSelected()));
        focusBeforeCheck.addActionListener(event -> {
            settings.setFocusBeforeStartEnabled(focusBeforeCheck.isSelected());
            updateFocusControlsEnabledState();
        });
        focusBeforeMinutesSpinner.addChangeListener(event -> settings.setFocusBeforeStartMinutes((Integer) focusBeforeMinutesSpinner.getValue()));
        focusWindowQueryField.addActionListener(event -> applyFocusWindowQueryFromField());
        focusWindowQueryField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                applyFocusWindowQueryFromField();
            }
        });
        testFocusButton.addActionListener(event -> runFocusTest());

        warningMinutesSpinner.addChangeListener(event -> {
            settings.setWarningThresholdMinutes((Integer) warningMinutesSpinner.getValue());
            updateTimerText();
        });
        soundCueCheck.addActionListener(event -> settings.setSoundCueEnabled(soundCueCheck.isSelected()));
    }

    private void runFocusTest() {
        applyFocusWindowQueryFromField();
        if (!settings.isFocusFfxivWindowEnabled()) {
            setFocusStatus("Enable Focus FFXIV window first.", true);
            return;
        }

        requestFfxivFocus(true);
    }

    private void applyDisplayModeFromSettings() {
        ClockDisplayMode mode = ClockDisplayMode.fromPersisted(settings.getClockDisplayMode());
        displayModeCombo.setSelectedIndex(switch (mode) {
            case DIGITAL -> 0;
            case ANALOG -> 1;
            case BOTH -> 2;
        });

        digitalPanel.setVisible(mode == ClockDisplayMode.DIGITAL || mode == ClockDisplayMode.BOTH);
        analogContainerPanel.setVisible(mode == ClockDisplayMode.ANALOG || mode == ClockDisplayMode.BOTH);
    }

    private ClockDisplayMode selectedDisplayMode() {
        return switch (displayModeCombo.getSelectedIndex()) {
            case 0 -> ClockDisplayMode.DIGITAL;
            case 1 -> ClockDisplayMode.ANALOG;
            default -> ClockDisplayMode.BOTH;
        };
    }

    private void updateFocusControlsEnabledState() {
        boolean enabled = focusWindowCheck.isSelected();
        focusAtStartCheck.setEnabled(enabled);
        focusBeforeCheck.setEnabled(enabled);
        focusBeforeMinutesSpinner.setEnabled(enabled && focusBeforeCheck.isSelected());
        focusWindowQueryField.setEnabled(enabled);
        testFocusButton.setEnabled(enabled);
    }

    private void applyFocusWindowQueryFromField() {
        settings.setFocusWindowTitleQuery(focusWindowQueryField.getText());
        focusWindowQueryField.setText(settings.getFocusWindowTitleQuery());
    }

    private void applyCustomThresholdsFromField() {
        String raw = customMinutesField.getText();
        if (raw == null || raw.isBlank()) {
            settings.setCustomNotifyBeforeMinutes(List.of());
            customMinutesField.setText("");
            setCustomStatus("Custom reminders cleared.", false);
            return;
        }

        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        int invalidCount = 0;
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(trimmed);
                if (value >= 1 && value <= 19) {
                    values.add(value);
                } else {
                    invalidCount++;
                }
            } catch (NumberFormatException ignored) {
                invalidCount++;
            }
        }

        settings.setCustomNotifyBeforeMinutes(values);
        List<Integer> normalized = settings.getCustomNotifyBeforeMinutes();
        customMinutesField.setText(formatCustomThresholds(normalized));

        if (normalized.isEmpty()) {
            setCustomStatus("No valid custom reminders saved.", true);
            return;
        }

        if (invalidCount > 0) {
            setCustomStatus("Saved: " + formatCustomThresholds(normalized) + " (ignored " + invalidCount + ").", true);
            return;
        }

        setCustomStatus("Saved: " + formatCustomThresholds(normalized) + ".", false);
    }

    private void setCustomStatus(String message, boolean warning) {
        customMinutesStatusLabel.setText(message);
        customMinutesStatusLabel.setForeground(warning ? WARNING_COLOR : ACCENT_COLOR);
    }

    private void setFocusStatus(String message, boolean warning) {
        focusStatusLabel.setText(message);
        focusStatusLabel.setForeground(warning ? WARNING_COLOR : ACCENT_COLOR);
    }

    private static String formatCustomThresholds(List<Integer> thresholds) {
        if (thresholds.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Integer threshold : thresholds) {
            joiner.add(Integer.toString(threshold));
        }
        return joiner.toString();
    }

    private void updateTimerText() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nowLocal = nowUtc.withZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime nextGateUtc = nextGateStartUtc(nowUtc);
        ZonedDateTime nextGateLocal = nextGateUtc.withZoneSameInstant(ZoneId.systemDefault());

        long secondsRemaining = Math.max(0, Duration.between(nowUtc, nextGateUtc).getSeconds());
        maybeNotifyAlerts(nowUtc, nextGateUtc, nextGateLocal, secondsRemaining);

        boolean blinkOn = (nowUtc.getSecond() % 2) == 0;
        boolean warning = secondsRemaining <= warningThresholdSeconds();
        digitalCountdownLabel.setForeground(warning
                ? WARNING_COLOR
                : defaultCountdownColor);
        digitalCountdownLabel.setText(formatDigitalCountdown(secondsRemaining, blinkOn));
        digitalDetailLabel.setText(String.format(
                "Next gate: %s local",
                nextGateLocal.format(NEXT_TIME_FORMAT)
        ));
        currentTimeLabel.setText(String.format(
                "Current time: %s",
                nowLocal.format(NEXT_TIME_FORMAT)
        ));
        analogClockPanel.setCurrentTime(nowLocal);
        analogClockPanel.setNextGateTime(nextGateLocal);

        previousSecondsRemaining = secondsRemaining;
    }

    private void maybeNotifyAlerts(
            ZonedDateTime nowUtc,
            ZonedDateTime nextGateUtc,
            ZonedDateTime nextGateLocal,
            long secondsRemaining
    ) {
        Instant currentWindowStart = currentGateWindowStartUtc(nowUtc).toInstant();
        boolean movedToNextWindow = lastObservedWindowStart != null && currentWindowStart.isAfter(lastObservedWindowStart);
        boolean anyAlertTriggered = false;

        if (movedToNextWindow && notificationsEnabledCheck.isSelected() && notifyAtStartCheck.isSelected()) {
            anyAlertTriggered |= sendAlert("Gate started", String.format(
                    "Gate started at %s local.",
                    nowUtc.withZoneSameInstant(ZoneId.systemDefault()).format(NEXT_TIME_FORMAT)
            ));
        }

        if (notificationsEnabledCheck.isSelected()) {
            for (Integer threshold : enabledThresholds()) {
                anyAlertTriggered |= notifyBeforeThreshold(threshold, secondsRemaining, nextGateLocal, nextGateUtc);
            }
        }

        if (anyAlertTriggered && soundCueCheck.isSelected()) {
            Toolkit.getDefaultToolkit().beep();
        }

        maybeFocusFfxivWindow(movedToNextWindow, secondsRemaining);
        lastObservedWindowStart = currentWindowStart;
    }

    private List<Integer> enabledThresholds() {
        LinkedHashSet<Integer> thresholds = new LinkedHashSet<>();
        if (notifyTenMinutesCheck.isSelected()) {
            thresholds.add(10);
        }
        if (notifyFiveMinutesCheck.isSelected()) {
            thresholds.add(5);
        }
        if (notifyOneMinuteCheck.isSelected()) {
            thresholds.add(1);
        }
        thresholds.addAll(settings.getCustomNotifyBeforeMinutes());
        return new ArrayList<>(thresholds);
    }

    private boolean notifyBeforeThreshold(
            int minutesBefore,
            long secondsRemaining,
            ZonedDateTime nextGateLocal,
            ZonedDateTime nextGateUtc
    ) {
        if (!hasCrossedThreshold(minutesBefore * 60L, secondsRemaining)) {
            return false;
        }

        return sendAlert("Gate soon", String.format(
                "Gate starts in %s at %s local.",
                formatMinutes(minutesBefore),
                nextGateLocal.format(NEXT_TIME_FORMAT)
        ));
    }

    private void maybeFocusFfxivWindow(boolean movedToNextWindow, long secondsRemaining) {
        if (!focusWindowCheck.isSelected()) {
            return;
        }

        boolean shouldFocus = focusAtStartCheck.isSelected() && movedToNextWindow;
        if (focusBeforeCheck.isSelected()) {
            int minutesBefore = (Integer) focusBeforeMinutesSpinner.getValue();
            shouldFocus |= hasCrossedThreshold(minutesBefore * 60L, secondsRemaining);
        }

        if (shouldFocus) {
            requestFfxivFocus(false);
        }
    }

    private void requestFfxivFocus(boolean testMode) {
        if (!focusRequestInProgress.compareAndSet(false, true)) {
            if (testMode) {
                setFocusStatus("Focus already in progress.", true);
            }
            return;
        }

        Thread focusThread = new Thread(() -> {
            try {
                boolean focused = focusFfxivWindowBestEffort(settings.getFocusWindowTitleQuery());
                if (testMode || focused) {
                    SwingUtilities.invokeLater(() -> setFocusStatus(
                            focused ? "Focus sent to matching FFXIV window." : "No matching window was found.",
                            !focused
                    ));
                }
            } finally {
                focusRequestInProgress.set(false);
            }
        }, "ffxiv-focus-window");
        focusThread.setDaemon(true);
        focusThread.start();
    }

    private boolean hasCrossedThreshold(long thresholdSeconds, long secondsRemaining) {
        return previousSecondsRemaining > thresholdSeconds
                && secondsRemaining <= thresholdSeconds
                && secondsRemaining > 0;
    }

    private boolean sendAlert(@NlsContexts.NotificationTitle String title, @NlsContexts.NotificationContent String content) {
        boolean deliveredDesktop = showDesktopNotification(title, content);
        if (!deliveredDesktop) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(title, content, NotificationType.INFORMATION)
                    .notify(project);
        }
        return true;
    }

    private boolean focusFfxivWindowBestEffort(String titleQueryCsv) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return false;
        }

        List<String> titleHints = parseFocusWindowHints(titleQueryCsv);
        String script = buildFocusPowerShellScript(titleHints);
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    script
            ).start();

            boolean finished = process.waitFor(4, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ignored) {
            return false;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<String> parseFocusWindowHints(String titleQueryCsv) {
        ArrayList<String> hints = new ArrayList<>();
        if (titleQueryCsv == null || titleQueryCsv.isBlank()) {
            hints.add("FINAL FANTASY XIV");
            hints.add("FFXIV");
            return hints;
        }

        for (String token : titleQueryCsv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && !hints.contains(trimmed)) {
                hints.add(trimmed);
            }
        }

        if (hints.isEmpty()) {
            hints.add("FINAL FANTASY XIV");
            hints.add("FFXIV");
        }
        return hints;
    }

    private static String buildFocusPowerShellScript(List<String> titleHints) {
        StringJoiner joiner = new StringJoiner(",");
        for (String hint : titleHints) {
            joiner.add("'" + hint.replace("'", "''") + "'");
        }

        return "$proc = Get-Process -Name 'ffxiv_dx11','ffxiv' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1;"
                + "if (-not $proc) {"
                + "  $patterns = @(" + joiner + ");"
                + "  foreach ($pattern in $patterns) {"
                + "    $proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.ProcessName -notlike 'Discord*' -and $_.MainWindowTitle -like ('*' + $pattern + '*') } | Select-Object -First 1;"
                + "    if ($proc) { break }"
                + "  }"
                + "}"
                + "if ($proc) { $wshell = New-Object -ComObject WScript.Shell; $null = $wshell.AppActivate($proc.Id); exit 0 } else { exit 1 }";
    }

    private static void updateOptionsToggleIcon(JToggleButton optionsToggle, boolean expanded) {
        Icon icon = UIManager.getIcon(expanded ? "Tree.expandedIcon" : "Tree.collapsedIcon");
        optionsToggle.setIcon(icon);
        optionsToggle.setText(OPTIONS_BUTTON_TEXT);
    }

    private boolean showDesktopNotification(String title, String message) {
        if (!SystemTray.isSupported()) {
            return false;
        }

        try {
            if (trayIcon == null) {
                trayIcon = new TrayIcon(createTrayImage(), "Gate countdown");
                trayIcon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(trayIcon);
            }
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            return true;
        } catch (AWTException ignored) {
            return false;
        }
    }

    private static Image createTrayImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x4CAF50));
        graphics.fillOval(1, 1, 14, 14);
        graphics.dispose();
        return image;
    }

    private static ZonedDateTime currentGateWindowStartUtc(ZonedDateTime nowUtc) {
        ZonedDateTime minuteStart = nowUtc.withSecond(0).withNano(0);
        return minuteStart.minusMinutes(minuteStart.getMinute() % 20L);
    }

    private static ZonedDateTime nextGateStartUtc(ZonedDateTime nowUtc) {
        ZonedDateTime minuteStart = nowUtc.withSecond(0).withNano(0);
        int minutesToNextWindow = (20 - (minuteStart.getMinute() % 20)) % 20;
        ZonedDateTime next = minuteStart.plusMinutes(minutesToNextWindow);
        if (!next.isAfter(nowUtc)) {
            next = next.plusMinutes(20);
        }
        return next;
    }

    private int warningThresholdSeconds() {
        return ((Integer) warningMinutesSpinner.getValue()) * 60;
    }

    private static String formatMinutes(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainder = minutes % 60;
            if (remainder == 0) {
                return hours + "h";
            }
            return hours + "h " + remainder + "min";
        }
        return minutes + "min";
    }

    private static String formatDigitalCountdown(long secondsRemaining, boolean blinkOn) {
        if (secondsRemaining <= 0) {
            return "00:00";
        }

        long hours = secondsRemaining / 3600;
        long remainder = secondsRemaining % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;
        String separator = blinkOn ? ":" : " ";

        if (hours > 0) {
            return String.format("%d:%02d%s%02d", hours, minutes, separator, seconds);
        }
        return String.format("%02d%s%02d", minutes, separator, seconds);
    }

    @Override
    public void dispose() {
        updateTimer.stop();
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private static final class AnalogClockPanel extends JPanel {
        private ZonedDateTime currentTime = ZonedDateTime.now();
        private ZonedDateTime nextGateTime;

        AnalogClockPanel() {
            setPreferredSize(new Dimension(168, 168));
            setMinimumSize(new Dimension(140, 140));
            setOpaque(false);
        }

        void setCurrentTime(ZonedDateTime currentTime) {
            this.currentTime = currentTime;
            repaint();
        }

        void setNextGateTime(ZonedDateTime nextGateTime) {
            this.nextGateTime = nextGateTime;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 12;
            int radius = size / 2;
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;

            g2.setColor(new Color(0xD9D9D9));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(centerX - radius, centerY - radius, size, size);

            g2.setColor(new Color(0x8A8A8A));
            for (int minute = 0; minute < 60; minute += 5) {
                double angle = Math.toRadians(minute * 6 - 90);
                int x1 = (int) (centerX + Math.cos(angle) * (radius - 8));
                int y1 = (int) (centerY + Math.sin(angle) * (radius - 8));
                int x2 = (int) (centerX + Math.cos(angle) * (radius - 2));
                int y2 = (int) (centerY + Math.sin(angle) * (radius - 2));
                g2.drawLine(x1, y1, x2, y2);
            }

            if (nextGateTime != null) {
                int nextGateMinute = nextGateTime.getMinute();
                double nextGateAngle = Math.toRadians(nextGateMinute * 6 - 90);
                g2.setColor(new Color(0x22C55E));
                g2.setStroke(new BasicStroke(3f));
                int x1 = (int) (centerX + Math.cos(nextGateAngle) * (radius - 10));
                int y1 = (int) (centerY + Math.sin(nextGateAngle) * (radius - 10));
                int x2 = (int) (centerX + Math.cos(nextGateAngle) * (radius));
                int y2 = (int) (centerY + Math.sin(nextGateAngle) * (radius));
                g2.drawLine(x1, y1, x2, y2);
            }

            int hour = currentTime.getHour() % 12;
            int minute = currentTime.getMinute();
            int second = currentTime.getSecond();

            double hourAngle = Math.toRadians((hour + (minute / 60.0)) * 30 - 90);
            double minuteAngle = Math.toRadians(minute * 6 - 90);
            double secondAngle = Math.toRadians(second * 6 - 90);

            g2.setColor(new Color(0x3A3A3A));
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(
                    centerX,
                    centerY,
                    (int) (centerX + Math.cos(hourAngle) * (radius * 0.5)),
                    (int) (centerY + Math.sin(hourAngle) * (radius * 0.5))
            );

            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(
                    centerX,
                    centerY,
                    (int) (centerX + Math.cos(minuteAngle) * (radius * 0.75)),
                    (int) (centerY + Math.sin(minuteAngle) * (radius * 0.75))
            );

            g2.setColor(new Color(0xDC2626));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(
                    centerX,
                    centerY,
                    (int) (centerX + Math.cos(secondAngle) * (radius * 0.85)),
                    (int) (centerY + Math.sin(secondAngle) * (radius * 0.85))
            );

            g2.setColor(new Color(0x2D7D46));
            g2.fillOval(centerX - 3, centerY - 3, 6, 6);
            g2.dispose();
        }
    }
}
