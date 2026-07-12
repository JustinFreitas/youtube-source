package dev.lavalink.youtube.cipher

import spock.lang.Specification
import spock.lang.Unroll

import static dev.lavalink.youtube.cipher.CipherScriptFixtures.*

class LocalSignatureCipherManagerSpec extends Specification {

    LocalSignatureCipherManager manager = new LocalSignatureCipherManager()

    def "extractFromScript extracts all cipher components from a valid script"() {
        given:
        def script = fullScript()

        when:
        def cipher = manager.extractFromScript(script, "https://example.com/valid.js")

        then:
        cipher.timestamp == "12345"
        cipher.globalVars.contains("Zx")
        cipher.sigActions.contains("Zy")
        cipher.sigFunction.contains("Zy.bb")
        cipher.rawScript == script
    }

    def "extractFromScript strips the n-function short-circuit guard"() {
        given:
        def script = fullScript()

        when:
        def cipher = manager.extractFromScript(script, "https://example.com/shortcircuit.js")

        then: "the short-circuit guard is removed"
        !cipher.nFunction.contains("typeof XYZ")

        and: "the rest of the n-function body is preserved"
        cipher.nFunction.contains("enhanced_except_")
        cipher.nFunction.contains("return b[Zy[4]](Zy[5])")
    }

    def "getScriptTimestamp extracts the timestamp from a valid script"() {
        expect:
        manager.getScriptTimestamp(null, fullScript(), "https://example.com/valid.js") == "12345"
    }

    def "getScriptTimestamp throws when no timestamp is present"() {
        given:
        def script = [GLOBAL_VARS_LINE, ACTIONS_LINE, SIG_FUNCTION_LINE, N_FUNCTION_LINE].join("\n")

        when:
        manager.getScriptTimestamp(null, script, "https://example.com/no-timestamp.js")

        then:
        def ex = thrown(ScriptExtractionException)
        ex.failureType == ScriptExtractionException.ExtractionFailureType.TIMESTAMP_NOT_FOUND
    }

    @Unroll
    def "extractFromScript throws #failureType when the script is missing #description"() {
        when:
        manager.extractFromScript(script, "https://example.com/missing-${failureType}.js")

        then:
        def ex = thrown(ScriptExtractionException)
        ex.failureType == failureType

        where:
        description       | script                                                                              || failureType
        "global variables" | [TIMESTAMP_LINE, ACTIONS_LINE, SIG_FUNCTION_LINE, N_FUNCTION_LINE].join("\n")      || ScriptExtractionException.ExtractionFailureType.VARIABLES_NOT_FOUND
        "sig actions"       | [TIMESTAMP_LINE, GLOBAL_VARS_LINE, SIG_FUNCTION_LINE, N_FUNCTION_LINE].join("\n")  || ScriptExtractionException.ExtractionFailureType.SIG_ACTIONS_NOT_FOUND
        "sig function"      | [TIMESTAMP_LINE, GLOBAL_VARS_LINE, ACTIONS_LINE, N_FUNCTION_LINE].join("\n")       || ScriptExtractionException.ExtractionFailureType.DECIPHER_FUNCTION_NOT_FOUND
        "n function"        | [TIMESTAMP_LINE, GLOBAL_VARS_LINE, ACTIONS_LINE, SIG_FUNCTION_LINE].join("\n")     || ScriptExtractionException.ExtractionFailureType.N_FUNCTION_NOT_FOUND
    }
}
