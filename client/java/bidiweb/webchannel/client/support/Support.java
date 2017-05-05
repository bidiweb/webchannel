package bidiweb.webchannel.client.support;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is used to abstract ALL platform specific dependencies,
 * which for now include: json, base64, url/urilbuilder/urlencoder,
 * http, debug, stats, timer.
 *
 * Platform-specific webchannel client implementation needs implement this class and pass it to
 * WebChannelTransports.
 *
 * <p>
 * Threading: Each WebChannel channel owns one instance of {@link Support}. Clients should ensure
 * that the same thread will be used for:
 * <ol>
 *   <li>Openning, reading from and writing to a webchannel.
 *   <li>Calling the onReadyStateChangeEvent() callback when received more HTTP data.
 *   <li>Calling the handler specified in setTimeout().
 * </ol>
 */
public abstract class Support {

  // redact not supported
  // execution hooks not supported (onstart/onend from timer)

  // ==== to be implemented by the implementation (provided by the app)

  public static abstract class Uri {
    // immutable
    @Override
    public abstract String toString();
  }

  public static abstract class UriBuilder implements Cloneable {
    public abstract UriBuilder addQueryParameter(String name, String value);

    public abstract String getAuthority();

    public abstract Uri getUri();

    @Override
    public abstract UriBuilder clone();

    @Override
    public abstract String toString();
  }

  public static abstract class UrlEncoder {
    public abstract String encode(String data);
  }

  public static abstract class Debugger {

    public abstract void info(String text);

    public void debug(String text) {
      this.info(text);
    }

    public abstract void warning(String text);

    public abstract void dumpException(Exception ex, String msg); // severe

    public abstract void severe(String text);

    /**
     * goog.asserts in JS.
     */
    public abstract void assertCondition(boolean condition, String text);

    public void httpRequest(String verb, UriBuilder uri, String id, long attempt, String postData) {
      // WARNING: Do not use info() log until we strip out the PII.
      debug(
          "HTTP REQ ("
          + id
          + ") [attempt "
          + attempt
          + "]: "
          + verb
          + "\n"
          + uri
          + "\n"
          + maybeRedactPostData(postData));
    }

    private String maybeRedactPostData(String data) {
      return data;
    }

    public void httpChannelResponseMetaData(
        String verb,
        UriBuilder uri,
        String id,
        long attempt,
        RequestReadyState readyState,
        int statusCode) {
      // WARNING: Do not use info() log until we strip out the PII.
      debug(
          "HTTP RESP ("
          + id
          + ") [ attempt "
          + attempt
          + "]: "
          + verb
          + "\n"
          + uri
          + "\n"
          + readyState
          + " "
          + statusCode);
    }

    public void httpChannelResponseText(String id, StringBuilder responseText, String desc) {
      // TODO: Optimized/finalized the logger part of the support code.
      // WARNING: Do not use info() log until we strip out the PII.
      debug(
          "HTTP TEXT ("
          + id
          + "): "
          + redactResponse(responseText)
          + (desc != null ? " " + desc : ""));
    }

    private StringBuilder redactResponse(StringBuilder data) {
      return data;
    }
  }

  /**
   * Class that tracks the state of an ongoing HTTP Request.
   *
   * For every HTTP request, WebChannel would create a new HttpRequest instance and then
   * call send() exactly once during the life cycle of this request.
   *
   * See {@link Support} for more info.
   */
  public static abstract class HttpRequest {
    private RequestReadyStateChangeHandler readyStateChangeHandler = null;

    public void setReadyStateChangeHandler(RequestReadyStateChangeHandler handler) {
      this.readyStateChangeHandler = handler;
    }

    public RequestReadyStateChangeHandler getReadyStateChangeHandler() {
      return this.readyStateChangeHandler;
    }

    public abstract String getResponseHeader(String name);

    /**
     * Appends any new response text to the provided buffer, and resets the
     * response body owned by this object.
     *
     * @param buffer The buffer to copy the response text to. If null, just
     *               reset the response body.
     */
    public void drainResponseText(StringBuilder buffer) {
    }

    public abstract RequestReadyState getReadyState();

    public abstract RequestErrorCode getLastErrorCode();

    public abstract int getStatus(); // HTTP status code

    /**
     * @param verb The HTTP method, supported values are "GET" and "POST".
     * @param postData Optional data to POST.
     * @param headers Optional header map.
     */
    public abstract void send(
        UriBuilder uri,
        String verb,
        @Nullable String postData,
        @Nullable Map<String, String> headers);

    public abstract void abort();
  }

  public interface RequestReadyStateChangeHandler {
    void onReadyStateChangeEvent(HttpRequest request);
  }

  public enum RequestReadyState {
    UNINITIALIZED,
    LOADING,
    LOADED, // whenever response headers are received
    INTERACTIVE, // whenever _new_ response data arrives (under 200)
    COMPLETE // whenever the response is finished (or aborted), EOF
  }

