/** Thrown when saving/loading fails. The message is written so it can be shown directly to the player. */
public class SaveException extends RuntimeException {
    public SaveException(String message) { super(message); }
    public SaveException(String message, Throwable cause) { super(message, cause); }
}
