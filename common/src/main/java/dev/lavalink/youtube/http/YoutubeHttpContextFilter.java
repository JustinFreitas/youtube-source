package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextRetryCounter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import dev.lavalink.youtube.clients.skeleton.Client;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

import static dev.lavalink.youtube.http.YoutubeOauth2Handler.OAUTH_INJECT_CONTEXT_ATTRIBUTE;

public class YoutubeHttpContextFilter extends BaseYoutubeHttpContextFilter {
  private static final Logger log = LoggerFactory.getLogger(YoutubeHttpContextFilter.class);

  private static final String ATTRIBUTE_RESET_RETRY = "isResetRetry";
  public static final String ATTRIBUTE_USER_AGENT_SPECIFIED = "clientUserAgent";
  public static final String ATTRIBUTE_VISITOR_DATA_SPECIFIED = "clientVisitorData";
  public static final String ATTRIBUTE_CIPHER_REQUEST_SPECIFIED = "remoteCipherRequest";

  private static final HttpContextRetryCounter retryCounter = new HttpContextRetryCounter("yt-token-retry");

  private YoutubeAccessTokenTracker tokenTracker;
  private YoutubeOauth2Handler oauth2Handler;

  private String remoteCipherPass;
  private String remoteCipherUserAgent;
  private String pluginVersion;

  public void setTokenTracker(@NotNull YoutubeAccessTokenTracker tokenTracker) {
    this.tokenTracker = tokenTracker;
  }

  public void setOauth2Handler(@NotNull YoutubeOauth2Handler oauth2Handler) {
    this.oauth2Handler = oauth2Handler;
  }

  public void setCipherConfig(@Nullable String remotePass,
                              @Nullable String userAgent,
                              @NotNull String pluginVersion) {
    this.remoteCipherPass = remotePass;
    this.remoteCipherUserAgent = userAgent;
    this.pluginVersion = pluginVersion;
  }


  @Override
  public void onContextOpen(HttpClientContext context) {
    CookieStore cookieStore = context.getCookieStore();

    if (cookieStore == null) {
      cookieStore = new BasicCookieStore();
      context.setCookieStore(cookieStore);
    }

    // Reset cookies and transient per-request attributes for each sequence of requests.
    cookieStore.clear();
    clearContextAttributes(context);
  }

  @Override
  public void onContextClose(HttpClientContext context) {
    clearContextAttributes(context);
    super.onContextClose(context);
  }

  private void clearContextAttributes(HttpClientContext context) {
    context.removeAttribute(ATTRIBUTE_RESET_RETRY);
    context.removeAttribute(ATTRIBUTE_USER_AGENT_SPECIFIED);
    context.removeAttribute(ATTRIBUTE_VISITOR_DATA_SPECIFIED);
    context.removeAttribute(ATTRIBUTE_CIPHER_REQUEST_SPECIFIED);
    context.removeAttribute(Client.OAUTH_CLIENT_ATTRIBUTE);
    context.removeAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE);
    context.removeAttribute(YoutubeAccessTokenTracker.TOKEN_FETCH_CONTEXT_ATTRIBUTE);
    context.removeAttribute(YoutubeOauth2Handler.OAUTH_FETCH_CONTEXT_ATTRIBUTE);
  }

  @Override
  public void onRequest(HttpClientContext context,
                        ClassicHttpRequest request,
                        boolean isRepetition) {
    if (!isRepetition) {
      context.removeAttribute(ATTRIBUTE_RESET_RETRY);
    }

    retryCounter.handleUpdate(context, isRepetition);

    if (tokenTracker != null && tokenTracker.isTokenFetchContext(context)) {
      // Used for fetching visitor id, let's not recurse.
      return;
    }

    if (oauth2Handler != null && oauth2Handler.isOauthFetchContext(context)) {
      return;
    }

    // HttpClient 5's ClassicHttpRequest#getUri() throws URISyntaxException (unlike HC4's getURI()).
    URI requestUri;
    try {
      requestUri = request.getUri();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    if (isRemoteCipherRequest(context)) {
      if (!DataFormatTools.isNullOrEmpty(remoteCipherPass)) {
        request.setHeader("Authorization", remoteCipherPass);
      }

      if (!DataFormatTools.isNullOrEmpty(remoteCipherUserAgent)) {
        request.setHeader("User-Agent", remoteCipherUserAgent);
      }

      if (pluginVersion != null) {
        request.setHeader("Plugin-Version", pluginVersion);
      }
      return;
    }

    super.onRequest(context, request, isRepetition);

    String userAgent = context.getAttribute(ATTRIBUTE_USER_AGENT_SPECIFIED, String.class);
    String host = requestUri.getHost();

    if (host == null || !host.contains("googlevideo")) {
      if (userAgent != null) {
        request.setHeader("User-Agent", userAgent);

        String visitorData = context.getAttribute(ATTRIBUTE_VISITOR_DATA_SPECIFIED, String.class);
        String resolvedVisitorId = visitorData != null ? visitorData : (tokenTracker != null ? tokenTracker.getVisitorId() : null);
        if (resolvedVisitorId != null) {
          request.setHeader("X-Goog-Visitor-Id", resolvedVisitorId);
        }

        context.removeAttribute(ATTRIBUTE_VISITOR_DATA_SPECIFIED);
        context.removeAttribute(ATTRIBUTE_USER_AGENT_SPECIFIED);
      }

      boolean isRequestFromOauthedClient = Boolean.TRUE.equals(context.getAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, Boolean.class));

      if (isRequestFromOauthedClient && oauth2Handler != null && Client.PLAYER_URL.equals(requestUri.toString())) {
        // Look at the userdata for any provided oauth-token
        String oauthToken = context.getAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE, String.class);
        // only apply the token to /player requests.
        if (oauthToken != null && !oauthToken.isEmpty()) {
          oauth2Handler.applyToken(request, oauthToken);
        } else {
          oauth2Handler.applyToken(request);
        }
      }
    }

//    try {
//      URI uri = new URIBuilder(request.getURI())
//          .setParameter("key", YoutubeConstants.INNERTUBE_ANDROID_API_KEY)
//          .build();
//
//      if (request instanceof HttpRequestBase) {
//        ((HttpRequestBase) request).setURI(uri);
//      } else {
//        throw new IllegalStateException("Cannot update request URI.");
//      }
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
  }

  @Override
  public boolean onRequestResponse(HttpClientContext context,
                                   ClassicHttpRequest request,
                                   HttpResponse response) {

//    if (tokenTracker.isTokenFetchContext(context) || retryCounter.getRetryCount(context) >= 1) {
//      return false;
//    }
    return false;
  }

  @Override
  public boolean onRequestException(HttpClientContext context,
                                    ClassicHttpRequest request,
                                    Throwable error) {
    // Always retry once in case of connection reset exception.
    if (HttpClientTools.isConnectionResetException(error)) {
      if (context.getAttribute(ATTRIBUTE_RESET_RETRY) == null) {
        context.setAttribute(ATTRIBUTE_RESET_RETRY, true);
        return true;
      }
    }

    return false;
  }

  private boolean isRemoteCipherRequest(HttpClientContext context) {
    return Boolean.TRUE.equals(context.getAttribute(ATTRIBUTE_CIPHER_REQUEST_SPECIFIED, Boolean.class));
  }
}
