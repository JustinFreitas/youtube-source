package dev.lavalink.youtube.cipher

import org.mozilla.javascript.engine.RhinoScriptEngineFactory
import spock.lang.Specification

import javax.script.ScriptEngine

class SignatureCipherSpec extends Specification {

    def "should correctly apply signature cipher and transform n-parameter"() {
        given:
        def cipher = new SignatureCipher(
            "12345",
            "var base_val = '_suffix';",
            "var actions = { rev: function(a) { return a.split('').reverse().join(''); } };",
            "function(a) { return actions.rev(a) + base_val; }",
            "function(a) { return a + base_val + '_nsig'; }",
            "mock raw script source"
        )
        def engine = new RhinoScriptEngineFactory().getScriptEngine()

        when:
        def decryptedSig = cipher.apply("hello", engine)
        def decryptedNsig = cipher.transform("world", engine)

        then:
        decryptedSig == "olleh_suffix"
        decryptedNsig == "world_suffix_nsig"
    }

    def "should leverage scriptEngine binding cache to avoid redundant eval() calls"() {
        given:
        def cipher = new SignatureCipher(
            "12345",
            "var count = 0;",
            "var actions = { inc: function(a) { count++; return a + count; } };",
            "function(a) { return actions.inc(a); }",
            "function(a) { return a; }",
            "mock raw script source"
        )
        // A spy/mock of ScriptEngine to trace eval calls is tricky because ScriptEngine is a standard interface,
        // but we can test it behaviorally by seeing if the evaluation state is preserved
        // or by manually checking that the "loaded_cipher_id" attribute is set on the engine context.
        def engine = new RhinoScriptEngineFactory().getScriptEngine()

        when: "first execution"
        def res1 = cipher.apply("test", engine)

        then: "caching key is set on the engine binding context"
        res1 == "test1"
        engine.get("loaded_cipher_id") == cipher.hashCode()

        when: "second execution"
        // If it re-evaluated, 'count' would be re-declared or reset to 0 (depending on JS semantics,
        // re-running 'var count = 0;' resets it). Since it is cached, it shouldn't re-eval the script,
        // so count will increment to 2.
        def res2 = cipher.apply("test", engine)

        then:
        res2 == "test2"

        when: "a different cipher runs"
        def anotherCipher = new SignatureCipher(
            "67890",
            "var count = 10;",
            "var actions = { inc: function(a) { count++; return a + count; } };",
            "function(a) { return actions.inc(a); }",
            "function(a) { return a; }",
            "another mock raw script"
        )
        def res3 = anotherCipher.apply("test", engine)

        then: "it detects hash change, re-evaluates the new cipher script and updates the engine cache id"
        res3 == "test11"
        engine.get("loaded_cipher_id") == anotherCipher.hashCode()
    }
}