  /**
   * Error codes.
   */
  public enum RequestErrorCode {
    /**
     * There is no error condition.
     */
    NO_ERROR,

    /**
     * The most common error from iframeio, unfortunately, is that the browser
     * responded with an error page that is classed as a different domain. The
     * situations, are when a browser error page  is shown -- 404, access denied,
     * DNS failure, connection reset etc.)
     */
    ACCESS_DENIED,

    /**
     * Currently the only case where file not found will be caused is when the
     * code is running on the local file system and a non-IE browser makes a
     * request to a file that doesn't exist.
     */
    FILE_NOT_FOUND,

    /**
     * If Firefox shows a browser error page, such as a connection reset by
     * server or access denied, then it will fail silently without the error or
     * load handlers firing.
     */
    FF_SILENT_ERROR,

    /**
     * Custom error provided by the client through the error check hook.
     */
    CUSTOM_ERROR,

    /**
     * Exception was thrown while processing the request.
     */
    EXCEPTION,

    /**
     * The Http response returned a non-successful http status code.
     */
    HTTP_ERROR,

    /**
     * The request was aborted.
     */
    ABORT,

    /**
     * The request timed out.
     */
    TIMEOUT,

    /**
     * The resource is not available offline.
     */
    OFFLINE,
  }

  public enum RequestStat {
    CONNECT_ATTEMPT,
    ERROR_NETWORK,
    ERROR_OTHER,
    TEST_STAGE_ONE_START,
    TEST_STAGE_TWO_START,
    TEST_STAGE_TWO_DATA_ONE,
    TEST_STAGE_TWO_DATA_TWO,
    TEST_STAGE_TWO_DATA_BOTH,
    TEST_STAGE_ONE_FAILED,
    TEST_STAGE_TWO_FAILED,
    PROXY,
    NOPROXY,
    REQUEST_UNKNOWN_SESSION_ID,
    REQUEST_BAD_STATUS,
    REQUEST_INCOMPLETE_DATA,
    REQUEST_BAD_DATA,
    REQUEST_NO_DATA,
    REQUEST_TIMEOUT,
    BACKCHANNEL_MISSING,
    BACKCHANNEL_DEAD,
    BROWSER_OFFLINE
  }

  public enum ServerReachability {
    REQUEST_MADE,
    REQUEST_SUCCEEDED,
    REQUEST_FAILED,
    BACK_CHANNEL_ACTIVITY
  }

  // stats

  public void notifyStatEvent(RequestStat event) {
    // optional
  }

  public void notifyServerReachabilityEvent(ServerReachability event) {
    // optional
  }

  public void notifyTimingEvent(int size, long rtt, int retries) {
    // optional
  }

  public void onStartExecution() {
    // not needed
  }

  public void onEndExecution() {
    // not needed now
  }

  // timer (future) support

  public interface TimeoutHandler {
    void onTimeout();
  }

  /**
   * @param handler The event handler of the timeout event
   * @param timeout The timeout from present. This can be zero too.
   * @return  The timer object to hold
   */
  public abstract Object setTimeout(TimeoutHandler handler, long timeout);

  /**
   * @param timer The timer object to clear.
   */
  public abstract void clearTimeout(Object timer);

  // ==== to be implemented by the apps

  public static abstract class JsonEncoder {
    // to be defined
  }

  /**
   * The decoded value needs be a canned type. All primitive types need be
   * Java boxed typed. Everything else will be opaque objects to the
   * WebChannel code, i.e. the consumer of the decoder (as supplied by the
   * application). Numeric values need be of the Long type.
   *
   * However, for efficiency, decoded user messages (again, as Objects) will
   * be passed back to the application directly,
   */
  public static abstract class JsonDecoder {
    /**
     * Decode an array up to the specified maxDepth (maximum being 3).
     * All primitive types are boxed types.
     * Messages are Objects.
     * Arrays are of List type.
     */
    public abstract List<?> decodeArray(String data, int maxDepth) throws IllegalArgumentException;
  }

  // not used yet

  public static abstract class Base64Encoder {
    public abstract String encode(byte[] data);
  }

  public static abstract class Base64Decoder {
    public abstract byte[] decode(String data);
  }

  // ===== factory methods

  public abstract UriBuilder newUriBuilder(Uri uri);

  public abstract UriBuilder newUriBuilder(String uri); // parse()

  public abstract Debugger getDebugger();

  public abstract HttpRequest newHttpRequest();

  public abstract UrlEncoder getUrlEncoder();

  public JsonEncoder getJsonEncoder() {
    throw new UnsupportedOperationException("not needed for now.");
  }

  public abstract JsonDecoder getJsonDecoder();

  public abstract Base64Encoder getBase64Encoder();

  public abstract Base64Decoder getBase64Decoder();
}
