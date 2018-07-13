package com.replaymod.render.gui;

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

public class noGuiRenderSettings  {
	/** RAH
	* Attempt to remove the GUI
	*
	**/
	public void doRender ()
	{
            // Closing this GUI ensures that settings are saved
            //getMinecraft().displayGuiScreen(null);
            try {
                VideoRenderer videoRenderer = new VideoRenderer(save(false), replayHandler, timeline);
                videoRenderer.renderVideo();
            } catch (VideoWriter.NoFFmpegException e) {
                LOGGER.error("Rendering video:", e);
                getMinecraft().displayGuiScreen(errorScreen);
            } catch (VideoWriter.FFmpegStartupException e) {
				e.printStackTrace();
            } catch (Throwable t) {
                //error(LOGGER, noGuiRenderSettings.this, CrashReport.makeCrashReport(t, "Rendering video"), () -> {});
				LOGGER.error("Rendering video:", e);
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




    public void load(RenderSettings settings) {
		return;
    }

    public RenderSettings save(boolean serialize) {
        return null;

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
