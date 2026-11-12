package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import dev.lavalink.youtube.OptionDisabledException;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MWeb extends Web {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("MWEB")
        .withClientField("clientVersion", "2.20240726.11.00");
//        .withUserAgent("Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)");

    public static volatile String poToken;

    public MWeb() {
        super();
    }

    public MWeb(@NotNull ClientOptions options) {
        super(options);
    }

    public static void setPoTokenAndVisitorData(String poToken, String visitorData) {
        MWeb.poToken = poToken;

        synchronized (BASE_CONFIG) {
            if (poToken == null || visitorData == null) {
                BASE_CONFIG.getRoot().remove("serviceIntegrityDimensions");
                BASE_CONFIG.withVisitorData(null);
                return;
            }

            Map<String, Object> sid = BASE_CONFIG.putOnceAndJoin(BASE_CONFIG.getRoot(), "serviceIntegrityDimensions");
            sid.put("poToken", poToken);
            BASE_CONFIG.withVisitorData(visitorData);
        }
    }

    @Override
    @Nullable
    protected String getPoToken() {
        return MWeb.poToken;
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    protected List<AudioTrack> extractSearchResults(@NotNull YoutubeAudioSourceManager source,
                                                    @NotNull JsonBrowser json) {
        return json.get("contents")
            .get("sectionListRenderer")
            .get("contents")
            .values() // .index(0)
            .stream()
            .flatMap(item -> item.get("itemSectionRenderer").get("contents").values().stream()) // actual results
            .map(item -> extractAudioTrack(item.get("videoWithContextRenderer"), source))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    @NotNull
    protected JsonBrowser extractMixPlaylistData(@NotNull JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnWatchNextResults")
            .get("playlist")
            .get("playlist");
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
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        JsonBrowser sectionEntry = json.get("contents")
            .get("singleColumnBrowseResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .index(0);

        JsonBrowser contents = sectionEntry.get("itemSectionRenderer").get("contents");
        JsonBrowser playlistVideoList = contents.index(0).get("playlistVideoListRenderer");
        if (!playlistVideoList.isNull()) {
            return playlistVideoList;
        }

        if (!contents.isNull()) {
            return contents;
        }

        JsonBrowser directVideoList = sectionEntry.get("playlistVideoListRenderer");
        if (!directVideoList.isNull()) {
            return directVideoList;
        }

        return super.extractPlaylistVideoList(json);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
