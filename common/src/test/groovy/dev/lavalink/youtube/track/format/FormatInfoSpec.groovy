package dev.lavalink.youtube.track.format

import org.apache.hc.core5.http.ContentType
import spock.lang.Specification
import spock.lang.Unroll

class FormatInfoSpec extends Specification {

    @Unroll
    def "get matches #contentType to #expected exactly"() {
        expect:
        FormatInfo.get(ContentType.parse(contentType)) == expected

        where:
        contentType                          || expected
        "audio/webm; codecs=opus"            || FormatInfo.WEBM_OPUS
        "audio/webm; codecs=vorbis"          || FormatInfo.WEBM_VORBIS
        "audio/mp4; codecs=mp4a.40.2"        || FormatInfo.MP4_AAC_LC
        "video/webm; codecs=vorbis"          || FormatInfo.WEBM_VIDEO_VORBIS
        "video/mp4; codecs=mp4a.40.2"        || FormatInfo.MP4_VIDEO_AAC_LC
    }

    def "get falls back to a substring codec match"() {
        expect:
        FormatInfo.get(ContentType.parse('audio/mp4; codecs="mp4a.40.2,something-else"')) == FormatInfo.MP4_AAC_LC
    }

    def "get returns null for a completely unknown content type"() {
        expect:
        FormatInfo.get(ContentType.parse("application/octet-stream")) == null
    }
}
