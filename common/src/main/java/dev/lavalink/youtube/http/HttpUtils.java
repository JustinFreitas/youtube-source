package dev.lavalink.youtube.http;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Small helpers bridging HttpClient 4 -> 5 API differences.
 */
public final class HttpUtils {
    private HttpUtils() {
    }

    /**
     * Reads an entity to a String. HttpClient 5's {@link EntityUtils#toString(HttpEntity, Charset)}
     * declares the checked {@link ParseException} (thrown on a malformed Content-Type header), which
     * HttpClient 4 did not. This wrapper translates it into an {@link IOException} so the numerous
     * existing {@code throws IOException} call sites remain unchanged after the migration.
     */
    public static String entityToString(HttpEntity entity, Charset charset) throws IOException {
        try {
            return EntityUtils.toString(entity, charset);
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }
}
