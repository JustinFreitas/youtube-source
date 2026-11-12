package dev.lavalink.youtube.clients.skeleton

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.cipher.CipherManager
import dev.lavalink.youtube.clients.AndroidVr
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class NonMusicClientRetrySpec extends Specification {

    private ClassicHttpResponse jsonResponse(String body) {
        def entity = new org.apache.hc.core5.http.io.entity.StringEntity(body, ContentType.APPLICATION_JSON)
        def header = new org.apache.hc.core5.http.message.BasicHeader("Content-Type", ContentType.APPLICATION_JSON.toString())
        def response = Mock(ClassicHttpResponse)
        response.getCode() >> 200
        response.getEntity() >> entity
        response.getFirstHeader("Content-Type") >> header
        return response
    }

    def "loadTrackInfoFromInnertube retries NON_EMBEDDABLE without embed fields and re-reads videoDetails"() {
        given:
        def videoId = "dQw4w9WgXcQ"
        def client = new AndroidVr()
        def source = new YoutubeAudioSourceManager(client)
        source.setCipherManager(Mock(CipherManager))

        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }

        def payloads = []
        def nonEmbeddableJson = '''
            {"playabilityStatus":{"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"simpleText":"Playback on other websites has been disabled by the video owner"}}}}}
        '''
        def okJson = """
            {"playabilityStatus":{"status":"OK"},
             "videoDetails":{"videoId":"${videoId}","title":"Rick Astley - Never Gonna Give You Up","author":"Rick Astley"}}
        """

        when:
        def result = client.loadTrackInfoFromInnertube(source, httpInterface, videoId, null, true)

        then:
        2 * httpInterface.execute(_ as HttpPost) >> { HttpPost post ->
            payloads << post.getEntity().getContent().getText(StandardCharsets.UTF_8.name())
            return payloads.size() == 1 ? jsonResponse(nonEmbeddableJson) : jsonResponse(okJson)
        }

        and: "first request includes EMBED clientScreen and thirdParty.embedUrl, while retry cleanly omits both"
        payloads[0].contains('"clientScreen":"EMBED"')
        payloads[0].contains('"thirdParty":{"embedUrl":"https://google.com"}')
        !payloads[1].contains('"clientScreen"')
        !payloads[1].contains('"thirdParty"')
        !AndroidVr.BASE_CONFIG.getRoot().context.containsKey("thirdParty")
        !AndroidVr.BASE_CONFIG.getRoot().context.client.containsKey("clientScreen")

        and: "videoDetails from the retry response is recognized without throwing Missing videoDetails block"
        result.get("videoDetails").get("videoId").text() == videoId
    }
}
