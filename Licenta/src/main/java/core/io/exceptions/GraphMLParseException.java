package core.io.exceptions;

/**
 * Custom exception for the GraphMLParser.
 * @author Apetrei Razvan-Emanuel
 */

public class GraphMLParseException extends Exception {
    public GraphMLParseException(String message) {
        super(message);
    }

    public GraphMLParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
