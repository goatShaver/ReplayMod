package com.replaymod.render.nogui;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.VideoWriter;
import com.replaymod.render.rendering.VideoRenderer;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;
import de.johni0702.minecraft.gui.container.GuiContainer;
import de.johni0702.minecraft.gui.container.GuiPanel;
import de.johni0702.minecraft.gui.container.GuiScreen;
import de.johni0702.minecraft.gui.container.GuiVerticalList;
import de.johni0702.minecraft.gui.element.*;
import de.johni0702.minecraft.gui.element.advanced.GuiColorPicker;
import de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import de.johni0702.minecraft.gui.function.Closeable;
import de.johni0702.minecraft.gui.layout.CustomLayout;
import de.johni0702.minecraft.gui.layout.GridLayout;
import de.johni0702.minecraft.gui.layout.HorizontalLayout;
import de.johni0702.minecraft.gui.layout.VerticalLayout;
import de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import de.johni0702.minecraft.gui.utils.Colors;
import de.johni0702.minecraft.gui.utils.Consumer;
import de.johni0702.minecraft.gui.utils.Utils;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.lwjgl.util.Color;
import org.lwjgl.util.Dimension;
import org.lwjgl.util.ReadableColor;
import org.lwjgl.util.ReadableDimension;

import javax.annotation.Nullable;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static com.replaymod.core.utils.Utils.error;
import static com.replaymod.render.ReplayModRender.LOGGER;

