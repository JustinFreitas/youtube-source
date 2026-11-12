package dev.lavalink.youtube.clients

import spock.lang.Specification

import java.lang.reflect.Modifier

class WebPoTokenIsolationSpec extends Specification {

    def cleanup() {
        Web.setPoTokenAndVisitorData(null, null)
        WebEmbedded.setPoTokenAndVisitorData(null, null)
        MWeb.setPoTokenAndVisitorData(null, null)
    }

    def "poToken fields on Web, WebEmbedded, and MWeb are volatile and isolated per class"() {
        expect:
        Modifier.isVolatile(Web.getDeclaredField("poToken").modifiers)
        Modifier.isVolatile(WebEmbedded.getDeclaredField("poToken").modifiers)
        Modifier.isVolatile(MWeb.getDeclaredField("poToken").modifiers)
    }

    def "setting Web poToken does not leak to MWeb or WebEmbedded playback URIs"() {
        given:
        def web = new Web()
        def webEmbedded = new WebEmbedded()
        def mweb = new MWeb()
        def playbackUri = new URI("https://rr1---sn-test.googlevideo.com/videoplayback?itag=251")

        when:
        Web.setPoTokenAndVisitorData("WEB_POT_123", "WEB_VD_123")

        then:
        web.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=WEB_POT_123")
        !mweb.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=")
        !webEmbedded.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=")

        when: "WebEmbedded sets its own poToken"
        WebEmbedded.setPoTokenAndVisitorData("EMBED_POT_456", "EMBED_VD_456")

        then: "Web retains its own poToken, WebEmbedded uses its own, and MWeb still appends none"
        Web.poToken == "WEB_POT_123"
        WebEmbedded.poToken == "EMBED_POT_456"
        MWeb.poToken == null
        web.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=WEB_POT_123")
        webEmbedded.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=EMBED_POT_456")
        !mweb.transformPlaybackUri(playbackUri, playbackUri).toString().contains("pot=")
    }
}
