package com.replaymod.replay.gui.screen;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.mojang.realmsclient.gui.ChatFormatting;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.SettingsRegistry;
import com.replaymod.core.gui.GuiReplaySettings;
import com.replaymod.core.utils.Utils;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.Setting;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;
import de.johni0702.minecraft.gui.container.AbstractGuiContainer;
import de.johni0702.minecraft.gui.container.GuiContainer;
import de.johni0702.minecraft.gui.container.GuiPanel;
import de.johni0702.minecraft.gui.container.GuiScreen;
import de.johni0702.minecraft.gui.element.*;
import de.johni0702.minecraft.gui.element.advanced.GuiResourceLoadingList;
import de.johni0702.minecraft.gui.function.Typeable;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.layout.GridLayout;
import de.johni0702.minecraft.gui.layout.HorizontalLayout;
import de.johni0702.minecraft.gui.layout.VerticalLayout;
import de.johni0702.minecraft.gui.popup.GuiYesNoPopup;
import de.johni0702.minecraft.gui.utils.Colors;
import de.johni0702.minecraft.gui.utils.Consumer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.Dimension;
import org.lwjgl.util.ReadableDimension;
import org.lwjgl.util.ReadablePoint;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.replaymod.replay.ReplayModReplay.LOGGER;

public class GuiReplayViewer extends GuiScreen implements Typeable {
    private final ReplayModReplay mod;

