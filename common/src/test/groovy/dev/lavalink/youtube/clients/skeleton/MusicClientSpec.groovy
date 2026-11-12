package dev.lavalink.youtube.clients.skeleton

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.clients.Music
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class MusicClientSpec extends Specification {

    def "getMusicSearchResult sets https://music.youtube.com as Referer"() {
        given:
        def music = new Music()
        def httpInterface = Mock(HttpInterface) {
            getContext() >> HttpClientContext.create()
        }
        def bytes = '{}'.getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(bytes)
        entity.getContentLength() >> bytes.length

        String refererHeader = null

        when:
        music.getMusicSearchResult(httpInterface, "lofi")

        then:
        1 * httpInterface.execute(_ as HttpPost) >> { HttpPost req ->
            refererHeader = req.getFirstHeader("Referer")?.getValue()
            return response
        }
        refererHeader == "https://music.youtube.com"
    }

    def "extractSearchResultTracks guards empty runs without throwing IndexOutOfBoundsException"() {
        given:
        def music = new Music()
        def source = new YoutubeAudioSourceManager()
        def json = JsonBrowser.parse('''
            [
                {"musicResponsiveListItemRenderer":{"flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                        {"text":"Empty Runs Track","navigationEndpoint":{"watchEndpoint":{"videoId":"empty123456"}}}
                    ]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[]}}}
                ]}},
                {"musicResponsiveListItemRenderer":{"flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                        {"text":"Valid Track","navigationEndpoint":{"watchEndpoint":{"videoId":"valid123456"}}}
                    ]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                        {"text":"Valid Artist"},
                        {"text":" • "},
                        {"text":"3:30"}
                    ]}}}
                ]}}
            ]
        ''')

        when:
        def tracks = music.extractSearchResultTracks(source, json)

        then:
        noExceptionThrown()
        tracks.size() == 1
        tracks[0].info.identifier == "valid123456"
        tracks[0].info.author == "Valid Artist"
        tracks[0].info.length == 210000L
    }
}
