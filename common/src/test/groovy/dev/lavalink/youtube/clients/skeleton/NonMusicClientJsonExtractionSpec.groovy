package dev.lavalink.youtube.clients.skeleton

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.clients.Web
import spock.lang.Specification

class NonMusicClientJsonExtractionSpec extends Specification {

    NonMusicClient client = new Web()
    YoutubeAudioSourceManager source = new YoutubeAudioSourceManager()

    // --- extractPlaylistError ---

    def "extractPlaylistError returns null when there are no alerts"() {
        expect:
        client.extractPlaylistError(JsonBrowser.parse('{}')) == null
    }

    def "extractPlaylistError returns null when no alert is of type ERROR"() {
        given:
        def json = JsonBrowser.parse('{"alerts":[{"alertRenderer":{"type":"INFO","text":{"simpleText":"hi"}}}]}')

        expect:
        client.extractPlaylistError(json) == null
    }

    def "extractPlaylistError prefers simpleText when present"() {
        given:
        def json = JsonBrowser.parse('''
            {"alerts":[{"alertRenderer":{"type":"ERROR","text":{"simpleText":"This playlist does not exist."}}}]}
        ''')

        expect:
        client.extractPlaylistError(json) == "This playlist does not exist."
    }

    def "extractPlaylistError joins runs when simpleText is absent"() {
        given:
        def json = JsonBrowser.parse('''
            {"alerts":[{"alertRenderer":{"type":"ERROR","text":{"runs":[{"text":"Part one, "},{"text":"part two."}]}}}]}
        ''')

        expect:
        client.extractPlaylistError(json) == "Part one, part two."
    }

    // --- extractPlaylistName ---

    def "extractPlaylistName extracts the title from the playlist metadata"() {
        // Web overrides extractPlaylistName to read from a different JSON shape than the
        // NonMusicClient base implementation.
        given:
        def json = JsonBrowser.parse('''
            {"metadata":{"playlistMetadataRenderer":{"title":"My Playlist"}}}
        ''')

        expect:
        client.extractPlaylistName(json) == "My Playlist"
    }

    def "extractPlaylistName returns null when the header is missing"() {
        expect:
        client.extractPlaylistName(JsonBrowser.parse('{}')) == null
    }

    // --- extractPlaylistVideoList / extractPlaylistContinuationVideos ---

    def "extractPlaylistVideoList navigates to the nested playlistVideoListRenderer"() {
        // Web overrides extractPlaylistVideoList with an extra itemSectionRenderer level
        // compared to the NonMusicClient base implementation.
        given:
        def json = JsonBrowser.parse('''
            {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
                {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                    {"itemSectionRenderer":{"contents":[
                        {"playlistVideoListRenderer":{"marker":"here"}}
                    ]}}
                ]}}}}
            ]}}}
        ''')

        expect:
        client.extractPlaylistVideoList(json).get("marker").text() == "here"
    }

    def "extractPlaylistContinuationVideos navigates to the appendContinuationItemsAction contents"() {
        // Web overrides extractPlaylistContinuationVideos with a different response shape than the
        // NonMusicClient base implementation.
        given:
        def json = JsonBrowser.parse('''
            {"onResponseReceivedActions":[
                {"appendContinuationItemsAction":{"continuationItems":{"marker":"page2"}}}
            ]}
        ''')

        expect:
        client.extractPlaylistContinuationVideos(json).get("marker").text() == "page2"
    }

    // --- extractPlaylistContinuationToken ---

    def "extractPlaylistContinuationToken extracts the token from a continuationCommand"() {
        // Web overrides extractPlaylistContinuationToken with a different response shape than the
        // NonMusicClient base implementation.
        given:
        def json = JsonBrowser.parse('''
            [{"continuationItemRenderer":{"continuationEndpoint":{
                "continuationCommand":{"token":"TOKEN123"}
            }}}]
        ''')

        expect:
        client.extractPlaylistContinuationToken(json) == "TOKEN123"
    }

    def "extractPlaylistContinuationToken falls back to the second commandExecutorCommand entry"() {
        given:
        def json = JsonBrowser.parse('''
            [{"continuationItemRenderer":{"continuationEndpoint":{
                "continuationCommand":{},
                "commandExecutorCommand":{"commands":[
                    {"irrelevant":true},
                    {"continuationCommand":{"token":"TOKEN456"}}
                ]}
            }}}]
        ''')

        expect:
        client.extractPlaylistContinuationToken(json) == "TOKEN456"
    }

    def "extractPlaylistContinuationToken returns null when there are no continuations"() {
        expect:
        client.extractPlaylistContinuationToken(JsonBrowser.parse('[]')) == null
    }

    // --- extractPlaylistTracks ---

    private static String playlistVideoRenderer(String videoId, boolean playable = true, boolean hasAuthor = true) {
        def isPlayable = playable ? '"isPlayable":true,' : ''
        def author = hasAuthor ? '"shortBylineText":{"runs":[{"text":"Some Author"}]},' : ''
        return """
            {"playlistVideoRenderer":{
                ${isPlayable}
                "videoId":"${videoId}",
                "title":{"simpleText":"Title ${videoId}"},
                ${author}
                "lengthSeconds":"125"
            }}
        """
    }

