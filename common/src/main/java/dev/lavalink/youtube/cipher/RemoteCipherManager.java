package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonWriter;
import com.grack.nanojson.JsonStringWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.ExceptionWithResponseBody;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of ciphers via a remote service
 */
public class RemoteCipherManager implements CipherManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteCipherManager.class);

    private final @NotNull String remoteUrl;

    protected volatile CachedPlayerScript cachedPlayerScript;

    /**
     * Create a new remote cipher manager
     */
    public RemoteCipherManager(@NotNull String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    @NotNull
    public String getRemoteUrl() {
        return remoteUrl;
    }


    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @NotNull
    public URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                                @NotNull String playerScript,
                                @NotNull StreamFormat format) throws IOException {
        return resolveUrl(
            httpInterface,
            format.getUrl(),
            playerScript,
            format.getSignature(),
            format.getNParameter(),
            format.getSignatureKey()
        );
    }

    public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
            synchronized (this) {
                if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
                    try {
                        return (cachedPlayerScript = getPlayerScript(httpInterface));
                    } catch (RuntimeException e) {
                        if (e instanceof ExceptionWithResponseBody) {
                            throw throwWithDebugInfo(log, null, e.getMessage(), "html", ((ExceptionWithResponseBody) e).getResponseBody());
                        }

                        throw e;
                    }
                }
            }
        }

        return cachedPlayerScript;
    }

    public String getTimestamp(HttpInterface httpInterface, String sourceUrl) throws IOException {
        synchronized (this) {
            HttpPost request = new HttpPost(getRemoteEndpoint("get_sts"));

            log.debug("Getting timestamp for script: {}", sourceUrl);

            String requestBody = JsonWriter.string()
                .object()
                .value("player_url", sourceUrl)
                .end()
                .done();
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            try (ClassicHttpResponse response = configureHttpInterface(httpInterface).execute(request)) {
                String responseBody = validateAndGetResponseBody(response);

                log.debug("Received response from remote cipher service: {}", responseBody);

                JsonBrowser json = JsonBrowser.parse(responseBody);
                return json.get("sts").text();
            }
        }
    }

    private String getRemoteEndpoint(String path) {
        return remoteUrl.endsWith("/") ? remoteUrl + path : remoteUrl + "/" + path;
    }

    public HttpInterface configureHttpInterface(HttpInterface httpInterface) {
        httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_CIPHER_REQUEST_SPECIFIED, true);
        return httpInterface;
    }

    private URI resolveUrl(HttpInterface httpInterface,
                           URI baseUrl,
                           String playerScript,
                           String signature,
                           String nParam,
                           String sigKey) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("resolve_url"));
        log.debug("Resolving stream url {} with player script {}", baseUrl, playerScript);

        JsonStringWriter writer = JsonWriter.string()
            .object()
            .value("stream_url", baseUrl.toString())
            .value("player_url", playerScript);

        if (signature != null) {
            writer.value("encrypted_signature", signature);
        }
        if (nParam != null) {
            writer.value("n_param", nParam);
        }
        if (sigKey != null) {
            writer.value("signature_key", sigKey);
        }

        String requestBody = writer.end().done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (ClassicHttpResponse response = configureHttpInterface(httpInterface).execute(request)) {
            String responseBody = validateAndGetResponseBody(response);
            JsonBrowser json = JsonBrowser.parse(responseBody);
            String resolvedUrl = json.get("resolved_url").text();

            if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                throw new IOException("Remote cipher service did not return a resolved URL.");
            }

            return new URI(resolvedUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public String validateAndGetResponseBody(@NotNull ClassicHttpResponse response) throws IOException {
        int statusCode = response.getCode();
        HttpEntity entity = response.getEntity();
        String responseBody = (entity != null) ? dev.lavalink.youtube.http.HttpUtils.entityToString(entity, StandardCharsets.UTF_8) : null;

        if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw new IOException("Remote cipher service request to resolve URL failed with status code: " + statusCode + ". Response: " + responseBody);
        }

        if (DataFormatTools.isNullOrEmpty(responseBody)) {
            throw new IOException("Received empty successful response from remote cipher service.");
        }

        return responseBody;
    }
}