public class noGuiRenderSettings extends GuiScreen implements Closeable {
	/** RAH
	* Attempt to remove the GUI
	*
	**/
	public void doRender ()
	{
            // Closing this GUI ensures that settings are saved
            getMinecraft().displayGuiScreen(null);
            try {
                VideoRenderer videoRenderer = new VideoRenderer(save(false), replayHandler, timeline);
                videoRenderer.renderVideo();
            } catch (VideoWriter.NoFFmpegException e) {
                LOGGER.error("Rendering video:", e);
                getMinecraft().displayGuiScreen(errorScreen);
            } catch (VideoWriter.FFmpegStartupException e) {
				e.printStackTrace();
            } catch (Throwable t) {
                error(LOGGER, GuiRenderSettings.this, CrashReport.makeCrashReport(t, "Rendering video"), () -> {});
            }
        }
    }



    private final ReplayHandler replayHandler;
    private final Timeline timeline;
    private File outputFile;
    private boolean outputFileManuallySet;

    public noGuiRenderSettings(ReplayHandler replayHandler, Timeline timeline) {
        this.replayHandler = replayHandler;
        this.timeline = timeline;

        String json = getConfigProperty(ReplayModRender.instance.getConfiguration()).getString();
        RenderSettings settings = new GsonBuilder()
                .registerTypeAdapter(RenderSettings.class, (InstanceCreator<RenderSettings>) type -> getDefaultRenderSettings())
                .registerTypeAdapter(ReadableColor.class, new Gson().getAdapter(Color.class))
                .create().fromJson(json, RenderSettings.class);
        load(settings);
    }

    protected void updateInputs() {
        // Validate video width and height
        String error = isResolutionValid();
        if (error == null) {
            renderButton.setEnabled().setTooltip(null);
            videoWidth.setTextColor(Colors.WHITE);
            videoHeight.setTextColor(Colors.WHITE);
        } else {
            renderButton.setDisabled().setTooltip(new GuiTooltip().setI18nText(error));
            videoWidth.setTextColor(Colors.RED);
            videoHeight.setTextColor(Colors.RED);
        }

        // Enable/Disable bitrate input field and dropdown
        if (encodingPresetDropdown.getSelectedValue().hasBitrateSetting()) {
            bitRateField.setEnabled();
            bitRateUnit.setEnabled();
        } else {
            bitRateField.setDisabled();
            bitRateUnit.setDisabled();
        }

        // Enable/Disable camera stabilization checkboxes
        switch (renderMethodDropdown.getSelectedValue()) {
            case CUBIC:
            case EQUIRECTANGULAR:
            case ODS:
                stabilizePanel.forEach(IGuiCheckbox.class).setEnabled();
                break;
            default:
                stabilizePanel.forEach(IGuiCheckbox.class).setDisabled();
        }

        // Enable/Disable inject metadata checkbox
        if (encodingPresetDropdown.getSelectedValue().getFileExtension().equals("mp4")
                && (renderMethodDropdown.getSelectedValue() == RenderSettings.RenderMethod.EQUIRECTANGULAR
                || renderMethodDropdown.getSelectedValue() == RenderSettings.RenderMethod.ODS)) {
            inject360Metadata.setEnabled().setTooltip(null);
        } else {
            inject360Metadata.setDisabled().setTooltip(new GuiTooltip().setColor(Colors.RED)
                    .setI18nText("replaymod.gui.rendersettings.360metadata.error"));
        }
    }

    protected String isResolutionValid() {
        RenderSettings.EncodingPreset preset = encodingPresetDropdown.getSelectedValue();
        RenderSettings.RenderMethod method = renderMethodDropdown.getSelectedValue();
        int videoWidth = this.videoWidth.getInteger();
        int videoHeight = this.videoHeight.getInteger();

        // Make sure the export arguments haven't been changed manually
        if (exportArguments.getText().equals(preset.getValue())) {
            // Yuv420 requires both dimensions to be even
            if (preset.isYuv420()
                    && (videoWidth % 2 != 0 || videoHeight % 2 != 0)) {
                return "replaymod.gui.rendersettings.customresolution.warning.yuv420";
            }
        }

        if (method == RenderSettings.RenderMethod.CUBIC
                && (videoWidth * 3 / 4 != videoHeight || videoWidth * 3 % 4 != 0)) {
            return "replaymod.gui.rendersettings.customresolution.warning.cubic";
        }
        if (method == RenderSettings.RenderMethod.EQUIRECTANGULAR
                && (videoWidth / 2 != videoHeight || videoWidth % 2 != 0)) {
            return "replaymod.gui.rendersettings.customresolution.warning.equirectangular";
        }
        if (method == RenderSettings.RenderMethod.ODS
                && videoWidth != videoHeight) {
            return "replaymod.gui.rendersettings.customresolution.warning.ods";
        }
        return null;
    }

    public void load(RenderSettings settings) {
        renderMethodDropdown.setSelected(settings.getRenderMethod());
        encodingPresetDropdown.setSelected(settings.getEncodingPreset());
        videoWidth.setValue(settings.getTargetVideoWidth());
        videoHeight.setValue(settings.getTargetVideoHeight());
        frameRateSlider.setValue(settings.getFramesPerSecond() - 10);
        if (settings.getBitRate() % (1 << 20) == 0) {
            bitRateField.setValue(settings.getBitRate() >> 20);
            bitRateUnit.setSelected(2);
        } else if (settings.getBitRate() % (1 << 10) == 0) {
            bitRateField.setValue(settings.getBitRate() >> 10);
            bitRateUnit.setSelected(1);
        } else {
            bitRateField.setValue(settings.getBitRate());
            bitRateUnit.setSelected(0);
        }
        if (settings.getOutputFile() == null) {
            outputFile = generateOutputFile(settings.getEncodingPreset());
            outputFileManuallySet = false;
        } else {
            outputFile = settings.getOutputFile();
            outputFileManuallySet = true;
        }
        outputFileButton.setLabel(outputFile.getName());
        nametagCheckbox.setChecked(settings.isRenderNameTags());
        stabilizeYaw.setChecked(settings.isStabilizeYaw());
        stabilizePitch.setChecked(settings.isStabilizePitch());
        stabilizeRoll.setChecked(settings.isStabilizeRoll());
        if (settings.getChromaKeyingColor() == null) {
            chromaKeyingCheckbox.setChecked(false);
            chromaKeyingColor.setColor(Colors.GREEN);
        } else {
            chromaKeyingCheckbox.setChecked(true);
            chromaKeyingColor.setColor(settings.getChromaKeyingColor());
        }
        inject360Metadata.setChecked(settings.isInject360Metadata());
        antiAliasingDropdown.setSelected(settings.getAntiAliasing());
        exportCommand.setText(settings.getExportCommand());
        exportArguments.setText(settings.getExportArguments());

        updateInputs();
    }

    public RenderSettings save(boolean serialize) {
        return new RenderSettings(
                renderMethodDropdown.getSelectedValue(),
                encodingPresetDropdown.getSelectedValue(),
                videoWidth.getInteger(),
                videoHeight.getInteger(),
                frameRateSlider.getValue() + 10,
                bitRateField.getInteger() << (10 * bitRateUnit.getSelected()),
                serialize ? null : outputFile,
                nametagCheckbox.isChecked(),
                stabilizeYaw.isChecked() && (serialize || stabilizeYaw.isEnabled()),
                stabilizePitch.isChecked() && (serialize || stabilizePitch.isEnabled()),
                stabilizeRoll.isChecked() && (serialize || stabilizeRoll.isEnabled()),
                chromaKeyingCheckbox.isChecked() ? chromaKeyingColor.getColor() : null,
                inject360Metadata.isChecked() && (serialize || inject360Metadata.isEnabled()),
                antiAliasingDropdown.getSelectedValue(),
                exportCommand.getText(),
                exportArguments.getText(),
                net.minecraft.client.gui.GuiScreen.isCtrlKeyDown()
        );
    }

    protected File generateOutputFile(RenderSettings.EncodingPreset encodingPreset) {
        String fileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        File folder = ReplayModRender.instance.getVideoFolder();
        return new File(folder, fileName + "." + encodingPreset.getFileExtension());
    }

    private RenderSettings getDefaultRenderSettings() {
        return new RenderSettings(RenderSettings.RenderMethod.DEFAULT, RenderSettings.EncodingPreset.MP4_DEFAULT, 1920, 1080, 60, 10 << 20, null,
                true, false, false, false, null, false, RenderSettings.AntiAliasing.NONE, "", RenderSettings.EncodingPreset.MP4_DEFAULT.getValue(), false);
    }

    @Override
    public void close() {
        RenderSettings settings = save(true);
        String json = new Gson().toJson(settings);
        Configuration config = ReplayModRender.instance.getConfiguration();
        getConfigProperty(config).set(json);
        config.save();
    }

    protected Property getConfigProperty(Configuration configuration) {
        return configuration.get("rendersettings", "settings", "{}",
                "Last state of the render settings GUI. Internal use only.");
    }

    public ReplayHandler getReplayHandler() {
        return replayHandler;
    }
}
