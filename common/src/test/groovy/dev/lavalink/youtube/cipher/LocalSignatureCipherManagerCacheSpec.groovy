package dev.lavalink.youtube.cipher

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static dev.lavalink.youtube.cipher.CipherScriptFixtures.TIMESTAMP_LINE

class LocalSignatureCipherManagerCacheSpec extends Specification {

    LocalSignatureCipherManager manager = new LocalSignatureCipherManager()

    private ClassicHttpResponse responseWithBody(String body) {
        def bytes = body.getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(bytes)
        entity.getContentLength() >> bytes.length
        return response
    }

    def "getCachedPlayerScript fetches once and reuses the cache on a second call"() {
        given:
        def httpInterface = Mock(HttpInterface)
        def embedBody = '{"jsUrl":"/s/player/abc123/player_ias.vflset/en_US/base.js"}'

        when:
        def first = manager.getCachedPlayerScript(httpInterface)
        def second = manager.getCachedPlayerScript(httpInterface)

        then: "only the first call hits the network -- once for the embed page, once for the script"
        2 * httpInterface.execute(_ as HttpUriRequestBase) >>> [
            responseWithBody(embedBody),
            responseWithBody(TIMESTAMP_LINE)
        ]
        first.url == "/s/player/abc123/player_ias.vflset/en_US/base.js"
        first.signatureTimestamp == "12345"

        and: "the second call returns the exact same cached instance"
        second.is(first)
    }

    def "getCachedPlayerScript refetches once the cached script has expired"() {
        given:
        def httpInterface = Mock(HttpInterface)
        def embedBody = '{"jsUrl":"/s/player/abc123/player_ias.vflset/en_US/base.js"}'

        def expiredScript = new CipherManager.CachedPlayerScript("/old/base.js", "99999")
        def expireField = CipherManager.CachedPlayerScript.getDeclaredField("expireTimestampMs")
        expireField.setAccessible(true)
        expireField.set(expiredScript, System.currentTimeMillis() - 1000)
        manager.cachedPlayerScript = expiredScript

        when:
        def result = manager.getCachedPlayerScript(httpInterface)

        then: "the expired entry is not reused -- a fresh fetch happens"
        2 * httpInterface.execute(_ as HttpUriRequestBase) >>> [
            responseWithBody(embedBody),
            responseWithBody(TIMESTAMP_LINE)
        ]
        result.url == "/s/player/abc123/player_ias.vflset/en_US/base.js"
        !result.is(expiredScript)
    }
}
