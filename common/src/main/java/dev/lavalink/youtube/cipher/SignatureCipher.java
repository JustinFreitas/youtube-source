package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Describes one signature cipher
 */
public class SignatureCipher {
    private static final Logger log = LoggerFactory.getLogger(SignatureCipher.class);

    public final String timestamp;
    public final String globalVars;
    public final String sigActions;
    public final String sigFunction;
    public final String nFunction;
    public final String rawScript;

    public SignatureCipher(@NotNull String timestamp,
                           @NotNull String globalVars,
                           @NotNull String sigActions,
                           @NotNull String sigFunction,
                           @NotNull String nFunction,
                           @NotNull String rawScript) {
        this.timestamp = timestamp;
        this.globalVars = globalVars;
        this.sigActions = sigActions;
        this.sigFunction = sigFunction;
        this.nFunction = nFunction;
        this.rawScript = rawScript;
    }

    /**
     * @param text Text to apply the cipher on
     * @return The result of the cipher on the input text
     */
    public String apply(@NotNull String text,
                        @NotNull ScriptEngine scriptEngine) throws ScriptException, NoSuchMethodException {
        Object loadedCipher = scriptEngine.get("loaded_cipher_id");
        if (!java.util.Objects.equals(loadedCipher, this.hashCode())) {
            scriptEngine.eval(globalVars + ";" + sigActions + ";decrypt_sig=" + sigFunction);
            scriptEngine.put("loaded_cipher_id", this.hashCode());
        }
        return (String) ((Invocable) scriptEngine).invokeFunction("decrypt_sig", text);
    }

//  /**
//   * @param text Text to apply the cipher on
//   * @return The result of the cipher on the input text
//   */
//  public String apply(@NotNull String text) {
//    StringBuilder builder = new StringBuilder(text);
//
//    for (CipherOperation operation : operations) {
//      switch (operation.type) {
//        case SWAP:
//          int position = operation.parameter % text.length();
//          char temp = builder.charAt(0);
//          builder.setCharAt(0, builder.charAt(position));
//          builder.setCharAt(position, temp);
//          break;
//        case REVERSE:
//          builder.reverse();
//          break;
//        case SLICE:
//        case SPLICE:
//          builder.delete(0, operation.parameter);
//          break;
//        default:
//          throw new IllegalStateException("All branches should be covered");
//      }
//    }
//
//    return builder.toString();
//  }

    /**
     * @param text         Text to transform
     * @param scriptEngine JavaScript engine to execute function
     * @return The result of the n parameter transformation
     */
    public String transform(@NotNull String text, @NotNull ScriptEngine scriptEngine)
        throws ScriptException, NoSuchMethodException {
        Object loadedNsig = scriptEngine.get("loaded_nsig_id");
        if (!java.util.Objects.equals(loadedNsig, this.hashCode())) {
            scriptEngine.eval(globalVars + ";decrypt_nsig=" + nFunction);
            scriptEngine.put("loaded_nsig_id", this.hashCode());
        }
        return (String) ((Invocable) scriptEngine).invokeFunction("decrypt_nsig", text);
    }

//  /**
//   * @param operation The operation to add to this cipher
//   */
//  public void addOperation(@NotNull CipherOperation operation) {
//    operations.add(operation);
//  }
//
//  /**
//   * @return True if the cipher contains no operations.
//   */
//  public boolean isEmpty() {
//    return operations.isEmpty();
//  }
}