    public final GuiResourceLoadingList<GuiReplayEntry> list = new GuiResourceLoadingList<GuiReplayEntry>(this).onSelectionChanged(new Runnable() {
        @Override
        public void run() {
			// RAH - added below line, commented out other lines
			replayButtonPanel.forEach(IGuiButton.class).setEnabled();
            //replayButtonPanel.forEach(IGuiButton.class).setEnabled(list.getSelected() != null);
            //if (list.getSelected() != null && list.getSelected().incompatible) {
            //    loadButton.setDisabled();
            //}
        }
    }).onLoad(new Consumer<Consumer<Supplier<GuiReplayEntry>>> () {
        @Override
        public void consume(Consumer<Supplier<GuiReplayEntry>> obj) {
            try {
                File folder = mod.getCore().getReplayFolder();
                for (final File file : folder.listFiles((FileFilter) new SuffixFileFilter(".mcpr", IOCase.INSENSITIVE))) {
                    if (Thread.interrupted()) break;

                    try (ReplayFile replayFile = new ZipReplayFile(new ReplayStudio(), file)) {

                        Optional<BufferedImage> thumb = replayFile.getThumb();
                        // Make sure that to int[] conversion doesn't have to occur in main thread
                        final BufferedImage theThumb;
                        if (thumb.isPresent()) {
                            BufferedImage buf = thumb.get();
                            // This is the same way minecraft calls this method, we cache the result and hand
                            // minecraft a BufferedImage with way simpler logic using the precomputed values
                            final int[] theIntArray = buf.getRGB(0, 0, buf.getWidth(), buf.getHeight(), null, 0, buf.getWidth());
                            theThumb = new BufferedImage(buf.getWidth(), buf.getHeight(), BufferedImage.TYPE_INT_ARGB) {
                                @Override
                                public int[] getRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
                                    System.arraycopy(theIntArray, 0, rgbArray, 0, theIntArray.length);
                                    return null; // Minecraft doesn't use the return value
                                }
                            };
                        } else {
                            theThumb = null;
                        }
                        final ReplayMetaData metaData = replayFile.getMetaData();

                        if (metaData != null) {
                            obj.consume(() -> new GuiReplayEntry(file, metaData, theThumb));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Could not load Replay File {}", file.getName(), e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }).onSelectionDoubleClicked(() -> {
        if (this.loadButton.isEnabled()) {
            this.loadButton.onClick();
            // Disable load button to prevent the player from opening the replay twice at the same time
            this.loadButton.setDisabled();
        }
    }).setDrawShadow(true).setDrawSlider(true);

    public final GuiButton loadButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
			LogManager.getLogger().debug("Called loadButton");
            try {
                mod.startReplay(list.getSelected().file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }).setSize(73, 20).setI18nLabel("replaymod.gui.load").setDisabled();

    public final GuiButton folderButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
            try {
                File folder = mod.getCore().getReplayFolder();
                String path = folder.getAbsolutePath();

                // First try OS specific methods
                try {
                    switch (Util.getOSType()) {
                        case WINDOWS:
                            Runtime.getRuntime().exec(String.format("cmd.exe /C start \"Open file\" \"%s\"", path));
                            return;
                        case OSX:
                            Runtime.getRuntime().exec(new String[]{"/usr/bin/open", path});
                            return;
                    }
                } catch (IOException e) {
                    LogManager.getLogger().error("Cannot open file", e);
                }

                // Otherwise try to java way
                try {
                    Desktop.getDesktop().browse(folder.toURI());
                } catch (Throwable throwable) {
                    // And if all fails, lwjgl
                    Sys.openURL("file://" + path);
                }
            } catch (IOException e) {
                mod.getLogger().error("Cannot open file", e);
            }
        }
    }).setSize(150, 20).setI18nLabel("replaymod.gui.viewer.replayfolder");

    public final GuiButton renameButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
            final File file = list.getSelected().file;
            String name = Utils.fileNameToReplayName(file.getName());
            final GuiTextField nameField = new GuiTextField().setSize(200, 20).setFocused(true).setText(name);
            final GuiYesNoPopup popup = GuiYesNoPopup.open(GuiReplayViewer.this,
                    new GuiLabel().setI18nText("replaymod.gui.viewer.rename.name").setColor(Colors.BLACK),
                    nameField
            ).setYesI18nLabel("replaymod.gui.rename").setNoI18nLabel("replaymod.gui.cancel");
            ((VerticalLayout) popup.getInfo().getLayout()).setSpacing(7);
            nameField.onEnter(new Runnable() {
                @Override
                public void run() {
                    if (popup.getYesButton().isEnabled()) {
                        popup.getYesButton().onClick();
                    }
                }
            }).onTextChanged(obj -> {
                popup.getYesButton().setEnabled(!nameField.getText().isEmpty()
                        && !new File(file.getParentFile(), Utils.replayNameToFileName(nameField.getText())).exists());
            });
            Futures.addCallback(popup.getFuture(), new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean delete) {
                    if (delete) {
                        // Sanitize their input
                        String name = nameField.getText().trim();
                        // This file is what they want
                        File targetFile = new File(file.getParentFile(), Utils.replayNameToFileName(name));
                        try {
                            // Finally, try to move it
                            FileUtils.moveFile(file, targetFile);
                        } catch (IOException e) {
                            // We failed (might also be their OS)
                            e.printStackTrace();
                            getMinecraft().displayGuiScreen(new GuiErrorScreen(
                                    I18n.format("replaymod.gui.viewer.delete.failed1"),
                                    I18n.format("replaymod.gui.viewer.delete.failed2")
                            ));
                            return;
                        }
                        list.load();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }).setSize(73, 20).setI18nLabel("replaymod.gui.rename").setDisabled();
    public final GuiButton deleteButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
            String name = list.getSelected().name.getText();
            GuiYesNoPopup popup = GuiYesNoPopup.open(GuiReplayViewer.this,
                    new GuiLabel().setI18nText("replaymod.gui.viewer.delete.linea").setColor(Colors.BLACK),
                    new GuiLabel().setI18nText("replaymod.gui.viewer.delete.lineb", name + ChatFormatting.RESET).setColor(Colors.BLACK)
            ).setYesI18nLabel("replaymod.gui.delete").setNoI18nLabel("replaymod.gui.cancel");
            Futures.addCallback(popup.getFuture(), new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean delete) {
                    if (delete) {
                        try {
                            FileUtils.forceDelete(list.getSelected().file);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        list.load();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }).setSize(73, 20).setI18nLabel("replaymod.gui.delete").setDisabled();

    public final GuiButton settingsButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
            new GuiReplaySettings(toMinecraft(), mod.getCore().getSettingsRegistry()).display();
        }
    }).setSize(73, 20).setI18nLabel("replaymod.gui.settings");

    public final GuiButton cancelButton = new GuiButton().onClick(new Runnable() {
        @Override
        public void run() {
            getMinecraft().displayGuiScreen(null);
        }
    }).setSize(73, 20).setI18nLabel("replaymod.gui.cancel");

    public final GuiPanel replayButtonPanel = new GuiPanel().setLayout(new GridLayout().setSpacingX(5).setSpacingY(5)
            .setColumns(2)).addElements(null, loadButton, new GuiPanel() /* Upload */, renameButton, deleteButton);
    public final GuiPanel generalButtonPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(5))
            .addElements(null, folderButton, new GuiPanel().setLayout(new HorizontalLayout().setSpacing(5))
                    .addElements(null, settingsButton, cancelButton));
    public final GuiPanel buttonPanel = new GuiPanel(this).setLayout(new HorizontalLayout().setSpacing(6))
            .addElements(null, replayButtonPanel, generalButtonPanel);

    public GuiReplayViewer(ReplayModReplay mod) {
        this.mod = mod;

        setTitle(new GuiLabel().setI18nText("replaymod.gui.replayviewer"));

        setLayout(new CustomLayout<GuiScreen>() {
            @Override
            protected void layout(GuiScreen container, int width, int height) {
                pos(buttonPanel, width / 2 - width(buttonPanel) / 2, height - 10 - height(buttonPanel));

                pos(list, 0, 30);
                size(list, width, y(buttonPanel) - 10 - y(list));
            }
        });
    }

    private final GuiImage defaultThumbnail = new GuiImage().setTexture(Utils.DEFAULT_THUMBNAIL);

    @Override
    public boolean typeKey(ReadablePoint mousePosition, int keyCode, char keyChar, boolean ctrlDown, boolean shiftDown) {
        if (keyCode == Keyboard.KEY_F1) {
            SettingsRegistry reg = ReplayMod.instance.getSettingsRegistry();
            reg.set(Setting.SHOW_SERVER_IPS, !reg.get(Setting.SHOW_SERVER_IPS));
            reg.save();
            list.load();
        }
        return false;
    }

    public class GuiReplayEntry extends AbstractGuiContainer<GuiReplayEntry> implements Comparable<GuiReplayEntry> {
        public final File file;
        public final GuiLabel name = new GuiLabel();
        public final GuiLabel server = new GuiLabel().setColor(Colors.LIGHT_GRAY);
        public final GuiLabel date = new GuiLabel().setColor(Colors.LIGHT_GRAY);
        public final GuiPanel infoPanel = new GuiPanel(this).setLayout(new VerticalLayout().setSpacing(2))
                .addElements(null, name, server, date);
        public final GuiLabel version = new GuiLabel(this).setColor(Colors.RED);
        public final GuiImage thumbnail;
        public final GuiLabel duration = new GuiLabel();
        public final GuiPanel durationPanel = new GuiPanel().setBackgroundColor(Colors.HALF_TRANSPARENT)
                .addElements(null, duration).setLayout(new CustomLayout<GuiPanel>() {
                    @Override
                    protected void layout(GuiPanel container, int width, int height) {
                        pos(duration, 2, 2);
                    }

                    @Override
                    public ReadableDimension calcMinSize(GuiContainer<?> container) {
                        ReadableDimension dimension = duration.calcMinSize();
                        return new Dimension(dimension.getWidth() + 2, dimension.getHeight() + 2);
                    }
                });

        private final long dateMillis;
        private final boolean incompatible;

        public GuiReplayEntry(File file, ReplayMetaData metaData, BufferedImage thumbImage) {
            this.file = file;

            name.setText(ChatFormatting.UNDERLINE + Utils.fileNameToReplayName(file.getName()));
            if (StringUtils.isEmpty(metaData.getServerName())
                    || !ReplayMod.instance.getSettingsRegistry().get(Setting.SHOW_SERVER_IPS)) {
                server.setI18nText("replaymod.gui.iphidden").setColor(Colors.DARK_RED);
            } else {
                server.setText(metaData.getServerName());
            }
            incompatible = !new ReplayStudio().isCompatible(metaData.getFileFormatVersion());
            if (incompatible) {
                version.setText("Minecraft " + metaData.getMcVersion());
            }
            dateMillis = metaData.getDate();
            date.setText(new SimpleDateFormat().format(new Date(dateMillis)));
            if (thumbImage == null) {
                thumbnail = new GuiImage(defaultThumbnail).setSize(30 * 16 / 9, 30);
                addElements(null, thumbnail);
            } else {
                thumbnail = new GuiImage(this).setTexture(thumbImage).setSize(30 * 16 / 9, 30);
            }
            duration.setText(Utils.convertSecondsToShortString(metaData.getDuration() / 1000));
            addElements(null, durationPanel);

            setLayout(new CustomLayout<GuiReplayEntry>() {
                @Override
                protected void layout(GuiReplayEntry container, int width, int height) {
                    pos(thumbnail, 0, 0);
                    x(durationPanel, width(thumbnail) - width(durationPanel));
                    y(durationPanel, height(thumbnail) - height(durationPanel));

                    pos(infoPanel, width(thumbnail) + 5, 0);
                    pos(version, width - width(version), 0);
                }

                @Override
                public ReadableDimension calcMinSize(GuiContainer<?> container) {
                    return new Dimension(300, thumbnail.getMinSize().getHeight());
                }
            });
        }

        @Override
        protected GuiReplayEntry getThis() {
            return this;
        }

        @Override
        public int compareTo(GuiReplayEntry o) {
            return Long.compare(o.dateMillis, dateMillis);
        }
    }
}
