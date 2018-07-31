package com.replaymod.online;

import com.replaymod.core.ReplayMod;
import com.replaymod.online.api.ApiClient;
import com.replaymod.online.gui.GuiLoginPrompt;
import com.replaymod.online.gui.GuiReplayDownloading;
import com.replaymod.online.gui.GuiSaveModifiedReplay;
import com.replaymod.online.handler.GuiHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.events.ReplayCloseEvent;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;
import de.johni0702.minecraft.gui.container.GuiScreen;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;

//#if MC>=10800
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
//#else
//$$ import cpw.mods.fml.common.Mod;
//$$ import cpw.mods.fml.common.event.FMLInitializationEvent;
//$$ import cpw.mods.fml.common.event.FMLPostInitializationEvent;
//$$ import cpw.mods.fml.common.event.FMLPreInitializationEvent;
//$$ import cpw.mods.fml.common.eventhandler.SubscribeEvent;
//#endif

import java.io.File;
import java.io.IOException;

import static com.replaymod.core.versions.MCVer.*;
import static net.minecraft.client.Minecraft.getMinecraft;

@Mod(modid = ReplayModOnline.MOD_ID,
        version = "@MOD_VERSION@",
        acceptedMinecraftVersions = "@MC_VERSION@",
        acceptableRemoteVersions = "*",
        //#if MC>=10800
        clientSideOnly = true,
        //#endif
        useMetadata = true)
public class ReplayModOnline {
    public static final String MOD_ID = "replaymod-online";

    @Mod.Instance(MOD_ID)
    public static ReplayModOnline instance;

    private ReplayMod core;

    private ReplayModReplay replayModule;

    public static Logger LOGGER;

    private ApiClient apiClient;

    /**
     * In case the currently opened replay gets modified, the resulting replay file is saved to this location.
     * Usually a file within the normal replays folder with a unique name.
     * When the replay is closed, the user is asked whether they want to give it a proper name.
     */
    private File currentReplayOutputFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        core = ReplayMod.instance;
        replayModule = ReplayModReplay.instance;

        core.getSettingsRegistry().register(Setting.class);

        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        ConfigurationAuthData authData = new ConfigurationAuthData(config);
        apiClient = new ApiClient(authData);
        authData.load(apiClient);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!getDownloadsFolder().exists()){
            if (!getDownloadsFolder().mkdirs()) {
                LOGGER.warn("Failed to create downloads folder: " + getDownloadsFolder());
            }
        }

        new GuiHandler(this).register();
        FML_BUS.register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Initial login prompt
        //if (!core.getSettingsRegistry().get(Setting.SKIP_LOGIN_PROMPT)) {
		if (false) {
            if (!isLoggedIn()) {
                core.runLater(() -> {
                    GuiScreen parent = GuiScreen.wrap(getMinecraft().currentScreen);
                    new GuiLoginPrompt(apiClient, parent, parent, false).display();
                });
            }
        }
    }

    public ReplayMod getCore() {
        return core;
    }

    public ReplayModReplay getReplayModule() {
        return replayModule;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public boolean isLoggedIn() {
        return apiClient.isLoggedIn();
    }

    public File getDownloadsFolder() {
        String path = core.getSettingsRegistry().get(Setting.DOWNLOAD_PATH);
        return new File(path.startsWith("./") ? getMinecraft().mcDataDir : null, path);
    }

    public File getDownloadedFile(int id) {
        return new File(getDownloadsFolder(), id + ".mcpr");
    }

    public boolean hasDownloaded(int id) {
        return getDownloadedFile(id).exists();
    }

    public void startReplay(int id, String name, GuiScreen onDownloadCancelled) throws IOException {
        File file = getDownloadedFile(id);
        if (file.exists()) {
            currentReplayOutputFile = new File(core.getReplayFolder(), System.currentTimeMillis() + ".mcpr");
            ReplayFile replayFile = new ZipReplayFile(new ReplayStudio(), file, currentReplayOutputFile);
            replayModule.startReplay(replayFile);
        } else {
            new GuiReplayDownloading(onDownloadCancelled, this, id, name).display();
        }
    }

    @SubscribeEvent
    public void onReplayClosed(ReplayCloseEvent.Post event) {
        if (currentReplayOutputFile != null) {
            if (currentReplayOutputFile.exists()) { // Replay was modified, ask user for new name
                new GuiSaveModifiedReplay(currentReplayOutputFile).display();
            }
            currentReplayOutputFile = null;
        }
    }
}
