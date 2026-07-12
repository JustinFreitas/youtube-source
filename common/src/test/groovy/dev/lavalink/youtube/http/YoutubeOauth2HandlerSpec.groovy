package dev.lavalink.youtube.http

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class YoutubeOauth2HandlerSpec extends Specification {

    private ClassicHttpResponse tokenResponse(String body) {
        def bytes = body.getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(bytes)
        entity.getContentLength() >> bytes.length
        return response
    }

    // --- updateTokens (private, called via Groovy's dynamic dispatch which bypasses Java access checks) ---

    def "updateTokens applies access token, token type, refresh token and expiry from the response"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))

        when:
        handler.updateTokens(JsonBrowser.parse(
            '{"token_type":"Bearer","access_token":"AT1","refresh_token":"RT1","expires_in":3600}'
        ))

        then:
        handler.hasAccessToken()
        handler.getRefreshToken() == "RT1"
    }

    def "updateTokens keeps the previous refresh token when the response omits one"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.refreshToken = "OLD_RT"

        when:
        handler.updateTokens(JsonBrowser.parse(
            '{"token_type":"Bearer","access_token":"AT1","expires_in":3600}'
        ))

        then:
        handler.getRefreshToken() == "OLD_RT"
    }

    // --- shouldRefreshAccessToken ---

    def "shouldRefreshAccessToken is false when oauth is not enabled"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.refreshToken = "RT1"
        handler.accessToken = null

        expect:
        !handler.shouldRefreshAccessToken()
    }

    def "shouldRefreshAccessToken is true when enabled with no access token yet"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = null

        expect:
        handler.shouldRefreshAccessToken()
    }

    def "shouldRefreshAccessToken is false when the access token has not yet expired"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = "AT1"
        handler.tokenExpires = System.currentTimeMillis() + 60000

        expect:
        !handler.shouldRefreshAccessToken()
    }

    def "shouldRefreshAccessToken is true once the access token has expired"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = "AT1"
        handler.tokenExpires = System.currentTimeMillis() - 1000

        expect:
        handler.shouldRefreshAccessToken()
    }

    def "shouldRefreshAccessToken is false without a refresh token"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.enabled = true
        handler.refreshToken = null
        handler.accessToken = null

        expect:
        !handler.shouldRefreshAccessToken()
    }

    // --- refreshAccessToken ---

    def "refreshAccessToken throws without a refresh token"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))

        when:
        handler.refreshAccessToken(true)

        then:
        thrown(IllegalStateException)
    }

    def "refreshAccessToken is a no-op when not forced and not yet due"() {
        given:
        def manager = Mock(HttpInterfaceManager)
        def handler = new YoutubeOauth2Handler(manager)
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = "AT1"
        handler.tokenExpires = System.currentTimeMillis() + 60000

        when:
        handler.refreshAccessToken(false)

        then:
        0 * manager.getInterface()
    }

    def "refreshAccessToken fetches and applies a new token when forced"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def handler = new YoutubeOauth2Handler(manager)
        handler.refreshToken = "RT1"

        when:
        handler.refreshAccessToken(true)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> tokenResponse(
            '{"token_type":"Bearer","access_token":"AT_NEW","expires_in":3600}'
        )
        handler.hasAccessToken()
    }

    def "refreshAccessToken throws when the response contains an error"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def handler = new YoutubeOauth2Handler(manager)
        handler.refreshToken = "RT1"

        when:
        handler.refreshAccessToken(true)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> tokenResponse('{"error":"invalid_grant"}')
        thrown(RuntimeException)
        !handler.hasAccessToken()
    }

    // --- applyToken(request) ---

    def "applyToken does nothing when oauth is disabled"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        def request = Mock(ClassicHttpRequest)

        when:
        handler.applyToken(request)

        then:
        0 * request.setHeader(*_)
    }

    def "applyToken sets the Authorization header when the access token is already valid"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = "AT1"
        handler.tokenType = "Bearer"
        handler.tokenExpires = System.currentTimeMillis() + 60000
        def request = Mock(ClassicHttpRequest)

        when:
        handler.applyToken(request)

        then:
        1 * request.setHeader("Authorization", "Bearer AT1")
    }

    def "applyToken refreshes an expired token before setting the header"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def handler = new YoutubeOauth2Handler(manager)
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = null
        def request = Mock(ClassicHttpRequest)

        when:
        handler.applyToken(request)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> tokenResponse(
            '{"token_type":"Bearer","access_token":"AT_NEW","expires_in":3600}'
        )
        1 * request.setHeader("Authorization", "Bearer AT_NEW")
    }

    def "applyToken swallows a refresh failure, backs off, and does not set a header"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def handler = new YoutubeOauth2Handler(manager)
        handler.enabled = true
        handler.refreshToken = "RT1"
        handler.accessToken = null
        def request = Mock(ClassicHttpRequest)

        when:
        handler.applyToken(request)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { throw new IOException("network down") }
        noExceptionThrown()
        0 * request.setHeader(*_)
        !handler.hasAccessToken()
    }

    def "applyToken(request, token) always sets a Bearer header regardless of internal state"() {
        given:
        def handler = new YoutubeOauth2Handler(Mock(HttpInterfaceManager))
        def request = Mock(ClassicHttpRequest)

        when:
        handler.applyToken(request, "MY_TOKEN")

        then:
        1 * request.setHeader("Authorization", "Bearer MY_TOKEN")
    }

    // --- setRefreshToken ---

    def "setRefreshToken with a blank token and skipInitialization does not touch the network"() {
        given:
        def manager = Mock(HttpInterfaceManager)
        def handler = new YoutubeOauth2Handler(manager)

        when:
        handler.setRefreshToken(null, true)

        then:
        0 * manager.getInterface()
        !handler.hasAccessToken()
        handler.getRefreshToken() == null
    }

    def "setRefreshToken with a real token immediately refreshes and enables oauth"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def handler = new YoutubeOauth2Handler(manager)
        def request = Mock(ClassicHttpRequest)

        when:
        handler.setRefreshToken("RT1", true)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> tokenResponse(
            '{"token_type":"Bearer","access_token":"AT1","expires_in":3600}'
        )
        handler.hasAccessToken()

        when: "applying the token now that oauth is enabled"
        handler.applyToken(request)

        then: "the Authorization header is set without needing another refresh"
        1 * request.setHeader("Authorization", "Bearer AT1")
    }
}
