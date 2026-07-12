package dev.lavalink.youtube.clients.skeleton

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.lavalink.youtube.clients.Web
import dev.lavalink.youtube.track.format.StreamFormat
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class StreamingNonMusicClientSpec extends Specification {

    // Web is a StreamingNonMusicClient with no extractFormat override, so this exercises the
    // base implementation directly.
    StreamingNonMusicClient client = new Web()

    private static String rawFormatUrl(String n = "NVALUE") {
        "https://example.com/videoplayback?id=abc&n=${n}&other=x"
    }

    def "extractFormat parses a plain (non-ciphered) format"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"bitrate":128000,
             "contentLength":"98765","audioChannels":2,
             "url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then:
        result
        formats.size() == 1
        def format = formats[0] as StreamFormat
        format.itag == 251
        format.bitrate == 128000L
        format.contentLength == 98765L
        format.audioChannels == 2L
        format.nParameter == "NVALUE"
        format.signature == null
        format.signatureKey == "signature"
        format.isDefaultAudioTrack()
        !format.isDrc()
    }

    def "extractFormat decodes a signatureCipher into url, signature and signature key"() {
        given:
        def encodedUrl = URLEncoder.encode(rawFormatUrl(), StandardCharsets.UTF_8.name())
        def cipher = "s=SIGVALUE&sp=sig&url=${encodedUrl}"
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"bitrate":128000,
             "contentLength":"98765","audioChannels":2,
             "signatureCipher":"${cipher}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then:
        result
        formats.size() == 1
        def format = formats[0] as StreamFormat
        format.getUrl().toString() == rawFormatUrl()
        format.signature == "SIGVALUE"
        format.signatureKey == "sig"
        format.nParameter == "NVALUE"
    }

    def "extractFormat fails when neither a url nor a cipher url is present"() {
        given:
        def json = JsonBrowser.parse('{"mimeType":"audio/webm; codecs=opus","itag":251}')
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then:
        !result
        formats.isEmpty()
    }

    def "extractFormat skips (non-fatally) a non-live format missing contentLength"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then: "not fatal, but the format is not added"
        result
        formats.isEmpty()
    }

    def "extractFormat keeps a live format even without contentLength"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, true)

        then:
        result
        formats.size() == 1
    }

    def "extractFormat keeps a legacy itag 18 format even without contentLength"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":18,"url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then:
        result
        formats.size() == 1
    }

    def "extractFormat fails gracefully on an unparseable mimeType"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"","itag":251,"contentLength":"98765","url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        def result = client.extractFormat(json, formats, false)

        then:
        !result
        formats.isEmpty()
    }

    def "extractFormat returns false immediately for a null or non-map json node"() {
        given:
        def formats = []

        expect:
        !client.extractFormat(JsonBrowser.parse("null"), formats, false)
        !client.extractFormat(JsonBrowser.parse("[1,2,3]"), formats, false)
        formats.isEmpty()
    }

    def "extractFormat defaults audioIsDefault to true and isDrc to false when absent"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"bitrate":128000,
             "contentLength":"98765","url":"${rawFormatUrl()}"}
        """)
        def formats = []

        when:
        client.extractFormat(json, formats, false)

        then:
        def format = formats[0] as StreamFormat
        format.isDefaultAudioTrack()
        !format.isDrc()
    }

    def "extractFormat respects an explicit non-default audio track and DRC flag"() {
        given:
        def json = JsonBrowser.parse("""
            {"mimeType":"audio/webm; codecs=opus","itag":251,"bitrate":128000,
             "contentLength":"98765","url":"${rawFormatUrl()}",
             "audioTrack":{"audioIsDefault":false},"isDrc":true}
        """)
        def formats = []

        when:
        client.extractFormat(json, formats, false)

        then:
        def format = formats[0] as StreamFormat
        !format.isDefaultAudioTrack()
        format.isDrc()
    }
}
