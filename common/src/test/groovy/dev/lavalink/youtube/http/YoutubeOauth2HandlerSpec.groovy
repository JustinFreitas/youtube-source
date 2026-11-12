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

    def "pollForToken exits cleanly and restores interrupt flag on InterruptedException"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        httpInterface.execute(_ as HttpPost) >> {
            return tokenResponse('{"error":"authorization_pending"}')
        }
        def handler = new YoutubeOauth2Handler(manager)
        def interruptedFlagRestored = new java.util.concurrent.atomic.AtomicBoolean(false)

        when:
        def t = new Thread({
            Thread.currentThread().interrupt()
            handler.pollForToken("DEV_CODE", 5000L)
            interruptedFlagRestored.set(Thread.currentThread().isInterrupted())
        })
        t.start()
        t.join(2000L)

        then:
        !t.isAlive()
        interruptedFlagRestored.get()
    }

    def "pollForToken sleeps on RuntimeException instead of busy-spinning and succeeds on retry"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def attempts = new java.util.concurrent.atomic.AtomicInteger(0)
        httpInterface.execute(_ as HttpPost) >> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("transient network error")
            }
            return tokenResponse('{"token_type":"Bearer","access_token":"AT_POLL","refresh_token":"RT_POLL","expires_in":3600}')
        }
        def handler = new YoutubeOauth2Handler(manager)

        when:
        long start = System.currentTimeMillis()
        handler.pollForToken("DEV_CODE", 60L)
        long elapsed = System.currentTimeMillis() - start

        then:
        attempts.get() == 2
        elapsed >= 45L
        handler.hasAccessToken()
        handler.getRefreshToken() == "RT_POLL"
    }

    def "pollForToken honors slow_down by increasing polling interval"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def attempts = new java.util.concurrent.atomic.AtomicInteger(0)
        def handler = new YoutubeOauth2Handler(manager)
        def t = new Thread({
            handler.pollForToken("DEV_CODE", 10L)
        })
        httpInterface.execute(_ as HttpPost) >> {
            if (attempts.incrementAndGet() == 1) {
                return tokenResponse('{"error":"slow_down"}')
            }
            return tokenResponse('{"token_type":"Bearer","access_token":"AT_SLOW","refresh_token":"RT_SLOW","expires_in":3600}')
        }

        when:
        t.start()
        Thread.sleep(120L)

        then: "because slow_down adds 5000ms to the 10ms interval, attempt 2 has not fired yet after 120ms"
        attempts.get() == 1
        t.isAlive()

        cleanup:
        t.interrupt()
        t.join(1000L)
    }

    def "setRefreshToken cancels any previously active poll thread"() {
        given:
        def pollEntered = new java.util.concurrent.CountDownLatch(1)
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            if (req.getUri().toString().endsWith("/device/code")) {
                return tokenResponse('{"verification_url":"https://google.com/device","user_code":"ABCD","device_code":"DC1","interval":10}')
            }
            pollEntered.countDown()
            return tokenResponse('{"error":"authorization_pending"}')
        }
        def handler = new YoutubeOauth2Handler(manager)

        when:
        handler.setRefreshToken(null, false)
        assert pollEntered.await(2, java.util.concurrent.TimeUnit.SECONDS)
        def firstPollThread = handler.pollThread as Thread
        assert firstPollThread != null && firstPollThread.isAlive()

        and: "calling setRefreshToken again cancels the old poll thread"
        handler.setRefreshToken(null, true)
        firstPollThread.join(2000L)

        then:
        !firstPollThread.isAlive()
    }

    def "log output redacts raw accessToken and refreshToken values"() {
        given:
        def loggerContext = org.apache.logging.log4j.LogManager.getContext(false) as org.apache.logging.log4j.core.LoggerContext
        def config = loggerContext.configuration
        def messages = new java.util.concurrent.CopyOnWriteArrayList<String>()
        def appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
            "OauthRedactTestAppender", null, org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY
        ) {
            @Override
            void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        def loggerConfig = config.getLoggerConfig(YoutubeOauth2Handler.name)
        def previousLevel = loggerConfig.level
        loggerConfig.setLevel(org.apache.logging.log4j.Level.TRACE)
        loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.TRACE, null)
        loggerContext.updateLoggers()

        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        httpInterface.execute(_ as HttpPost) >> tokenResponse(
            '{"token_type":"Bearer","access_token":"SUPER_SECRET_ACCESS_TOKEN_999","refresh_token":"SUPER_SECRET_REFRESH_TOKEN_888","expires_in":3600}'
        )
        def handler = new YoutubeOauth2Handler(manager)
        def request = Mock(ClassicHttpRequest)

        when:
        handler.setRefreshToken("SUPER_SECRET_REFRESH_TOKEN_888", true)
        handler.applyToken(request)

        then:
        messages.every { !it.contains("SUPER_SECRET_ACCESS_TOKEN_999") && !it.contains("SUPER_SECRET_REFRESH_TOKEN_888") }

        cleanup:
        loggerConfig.removeAppender("OauthRedactTestAppender")
        loggerConfig.setLevel(previousLevel)
        appender.stop()
        loggerContext.updateLoggers()
    }
}
