package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Ios extends StreamingNonMusicClient {
    public static String CLIENT_VERSION = "21.32.4";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withUserAgent(String.format("com.google.ios.youtube/%s (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)", CLIENT_VERSION))
        .withClientName("IOS")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withUserField("lockedSafetyMode", false);

    protected ClientOptions options;

    public Ios() {
        this(ClientOptions.DEFAULT);
    }

    public Ios(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        JsonBrowser contents = json.get("contents")
            .get("singleColumnBrowseResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .index(0)
            .get("itemSectionRenderer")
            .get("contents");

        JsonBrowser playlistVideoList = contents.index(0).get("playlistVideoListRenderer");
        if (!playlistVideoList.isNull()) {
            return playlistVideoList;
        }

        if (!contents.isNull()) {
            return contents;
        }

        return super.extractPlaylistVideoList(json);
    }

    @Override
    @Nullable
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        String title = json.get("header")
                .get("pageHeaderRenderer")
                .get("pageTitle")
                .text();
        return title != null ? title : super.extractPlaylistName(json);
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return WEB_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return this.options;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public boolean requirePlayerScript() {
        return false;
    }
}
