package dev.lavalink.youtube.cipher

/**
 * Small, hand-built stand-ins for a YouTube player script (base.js) -- just enough JS shape to
 * satisfy each extraction regex in LocalSignatureCipherManager, without depending on a live
 * network fetch of a real (and constantly-changing) YouTube script.
 */
class CipherScriptFixtures {

    public static final String TIMESTAMP_LINE =
        'var _yt_config={"SIGNATURE_TIMESTAMP":true,signatureTimestamp:12345};'

    public static final String GLOBAL_VARS_LINE =
        'var Zx="abcdefghijklmnopqrstuvwxyz".split("");'

    public static final String ACTIONS_LINE =
        'var Zy={aa:function(a){a.reverse()},bb:function(a,b){a.splice(0,b)},' +
            'cc:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}};'

    public static final String SIG_FUNCTION_LINE =
        'function abc(a){a=a.split("");Zy.bb(a,3);return a.join("")};'

    public static final String N_FUNCTION_LINE =
        'function(a){var b=a[Zy[0]](Zy[1]);if(typeof XYZ==="undefined")return a;' +
            'try{doStuff()}catch(d){return"enhanced_except_"+a}return b[Zy[4]](Zy[5])};'

    static String fullScript() {
        [TIMESTAMP_LINE, GLOBAL_VARS_LINE, ACTIONS_LINE, SIG_FUNCTION_LINE, N_FUNCTION_LINE].join("\n")
    }
}
