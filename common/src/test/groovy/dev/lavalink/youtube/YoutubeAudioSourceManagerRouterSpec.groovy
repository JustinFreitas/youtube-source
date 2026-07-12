package dev.lavalink.youtube

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import dev.lavalink.youtube.clients.skeleton.Client
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.protocol.RedirectLocations
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

class YoutubeAudioSourceManagerRouterSpec extends Specification {

    static final String VIDEO_ID = "dQw4w9WgXcQ"

    HttpInterface httpInterface = Mock(HttpInterface)

    private YoutubeAudioSourceManager manager(boolean allowSearch = true, boolean allowDirectVideoIds = true, boolean allowDirectPlaylistIds = true) {
        new YoutubeAudioSourceManager(allowSearch, allowDirectVideoIds, allowDirectPlaylistIds)
    }

    def "ytsearch prefix routes to loadSearch when search is allowed"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "ytsearch:some query")
        router.route(client)

        then:
        1 * client.loadSearch(source, httpInterface, "some query")
    }

    def "empty ytsearch query returns Router.none"() {
        given:
        def source = manager()

        when:
        def router = source.getRouter(httpInterface, "ytsearch:   ")

        then:
        router.route(Mock(Client)) == AudioReference.NO_TRACK
    }

    def "ytsearch prefix is unhandled when search is disallowed"() {
        given:
        def source = manager(false)

        expect:
        source.getRouter(httpInterface, "ytsearch:some query") == null
    }

    def "ytmsearch prefix routes to loadSearchMusic when search is allowed"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "ytmsearch:some query")
        router.route(client)

        then:
        1 * client.loadSearchMusic(source, httpInterface, "some query")
    }

    def "watch URL with a video id routes to loadVideo"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/watch?v=${VIDEO_ID}")
        router.route(client)

        then:
        1 * client.loadVideo(source, httpInterface, VIDEO_ID)
    }

    def "watch URL with a non-mix, non-excluded playlist routes to loadPlaylist with the video id"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/watch?v=${VIDEO_ID}&list=PLabc123")
        router.route(client)

        then:
        1 * client.loadPlaylist(source, httpInterface, "PLabc123", VIDEO_ID)
    }

    def "watch URL with an RD-prefixed playlist routes to loadMix"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/watch?v=${VIDEO_ID}&list=RDabc123")
        router.route(client)

        then:
        1 * client.loadMix(source, httpInterface, "RDabc123", VIDEO_ID)
    }

    @Unroll
    def "watch URL with excluded playlist prefix #prefix falls back to loadVideo"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/watch?v=${VIDEO_ID}&list=${prefix}xyz")
        router.route(client)

        then:
        1 * client.loadVideo(source, httpInterface, VIDEO_ID)
        0 * client.loadPlaylist(*_)

        where:
        prefix << ["LL", "WL", "LM"]
    }

    def "playlist URL routes to loadPlaylist with no video id"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/playlist?list=PLabc123")
        router.route(client)

        then:
        1 * client.loadPlaylist(source, httpInterface, "PLabc123", null)
    }

    def "playlist URL with an RD-prefixed id routes to loadMix"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/playlist?list=RDabc123")
        router.route(client)

        then:
        1 * client.loadMix(source, httpInterface, "RDabc123", "abc123")
    }

    def "bare direct video id routes to loadVideo when allowed"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, VIDEO_ID)
        router.route(client)

        then:
        1 * client.loadVideo(source, httpInterface, VIDEO_ID)
    }

    def "bare direct video id is unhandled when direct video ids are disallowed"() {
        given:
        def source = manager(true, false, true)

        expect:
        source.getRouter(httpInterface, VIDEO_ID) == null
    }

    def "bare direct playlist id routes to loadPlaylist when allowed"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, "PLabc123def456")
        router.route(client)

        then:
        1 * client.loadPlaylist(source, httpInterface, "PLabc123def456", null)
    }

    def "bare direct playlist id is unhandled when direct playlist ids are disallowed"() {
        given:
        def source = manager(true, true, false)

        expect:
        source.getRouter(httpInterface, "PLabc123def456") == null
    }

    @Unroll
    def "shorthand URL #url resolves to loadVideo"() {
        given:
        def source = manager()
        def client = Mock(Client)

        when:
        def router = source.getRouter(httpInterface, url)
        router.route(client)

        then:
        1 * client.loadVideo(source, httpInterface, VIDEO_ID)

        where:
        url << [
            "https://youtu.be/${VIDEO_ID}".toString(),
            "https://www.youtube.com/shorts/${VIDEO_ID}".toString(),
            "https://www.youtube.com/live/${VIDEO_ID}".toString(),
            "https://www.youtube.com/embed/${VIDEO_ID}".toString(),
        ]
    }

    def "completely unrecognized identifier is unhandled"() {
        given:
        def source = manager()

        expect:
        source.getRouter(httpInterface, "this is not a url or id") == null
    }

    def "watch_videos URL follows the redirect and routes based on the resolved watch URL"() {
        given:
        def source = manager()
        def client = Mock(Client)

        def body = "ok".getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(body)
        entity.getContentLength() >> body.length

        def redirectLocations = new RedirectLocations()
        redirectLocations.add(new URI("https://www.youtube.com/watch?v=${VIDEO_ID}&list=PLabc123"))

        def context = HttpClientContext.create()
        context.setRedirectLocations(redirectLocations)

        httpInterface.getContext() >> context
        httpInterface.execute(_) >> response

        when:
        def router = source.getRouter(httpInterface, "https://www.youtube.com/watch_videos?video_ids=aaa,bbb,ccc")
        router.route(client)

        then:
        1 * client.loadPlaylist(source, httpInterface, "PLabc123", VIDEO_ID)
    }

    def "watch_videos URL with no redirect throws a FriendlyException"() {
        given:
        def source = manager()

        def body = "ok".getBytes(StandardCharsets.UTF_8)
        def response = Mock(ClassicHttpResponse)
        def entity = Mock(HttpEntity)
        response.getCode() >> 200
        response.getEntity() >> entity
        entity.getContent() >> new ByteArrayInputStream(body)
        entity.getContentLength() >> body.length

        def context = HttpClientContext.create()

        httpInterface.getContext() >> context
        httpInterface.execute(_) >> response

        when:
        source.getRouter(httpInterface, "https://www.youtube.com/watch_videos?video_ids=aaa,bbb,ccc")

        then:
        thrown(com.sedmelluq.discord.lavaplayer.tools.FriendlyException)
    }
}
