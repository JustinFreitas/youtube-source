package dev.lavalink.youtube.http

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class YoutubeAccessTokenTrackerSpec extends Specification {

    private ClassicHttpResponse visitorIdResponse(String visitorId) {
        def body = "{\"responseContext\":{\"visitorData\":\"${visitorId}\"}}"
        def bytes = body.getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(bytes)
        entity.getContentLength() >> bytes.length
        return response
    }

    def "getVisitorId fetches and caches on first call"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def tracker = new YoutubeAccessTokenTracker(manager)

        when:
        def visitorId = tracker.getVisitorId()

        then:
        1 * httpInterface.execute(_ as HttpPost) >> visitorIdResponse("VISITOR_1")
        visitorId == "VISITOR_1"
    }

    def "getVisitorId reuses the cached value within the refresh interval"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def tracker = new YoutubeAccessTokenTracker(manager)

        when:
        def first = tracker.getVisitorId()
        def second = tracker.getVisitorId()

        then: "only the first call hits the network"
        1 * httpInterface.execute(_ as HttpPost) >> visitorIdResponse("VISITOR_1")
        first == "VISITOR_1"
        second == "VISITOR_1"
    }

    def "getVisitorId refetches once the refresh interval has elapsed"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def tracker = new YoutubeAccessTokenTracker(manager)

        def visitorIdField = YoutubeAccessTokenTracker.getDeclaredField("visitorId")
        visitorIdField.setAccessible(true)
        visitorIdField.set(tracker, "STALE_VISITOR")

        def lastUpdateField = YoutubeAccessTokenTracker.getDeclaredField("lastVisitorIdUpdate")
        lastUpdateField.setAccessible(true)
        // More than the 10-minute refresh interval in the past.
        lastUpdateField.set(tracker, System.currentTimeMillis() - java.util.concurrent.TimeUnit.MINUTES.toMillis(11))

        when:
        def visitorId = tracker.getVisitorId()

        then:
        1 * httpInterface.execute(_ as HttpPost) >> visitorIdResponse("VISITOR_FRESH")
        visitorId == "VISITOR_FRESH"
    }

    def "getVisitorId returns the previous value and swallows the error when the fetch fails"() {
        given:
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def manager = Mock(HttpInterfaceManager) {
            getInterface() >> httpInterface
        }
        def tracker = new YoutubeAccessTokenTracker(manager)

        when:
        def visitorId = tracker.getVisitorId()

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { throw new IOException("network down") }
        noExceptionThrown()
        visitorId == null
    }
}
