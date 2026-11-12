package dev.lavalink.youtube.http

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.lavalink.youtube.clients.skeleton.Client
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import spock.lang.Specification

class YoutubeHttpContextFilterSpec extends Specification {

    def "onRequest preserves CONSENT cookie from BaseYoutubeHttpContextFilter without duplicating on retry"() {
        given:
        def filter = new YoutubeHttpContextFilter()
        def context = HttpClientContext.create()
        def request = new HttpGet("https://www.youtube.com/embed/dQw4w9WgXcQ")

        when:
        filter.onRequest(context, request, false)
        filter.onRequest(context, request, true)

        then:
        def cookies = request.getHeaders("Cookie")
        cookies.length == 1
        cookies[0].value.contains("CONSENT=YES+")
    }

    def "onRequest applies OAuth token to PLAYER_URL and onContextClose cleans up OAUTH_CLIENT_ATTRIBUTE"() {
        given:
        def oauthHandler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        oauthHandler.enabled = true
        oauthHandler.refreshToken = "RT_1"
        oauthHandler.accessToken = "AT_1"
        oauthHandler.tokenType = "Bearer"
        oauthHandler.tokenExpires = System.currentTimeMillis() + 60000L

        def filter = new YoutubeHttpContextFilter()
        filter.setOauth2Handler(oauthHandler)

        def context = HttpClientContext.create()
        context.setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, true)

        def searchReq = new HttpPost(Client.SEARCH_URL)
        def playerReq = new HttpPost(Client.PLAYER_URL)

        when: "a non-player request runs first, followed by a player request"
        filter.onRequest(context, searchReq, false)
        filter.onRequest(context, playerReq, false)

        then: "Authorization is only applied to PLAYER_URL"
        searchReq.getFirstHeader("Authorization") == null
        playerReq.getFirstHeader("Authorization")?.value == "Bearer AT_1"

        when: "context closes"
        filter.onContextClose(context)

        then: "OAUTH_CLIENT_ATTRIBUTE is removed"
        context.getAttribute(Client.OAUTH_CLIENT_ATTRIBUTE) == null
    }
}
