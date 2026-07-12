package dev.lavalink.youtube.track.format

import org.apache.hc.core5.http.ContentType
import spock.lang.Specification

class TrackFormatsSpec extends Specification {

    private static StreamFormat format(
        String contentType,
        long bitrate = 128000,
        long audioChannels = 2,
        boolean isDefaultAudioTrack = true,
        boolean isDrc = false
    ) {
        new StreamFormat(
            ContentType.parse(contentType),
            0,
            bitrate,
            1000,
            audioChannels,
            "https://example.com/stream",
            null,
            null,
            "sig",
            isDefaultAudioTrack,
            isDrc
        )
    }

    def "getBestFormat picks the only available default audio format"() {
        given:
        def opus = format("audio/webm; codecs=opus")
        def formats = new TrackFormats([opus], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(opus)
    }

    def "getBestFormat prefers a lower FormatInfo ordinal regardless of bitrate"() {
        given:
        def opus = format("audio/webm; codecs=opus", 64000)
        def aac = format("audio/mp4; codecs=mp4a.40.2", 256000)
        def formats = new TrackFormats([aac, opus], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(opus)
    }

    def "getBestFormat skips opus with more than 2 audio channels once a best candidate is already set"() {
        // isBetterFormat() only rejects on the multi-channel-opus check when comparing against an
        // existing best candidate (its "other == null" bootstrap check short-circuits before that
        // point for the very first format seen) -- so the non-opus format must be evaluated first.
        given:
        def aac = format("audio/mp4; codecs=mp4a.40.2", 128000, 2)
        def multiChannelOpus = format("audio/webm; codecs=opus", 128000, 6)
        def formats = new TrackFormats([aac, multiChannelOpus], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(aac)
    }

    def "getBestFormat rejects a DRC candidate once a non-DRC best is already set"() {
        // Same "other == null" bootstrap quirk as above: the explicit DRC-rejection branch only
        // fires when comparing against an existing non-DRC best, so it must be evaluated first.
        given:
        def nonDrc = format("audio/webm; codecs=opus", 128000, 2, true, false)
        def drc = format("audio/webm; codecs=opus", 128000, 2, true, true)
        def formats = new TrackFormats([nonDrc, drc], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(nonDrc)
    }

    def "getBestFormat prefers higher bitrate when ordinal and DRC are equal"() {
        given:
        def low = format("audio/webm; codecs=opus", 64000)
        def high = format("audio/webm; codecs=opus", 192000)
        def formats = new TrackFormats([low, high], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(high)
    }

    def "getBestFormat ignores non-default-audio-track formats"() {
        given:
        def nonDefault = format("audio/webm; codecs=opus", 320000, 2, false)
        def defaultTrack = format("audio/mp4; codecs=mp4a.40.2", 64000, 2, true)
        def formats = new TrackFormats([nonDefault, defaultTrack], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(defaultTrack)
    }

    def "getBestFormat ignores formats with an unrecognized content type"() {
        given:
        def unknown = format("application/octet-stream", 320000)
        def known = format("audio/mp4; codecs=mp4a.40.2", 64000)
        def formats = new TrackFormats([unknown, known], "https://example.com/player.js")

        expect:
        formats.getBestFormat().is(known)
    }

    def "getBestFormat throws when no format qualifies"() {
        given:
        def formats = new TrackFormats([format("application/octet-stream")], "https://example.com/player.js")

        when:
        formats.getBestFormat()

        then:
        thrown(RuntimeException)
    }
}
