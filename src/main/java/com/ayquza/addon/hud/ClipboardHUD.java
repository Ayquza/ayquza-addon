package com.ayquza.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class ClipboardHUD extends HudElement {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");

    // Settings
    private final Setting<Integer> maxLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-length")
        .description("Maximum number of characters to display.")
        .defaultValue(50)
        .min(1)
        .max(200)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Shows 'Clipboard:' before the content.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color for the title.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(showTitle::get)
        .build()
    );

    private final Setting<SettingColor> contentColor = sgGeneral.add(new ColorSetting.Builder()
        .name("content-color")
        .description("Color for the clipboard content.")
        .defaultValue(new SettingColor(175, 175, 175))
        .build()
    );

    private final Setting<Boolean> multiLine = sgGeneral.add(new BoolSetting.Builder()
        .name("multi-line")
        .description("Shows multi-line text in separate lines.")
        .defaultValue(false)
        .build()
    );

    private String lastClipboardContent = "";
    private long lastUpdate = 0;
    private static final long UPDATE_INTERVAL = 1000; // Erhöht auf 1000ms um Spam zu reduzieren
    private boolean clipboardError = false;
    private long lastErrorTime = 0;
    private static final long ERROR_COOLDOWN = 5000; // 5 Sekunden Cooldown nach Fehler

    // Background Settings
    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the text.")
        .defaultValue(2)
        .min(0)
        .max(10)
        .build()
    );

    public static final HudElementInfo<ClipboardHUD> INFO = new HudElementInfo<>(Hud.GROUP, "clipboard", "Shows the current clipboard content.", ClipboardHUD::new);

    public ClipboardHUD() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        updateClipboard();
        calculateSize(renderer);
    }

    private void calculateSize(HudRenderer renderer) {
        String displayText;

        if (clipboardError) {
            displayText = showTitle.get() ? "Clipboard: (non-text content)" : "(non-text content)";
        } else if (lastClipboardContent.isEmpty()) {
            displayText = showTitle.get() ? "Clipboard: (empty)" : "(empty)";
        } else {
            String displayContent = truncateContent(lastClipboardContent);

            if (multiLine.get() && displayContent.contains("\n")) {
                String[] lines = displayContent.split("\n");
                double maxWidth = 0;
                int lineCount = lines.length;

                if (showTitle.get()) {
                    maxWidth = Math.max(maxWidth, renderer.textWidth("Clipboard:"));
                    lineCount++;
                }

                for (String line : lines) {
                    maxWidth = Math.max(maxWidth, renderer.textWidth(line));
                }

                setSize(maxWidth + border.get() * 2, renderer.textHeight() * lineCount + border.get() * 2);
                return;
            } else {
                String singleLine = displayContent.replace("\n", " ").replace("\r", "");
                displayText = showTitle.get() ? "Clipboard: " + singleLine : singleLine;
            }
        }

        setSize(renderer.textWidth(displayText) + border.get() * 2, renderer.textHeight() + border.get() * 2);
    }

    @Override
    public void render(HudRenderer renderer) {
        // Background rendern falls aktiviert
        if (background.get()) {
            renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
        }

        if (clipboardError) {
            if (showTitle.get()) {
                renderer.text("Clipboard: (non-text content)", x + border.get(), y + border.get(), titleColor.get(), true);
            } else {
                renderer.text("(non-text content)", x + border.get(), y + border.get(), contentColor.get(), true);
            }
            return;
        }

        if (lastClipboardContent.isEmpty()) {
            if (showTitle.get()) {
                renderer.text("Clipboard: (empty)", x + border.get(), y + border.get(), titleColor.get(), true);
            } else {
                renderer.text("(empty)", x + border.get(), y + border.get(), contentColor.get(), true);
            }
            return;
        }

        String displayContent = truncateContent(lastClipboardContent);

        if (multiLine.get() && displayContent.contains("\n")) {
            renderMultiLine(renderer, displayContent);
        } else {
            renderSingleLine(renderer, displayContent.replace("\n", " ").replace("\r", ""));
        }
    }

    private void renderText(HudRenderer renderer, String text, SettingColor color, double x, double y) {
        renderer.text(text, x + border.get(), y + border.get(), color, true);
    }

    private void renderSingleLine(HudRenderer renderer, String content) {
        if (showTitle.get()) {
            renderText(renderer, "Clipboard: ", titleColor.get(), x, y);
            double titleWidth = renderer.textWidth("Clipboard: ");
            renderText(renderer, content, contentColor.get(), x + titleWidth, y);
        } else {
            renderText(renderer, content, contentColor.get(), x, y);
        }
    }

    private void renderMultiLine(HudRenderer renderer, String content) {
        String[] lines = content.split("\n");
        double yOffset = 0;

        if (showTitle.get()) {
            renderText(renderer, "Clipboard:", titleColor.get(), x, y + yOffset);
            yOffset += renderer.textHeight();
        }

        for (String line : lines) {
            renderText(renderer, line, contentColor.get(), x, y + yOffset);
            yOffset += renderer.textHeight();
        }
    }

    private void updateClipboard() {
        long currentTime = System.currentTimeMillis();

        // Skip update wenn wir vor kurzem einen Fehler hatten
        if (clipboardError && currentTime - lastErrorTime < ERROR_COOLDOWN) {
            return;
        }

        // Normal Update-Intervall prüfen
        if (currentTime - lastUpdate < UPDATE_INTERVAL) {
            return;
        }
        lastUpdate = currentTime;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getWindow() == null) {
                lastClipboardContent = "";
                clipboardError = false;
                return;
            }

            // Erst prüfen ob überhaupt Text in der Zwischenablage ist
            long windowHandle = mc.getWindow().getHandle();

            // Vorsichtig auf Clipboard zugreifen
            String clipboardContent = null;
            try {
                clipboardContent = GLFW.glfwGetClipboardString(windowHandle);
            } catch (Exception e) {
                // Clipboard enthält wahrscheinlich Nicht-Text-Daten
                clipboardError = true;
                lastErrorTime = currentTime;
                return;
            }

            if (clipboardContent != null) {
                lastClipboardContent = clipboardContent;
                clipboardError = false;
            } else {
                // Null bedeutet meist Nicht-Text-Daten in Zwischenablage
                clipboardError = true;
                lastErrorTime = currentTime;
            }

        } catch (Exception e) {
            clipboardError = true;
            lastErrorTime = currentTime;
            // Keine Console-Ausgabe mehr für bekannte Clipboard-Probleme
        }
    }

    private String truncateContent(String content) {
        if (content.length() <= maxLength.get()) {
            return content;
        }
        return content.substring(0, maxLength.get() - 3) + "...";
    }
}
