package com.ayquza.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;

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
    private static final long UPDATE_INTERVAL = 500; // Update every 500ms

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
        if (lastClipboardContent.isEmpty()) {
            String text = showTitle.get() ? "Clipboard: (empty)" : "(empty)";
            setSize(renderer.textWidth(text) + border.get() * 2, renderer.textHeight() + border.get() * 2);
            return;
        }

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
        } else {
            String singleLine = displayContent.replace("\n", " ").replace("\r", "");
            if (showTitle.get()) {
                setSize(renderer.textWidth("Clipboard: " + singleLine) + border.get() * 2, renderer.textHeight() + border.get() * 2);
            } else {
                setSize(renderer.textWidth(singleLine) + border.get() * 2, renderer.textHeight() + border.get() * 2);
            }
        }
    }

    @Override
    public void render(HudRenderer renderer) {
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

        // Background rendern falls aktiviert
        if (background.get()) {
            renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
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
        if (currentTime - lastUpdate < UPDATE_INTERVAL) {
            return;
        }
        lastUpdate = currentTime;

        try {
            // Check if we're in a headless environment
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                lastClipboardContent = "(Headless environment)";
                return;
            }

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard != null && clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                String content = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (content != null) {
                    lastClipboardContent = content;
                } else {
                    lastClipboardContent = "";
                }
            } else {
                lastClipboardContent = "(Non-text content)";
            }
        } catch (java.awt.HeadlessException e) {
            lastClipboardContent = "(No GUI environment)";
            System.err.println("[ClipboardHUD] HeadlessException: Running without GUI");
        } catch (IllegalStateException e) {
            // Clipboard wird von anderem Prozess verwendet
            lastClipboardContent = "(Clipboard busy)";
        } catch (Exception e) {
            // Detaillierte Fehlermeldung für Debugging
            lastClipboardContent = "(Error: " + e.getClass().getSimpleName() + ")";
            System.err.println("[ClipboardHUD] Error accessing clipboard: " + e.getMessage());
        }
    }

    private String truncateContent(String content) {
        if (content.length() <= maxLength.get()) {
            return content;
        }
        return content.substring(0, maxLength.get() - 3) + "...";
    }

}