    def "extractPlaylistTracks adds playable tracks and skips unplayable or region-blocked ones"() {
        given:
        def json = JsonBrowser.parse("""
            {"contents":[
                ${playlistVideoRenderer("aaaaaaaaaaa")},
                ${playlistVideoRenderer("bbbbbbbbbbb", false, true)},
                ${playlistVideoRenderer("ccccccccccc", true, false)}
            ]}
        """)
        def tracks = []

        when:
        client.extractPlaylistTracks(json, tracks, source)

        then:
        tracks.size() == 1
        tracks[0].info.identifier == "aaaaaaaaaaa"
        tracks[0].info.title == "Title aaaaaaaaaaa"
        tracks[0].info.author == "Some Author"
        tracks[0].info.length == 125000L
    }

    def "extractPlaylistTracks works directly on a values list without a contents wrapper"() {
        given:
        def json = JsonBrowser.parse("[${playlistVideoRenderer("aaaaaaaaaaa")}]")
        def tracks = []

        when:
        client.extractPlaylistTracks(json, tracks, source)

        then:
        tracks.size() == 1
    }

    def "extractPlaylistTracks is a no-op for null json"() {
        given:
        def tracks = []

        when:
        client.extractPlaylistTracks(JsonBrowser.parse("null"), tracks, source)

        then:
        tracks.isEmpty()
    }

    // --- extractAudioTrack ---

    def "extractAudioTrack returns null for null json"() {
        expect:
        client.extractAudioTrack(JsonBrowser.parse("null"), source) == null
    }

    def "extractAudioTrack returns null when lengthText is absent (livestream)"() {
        given:
        def json = JsonBrowser.parse('{"videoId":"aaaaaaaaaaa","title":{"simpleText":"Live"}}')

        expect:
        client.extractAudioTrack(json, source) == null
    }

