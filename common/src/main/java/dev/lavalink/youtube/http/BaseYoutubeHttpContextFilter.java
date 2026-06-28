package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;

public class BaseYoutubeHttpContextFilter implements HttpContextFilter {
  @Override
  public void onContextOpen(HttpClientContext context) {

  }

  @Override
  public void onContextClose(HttpClientContext context) {

  }

  @Override
  public void onRequest(HttpClientContext context,
                        ClassicHttpRequest request,
                        boolean isRepetition) {
    // Consent cookie, so we do not land on consent page for HTML requests
    request.addHeader("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+471");
  }

  @Override
  public boolean onRequestResponse(HttpClientContext context,
                                   ClassicHttpRequest request,
                                   HttpResponse response) {
    return false;
  }

  @Override
  public boolean onRequestException(HttpClientContext context,
                                    ClassicHttpRequest request,
                                    Throwable error) {
    return false;
  }
}
