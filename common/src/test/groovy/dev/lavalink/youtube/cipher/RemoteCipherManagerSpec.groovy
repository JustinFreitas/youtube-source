package dev.lavalink.youtube.cipher

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import dev.lavalink.youtube.track.format.StreamFormat
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

class RemoteCipherManagerSpec extends Specification {

    HttpInterface httpInterface = Mock(HttpInterface) {
        getContext() >> HttpClientContext.create()
    }

    private static StreamFormat streamFormat(String signature = null, String nParam = null, String sigKey = null) {
        new StreamFormat(
            ContentType.parse("audio/webm; codecs=opus"),
            0, 128000, 1000, 2,
            "https://example.com/stream?id=abc",
            nParam, signature, sigKey,
            true, false
        )
    }

    private ClassicHttpResponse responseWithBody(int code, String body) {
        def bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        response.getCode() >> code
        if (body != null) {
            def entity = Mock(HttpEntity)
            response.getEntity() >> entity
            entity.getContent() >> new ByteArrayInputStream(bytes)
            entity.getContentLength() >> bytes.length
        } else {
            response.getEntity() >> null
        }
        return response
    }

    def "getTimestamp posts to the get_sts endpoint and extracts sts from the response"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")

        when:
        def timestamp = manager.getTimestamp(httpInterface, "https://youtube.com/player.js")

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            assert req.getUri().toString() == "https://cipher.example.com/get_sts"
            return responseWithBody(200, '{"sts":"12345"}')
        }
        timestamp == "12345"
    }

    def "getRemoteEndpoint avoids a double slash when the base url already ends with one"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com/")

        when:
        manager.getTimestamp(httpInterface, "https://youtube.com/player.js")

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            assert req.getUri().toString() == "https://cipher.example.com/get_sts"
            return responseWithBody(200, '{"sts":"12345"}')
        }
    }

    def "resolveFormatUrl posts signature, n parameter and signature key when present"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")
        def format = streamFormat("SIGVALUE", "NVALUE", "sig")

        when:
        def resolved = manager.resolveFormatUrl(httpInterface, "https://youtube.com/player.js", format)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            def body = req.getEntity().getContent().getText(StandardCharsets.UTF_8.name())
            assert body.contains('"encrypted_signature":"SIGVALUE"')
            assert body.contains('"n_param":"NVALUE"')
            assert body.contains('"signature_key":"sig"')
            return responseWithBody(200, '{"resolved_url":"https://resolved.example.com/stream"}')
        }
        resolved.toString() == "https://resolved.example.com/stream"
    }

    def "resolveFormatUrl omits signature fields when they are absent"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")
        def format = streamFormat()

        when:
        manager.resolveFormatUrl(httpInterface, "https://youtube.com/player.js", format)

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            def body = req.getEntity().getContent().getText(StandardCharsets.UTF_8.name())
            assert !body.contains("encrypted_signature")
            assert !body.contains("n_param")
            assert !body.contains("signature_key")
            return responseWithBody(200, '{"resolved_url":"https://resolved.example.com/stream"}')
        }
    }

    def "resolveFormatUrl throws when the remote service omits a resolved url"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")

        when:
        manager.resolveFormatUrl(httpInterface, "https://youtube.com/player.js", streamFormat())

        then:
        1 * httpInterface.execute(_ as HttpPost) >> responseWithBody(200, '{"resolved_url":""}')
        thrown(IOException)
    }

    def "validateAndGetResponseBody throws on a non-success status code"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")

        when:
        manager.validateAndGetResponseBody(responseWithBody(500, "server error"))

        then:
        thrown(IOException)
    }

    def "validateAndGetResponseBody throws on an empty successful response"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")

        when:
        manager.validateAndGetResponseBody(responseWithBody(200, ""))

        then:
        thrown(IOException)
    }

    def "validateAndGetResponseBody returns the body on a successful non-empty response"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")

        expect:
        manager.validateAndGetResponseBody(responseWithBody(200, '{"ok":true}')) == '{"ok":true}'
    }

    def "configureHttpInterface marks the request context as a cipher request"() {
        given:
        def manager = new RemoteCipherManager("https://cipher.example.com")
        def realHttpInterface = Mock(HttpInterface)
        def context = HttpClientContext.create()
        realHttpInterface.getContext() >> context

        when:
        manager.configureHttpInterface(realHttpInterface)

        then:
        context.getAttribute(dev.lavalink.youtube.http.YoutubeHttpContextFilter.ATTRIBUTE_CIPHER_REQUEST_SPECIFIED) == true
    }
}
