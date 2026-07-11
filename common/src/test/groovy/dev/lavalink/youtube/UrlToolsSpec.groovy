package dev.lavalink.youtube

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import spock.lang.Specification
import spock.lang.Unroll

class UrlToolsSpec extends Specification {

    @Unroll
    def "should parse url path and query params for: #url"() {
        when:
        def info = UrlTools.getUrlInfo(url, true)

        then:
        info.path == expectedPath
        info.parameters == expectedParams

        where:
        url                                                              | expectedPath | expectedParams
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ"                   | "/watch"     | [v: "dQw4w9WgXcQ"]
        "http://music.youtube.com/playlist?list=PL123&index=5"          | "/playlist"  | [list: "PL123", index: "5"]
        "www.youtube.com/shorts/12345678901?foo=bar"                    | "/shorts/12345678901" | [foo: "bar"]
        "https://youtu.be/dQw4w9WgXcQ?t=42"                             | "/dQw4w9WgXcQ" | [t: "42"]
        "youtu.be/dQw4w9WgXcQ"                                          | "/dQw4w9WgXcQ" | [:]
    }

    def "should throw FriendlyException for completely invalid URL formats"() {
        when:
        UrlTools.getUrlInfo("http:// ", false)

        then:
        thrown(FriendlyException)
    }

    def "should retry with shorter prefix if URL contains invalid trailing characters"() {
        when:
        // Backslash at the end is an invalid URI char, should retry with substring.
        // Slicing it off results in the parameter v: dQw4w9WgXc (missing the Q due to index offset)
        def info = UrlTools.getUrlInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ\\", true)

        then:
        info.path == "/watch"
        info.parameters == [v: "dQw4w9WgXc"]
    }
}