    def "extractAudioTrack returns null when unplayableText is present"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "unplayableText":{"simpleText":"This video is unavailable"}}
        ''')

        expect:
        client.extractAudioTrack(json, source) == null
    }

    def "extractAudioTrack prefers headline over title when both are present"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "headline":{"runs":[{"text":"Headline Title"}]},
             "title":{"runs":[{"text":"Regular Title"}]},
             "shortBylineText":{"runs":[{"text":"Some Author"}]}}
        ''')

        when:
        def track = client.extractAudioTrack(json, source)

        then:
        track.info.title == "Headline Title"
    }

    def "extractAudioTrack falls back to title.simpleText when no runs are present"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "title":{"simpleText":"Simple Title"},
             "shortBylineText":{"runs":[{"text":"Some Author"}]}}
        ''')

        when:
        def track = client.extractAudioTrack(json, source)

        then:
        track.info.title == "Simple Title"
    }

    def "extractAudioTrack prefers longBylineText over shortBylineText for the author"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "title":{"simpleText":"Title"},
             "longBylineText":{"runs":[{"text":"Long Author"}]},
             "shortBylineText":{"runs":[{"text":"Short Author"}]}}
        ''')

        when:
        def track = client.extractAudioTrack(json, source)

        then:
        track.info.author == "Long Author"
    }

    def "extractAudioTrack defaults the author to Unknown artist when absent"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "title":{"simpleText":"Title"}}
        ''')

        when:
        def track = client.extractAudioTrack(json, source)

        then:
        track.info.author == "Unknown artist"
    }

    def "extractAudioTrack parses the duration from lengthText"() {
        given:
        def json = JsonBrowser.parse('''
            {"videoId":"aaaaaaaaaaa","lengthText":{"simpleText":"3:45"},
             "title":{"simpleText":"Title"},
             "shortBylineText":{"runs":[{"text":"Author"}]}}
        ''')

        when:
        def track = client.extractAudioTrack(json, source)

        then:
        track.info.length == 225000L
        track.info.identifier == "aaaaaaaaaaa"
    }

    // --- extractSearchResults ---

    def "extractSearchResults extracts tracks and filters out unplayable entries"() {
        // Web overrides extractSearchResults with a different response shape (and a
        // videoRenderer key instead of compactVideoRenderer) than the NonMusicClient base implementation.
        given:
        def json = JsonBrowser.parse('''
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[
                    {"videoRenderer":{
                        "videoId":"aaaaaaaaaaa",
                        "lengthText":{"simpleText":"3:45"},
                        "title":{"simpleText":"Result One"},
                        "shortBylineText":{"runs":[{"text":"Author One"}]}
                    }},
                    {"videoRenderer":{
                        "videoId":"bbbbbbbbbbb",
                        "title":{"simpleText":"Livestream, no lengthText"}
                    }}
                ]}}
            ]}}}}}
        ''')

        when:
        def tracks = client.extractSearchResults(source, json)

        then:
        tracks.size() == 1
        tracks[0].info.identifier == "aaaaaaaaaaa"
        tracks[0].info.title == "Result One"
    }

    def "extractPlaylistTracks extracts tracks from modern lockupViewModel layout"() {
        given:
        def json = JsonBrowser.parse('''
            {"contents": [
                {"lockupViewModel": {
                    "contentId": "xyz12345678",
                    "metadata": {
                        "lockupMetadataViewModel": {
                            "title": {"content": "Lockup Title"},
                            "metadataRows": [
                                {"metadataParts": [{"text": {"content": "Lockup Artist"}}]}
                            ]
                        }
                    },
                    "contentImage": {
                        "thumbnailViewModel": {
                            "overlays": [
                                {"thumbnailOverlayTimeStatusRenderer": {"text": {"simpleText": "4:20"}}}
                            ]
                        }
                    }
                }}
            ]}
        ''')
        def tracks = []

        when:
        client.extractPlaylistTracks(json, tracks, source)

        then:
        tracks.size() == 1
        tracks[0].info.identifier == "xyz12345678"
        tracks[0].info.title == "Lockup Title"
        tracks[0].info.author == "Lockup Artist"
        tracks[0].info.length == 260000L
    }

    def "extractPlaylistName falls back to pageHeaderRenderer when playlistHeaderRenderer is absent"() {
        given:
        def json = JsonBrowser.parse('''
            {"header": {"pageHeaderRenderer": {"pageTitle": "Modern Header Playlist"}}}
        ''')

        expect:
        client.extractPlaylistName(json) == "Modern Header Playlist"
    }

    def "AndroidVr does not declare a shadowed options field and falls back to super.extractPlaylistName"() {
        given:
        def customOptions = new dev.lavalink.youtube.clients.ClientOptions()
        customOptions.setSearching(false)
        def vr = new dev.lavalink.youtube.clients.AndroidVr(customOptions)
        def metadataJson = JsonBrowser.parse('{"metadata":{"playlistMetadataRenderer":{"title":"Metadata Fallback Title"}}}')
        def modernHeaderJson = JsonBrowser.parse('{"header":{"pageHeaderRenderer":{"pageTitle":"PageHeader Fallback Title"}}}')

        expect:
        dev.lavalink.youtube.clients.AndroidVr.getDeclaredFields().every { it.name != "options" }
        vr.getOptions().is(customOptions)
        !vr.getOptions().getSearching()
        vr.extractPlaylistName(metadataJson) == "Metadata Fallback Title"
        vr.extractPlaylistName(modernHeaderJson) == "PageHeader Fallback Title"
    }

    def "Ios, MWeb, and Android fall back to super.extractPlaylistName when primary path is absent"() {
        given:
        def ios = new dev.lavalink.youtube.clients.Ios()
        def mweb = new dev.lavalink.youtube.clients.MWeb()
        def android = new dev.lavalink.youtube.clients.Android(dev.lavalink.youtube.clients.ClientOptions.DEFAULT, false)
        def metadataJson = JsonBrowser.parse('{"metadata":{"playlistMetadataRenderer":{"title":"Fallback Playlist"}}}')
        def headerRunsJson = JsonBrowser.parse('{"header":{"playlistHeaderRenderer":{"title":{"runs":[{"text":"Runs Header Playlist"}]}}}}')

        expect:
        ios.extractPlaylistName(metadataJson) == "Fallback Playlist"
        ios.extractPlaylistName(headerRunsJson) == "Runs Header Playlist"
        mweb.extractPlaylistName(metadataJson) == "Fallback Playlist"
        mweb.extractPlaylistName(headerRunsJson) == "Runs Header Playlist"
        android.extractPlaylistName(metadataJson) == "Fallback Playlist"
        android.extractPlaylistName(headerRunsJson) == "Runs Header Playlist"
    }

    def "Ios and MWeb extractPlaylistVideoList support lockupViewModel items and fallback layouts"() {
        given:
        def ios = new dev.lavalink.youtube.clients.Ios()
        def mweb = new dev.lavalink.youtube.clients.MWeb()
        def lockupSingleColumnJson = JsonBrowser.parse('''
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                    {"itemSectionRenderer":{"contents":[
                        {"lockupViewModel":{"contentId":"vid12345678","metadata":{"lockupMetadataViewModel":{"title":{"content":"Modern Track"}}}}}
                    ]}}
                ]}}}}
            ]}}}
        ''')
        def fallbackDirectListJson = JsonBrowser.parse('''
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                    {"playlistVideoListRenderer":{"marker":"directList"}}
                ]}}}}
            ]}}}
        ''')

        when:
        def iosTracks = []
        ios.extractPlaylistTracks(ios.extractPlaylistVideoList(lockupSingleColumnJson), iosTracks, source)
        def mwebTracks = []
        mweb.extractPlaylistTracks(mweb.extractPlaylistVideoList(lockupSingleColumnJson), mwebTracks, source)

        then:
        iosTracks.size() == 1
        iosTracks[0].info.identifier == "vid12345678"
        mwebTracks.size() == 1
        mwebTracks[0].info.identifier == "vid12345678"
        ios.extractPlaylistVideoList(fallbackDirectListJson).get("marker").text() == "directList"
        mweb.extractPlaylistVideoList(fallbackDirectListJson).get("marker").text() == "directList"
    }
}
