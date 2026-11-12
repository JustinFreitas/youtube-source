package dev.lavalink.youtube.clients

import spock.lang.Specification

class ClientConfigSpec extends Specification {

    def "withClientField nests the value under context.client"() {
        given:
        def config = new ClientConfig()

        when:
        config.withClientField("deviceModel", "Pixel 8")

        then:
        config.getRoot().context.client.deviceModel == "Pixel 8"
    }

    def "withClientName sets both the name property and the client field"() {
        given:
        def config = new ClientConfig()

        when:
        config.withClientName("ANDROID")

        then:
        config.getName() == "ANDROID"
        config.getRoot().context.client.clientName == "ANDROID"
    }

    def "withVisitorData sets both the visitorData property and the client field"() {
        given:
        def config = new ClientConfig()

        when:
        config.withVisitorData("visitor-123")

        then:
        config.getVisitorData() == "visitor-123"
        config.getRoot().context.client.visitorData == "visitor-123"
    }

    def "withVisitorData(null) is a no-op when no context exists yet"() {
        given:
        def config = new ClientConfig()

        when:
        config.withVisitorData(null)

        then:
        config.getVisitorData() == null
        !config.getRoot().containsKey("context")
    }

    def "withVisitorData(null) removes only the visitorData key, keeping sibling client fields"() {
        given:
        def config = new ClientConfig()
        config.withClientField("deviceModel", "Pixel 8")
        config.withVisitorData("visitor-123")

        when:
        config.withVisitorData(null)

        then:
        config.getVisitorData() == null
        !config.getRoot().context.client.containsKey("visitorData")
        config.getRoot().context.client.deviceModel == "Pixel 8"
    }

    def "withVisitorData(null) cleans up empty client and context maps"() {
        given:
        def config = new ClientConfig()
        config.withVisitorData("visitor-123")

        when:
        config.withVisitorData(null)

        then:
        config.getVisitorData() == null
        !config.getRoot().containsKey("context")
    }

    def "putOnceAndJoin returns the same map instance on repeated calls for the same key"() {
        given:
        def config = new ClientConfig()
        def root = config.getRoot()

        when:
        def first = config.putOnceAndJoin(root, "context")
        first.put("marker", "value")
        def second = config.putOnceAndJoin(root, "context")

        then:
        second.is(first)
        second.marker == "value"
    }

    def "copy deep-copies nested maps and preserves scalar properties including apiKey"() {
        given:
        def original = new ClientConfig()
            .withClientName("ANDROID")
            .withUserAgent("TestAgent/1.0")
            .withVisitorData("visitor-123")
            .withApiKey("AIzaTestKey123")
            .withRootField("tags", ["a", "b"])

        when:
        def copy = original.copy()

        then: "the top-level root map and scalar properties are copied"
        !copy.getRoot().is(original.getRoot())
        copy.getName() == original.getName()
        copy.getUserAgent() == original.getUserAgent()
        copy.getVisitorData() == original.getVisitorData()
        copy.getApiKey() == "AIzaTestKey123"

        when: "context.thirdParty is added via withThirdPartyEmbedUrl on the copy"
        copy.withThirdPartyEmbedUrl("https://example.com/embed")

        then: "context.thirdParty exists only on the copy and does not pollute original.context"
        copy.getRoot().context.thirdParty.embedUrl == "https://example.com/embed"
        !original.getRoot().context.containsKey("thirdParty")

        when: "an existing nested map (context.client) and list are mutated via the copy"
        copy.withClientField("extra", "onlyOnCopy")
        (copy.getRoot().tags as List).add("c")

        then: "nested maps and lists are isolated from the original"
        copy.getRoot().context.client.extra == "onlyOnCopy"
        !original.getRoot().context.client.containsKey("extra")
        original.getRoot().tags == ["a", "b"]
    }

    def "toJsonString renders the nested structure as JSON"() {
        given:
        def config = new ClientConfig()
        config.withClientName("ANDROID")

        expect:
        config.toJsonString() == '{"context":{"client":{"clientName":"ANDROID"}}}'
    }
}
