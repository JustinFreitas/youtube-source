package dev.lavalink.youtube.clients.skeleton

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.lavalink.youtube.CannotBeLoaded
import dev.lavalink.youtube.clients.Web
import spock.lang.Specification
import spock.lang.Unroll

class ClientPlayabilityStatusSpec extends Specification {

    Client client = new Web()

    def "OK status returns the OK playability status"() {
        given:
        def status = JsonBrowser.parse('{"status":"OK"}')

        expect:
        client.getPlayabilityStatus(status, true) == Client.PlayabilityStatus.OK
    }

    def "ERROR status throws a FriendlyException with the given reason"() {
        given:
        def status = JsonBrowser.parse('{"status":"ERROR","reason":"Video unavailable"}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "Video unavailable"
    }

    def "UNPLAYABLE status throws with the subreason simpleText"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"simpleText":"Blocked in your country"}}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "Blocked in your country"
    }

    def "UNPLAYABLE status joins subreason runs when no simpleText is present"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"runs":[{"text":"Line one"},{"text":"Line two"}]}}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "Line one\nLine two"
    }

    def "UNPLAYABLE status returns NON_EMBEDDABLE when embedding is disabled and throwOnNotOk is false"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"simpleText":"Playback on other websites has been disabled by the video owner"}}}}
        ''')

        expect:
        client.getPlayabilityStatus(status, false) == Client.PlayabilityStatus.NON_EMBEDDABLE
    }

    def "UNPLAYABLE status still throws when embedding is disabled but throwOnNotOk is true"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"simpleText":"Playback on other websites has been disabled by the video owner"}}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        thrown(FriendlyException)
    }

    def "UNPLAYABLE status with no discoverable reason throws a suspicious FriendlyException"() {
        given:
        def status = JsonBrowser.parse('{"status":"UNPLAYABLE"}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "This video is unplayable."
        ex.severity == FriendlyException.Severity.SUSPICIOUS
    }

    def "LOGIN_REQUIRED status with a private-video reason throws CannotBeLoaded"() {
        given:
        def status = JsonBrowser.parse('{"status":"LOGIN_REQUIRED","reason":"This video is private."}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(CannotBeLoaded)
        (ex.cause as FriendlyException).message == "This is a private video."
    }

    def "LOGIN_REQUIRED status with an age-restriction reason throws a FriendlyException"() {
        given:
        def status = JsonBrowser.parse('{"status":"LOGIN_REQUIRED","reason":"This video may be inappropriate for some users."}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "This video requires age verification."
    }

    def "LOGIN_REQUIRED status with any other reason throws a generic login-required FriendlyException"() {
        given:
        def status = JsonBrowser.parse('{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you are not a bot"}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "This video requires login."
    }

    def "CONTENT_CHECK_REQUIRED status throws with the unplayable reason"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"CONTENT_CHECK_REQUIRED","reason":"Content warning",
                "errorScreen":{"playerErrorMessageRenderer":{}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "Content warning"
    }

    def "LIVE_STREAM_OFFLINE status with a trailer renderer throws a trailer-specific FriendlyException"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"LIVE_STREAM_OFFLINE","errorScreen":{"ypcTrailerRenderer":{}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "This trailer cannot be loaded."
    }

    def "LIVE_STREAM_OFFLINE status without a trailer renderer throws with the unplayable reason"() {
        given:
        def status = JsonBrowser.parse('''
            {"status":"LIVE_STREAM_OFFLINE","reason":"Stream ended",
                "errorScreen":{"playerErrorMessageRenderer":{}}}
        ''')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "Stream ended"
    }

    @Unroll
    def "unrecognized status #status throws a generic anonymous-viewing FriendlyException"() {
        given:
        def json = JsonBrowser.parse("{\"status\":\"${status}\"}")

        when:
        client.getPlayabilityStatus(json, true)

        then:
        def ex = thrown(FriendlyException)
        ex.message == "This video cannot be viewed anonymously."

        where:
        status << ["LOGIN_REQUIRED_FOO", "SOME_NEW_STATUS", ""]
    }

    def "missing status block throws a plain RuntimeException"() {
        given:
        def status = JsonBrowser.parse('{}')

        when:
        client.getPlayabilityStatus(status, true)

        then:
        def ex = thrown(RuntimeException)
        !(ex instanceof FriendlyException)
        ex.message == "No playability status block."
    }

    def "getUnplayableReason prefers subreason simpleText over the top-level reason"() {
        given:
        def status = JsonBrowser.parse('''
            {"reason":"top level reason","errorScreen":{"playerErrorMessageRenderer":
                {"subreason":{"simpleText":"specific reason"}}}}
        ''')

        expect:
        client.getUnplayableReason(status) == "specific reason"
    }

    def "getUnplayableReason falls back to the top-level reason when no subreason exists"() {
        given:
        def status = JsonBrowser.parse('{"reason":"top level reason"}')

        expect:
        client.getUnplayableReason(status) == "top level reason"
    }
}
