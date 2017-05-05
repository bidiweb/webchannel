package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.support.Support;
import bidiweb.webchannel.client.support.Support.Debugger;
import bidiweb.webchannel.client.support.Support.HttpRequest;
import bidiweb.webchannel.client.support.Support.RequestErrorCode;
import bidiweb.webchannel.client.support.Support.RequestReadyState;
import bidiweb.webchannel.client.support.Support.RequestStat;
import bidiweb.webchannel.client.support.Support.ServerReachability;
import bidiweb.webchannel.client.support.Support.Uri;
import bidiweb.webchannel.client.support.Support.UriBuilder;

import java.util.HashMap;
import java.util.Map;

class ChannelRequest implements Support.RequestReadyStateChangeHandler, Support.TimeoutHandler {

  private static final long TIMEOUT_MS = 45 * 1000;

  // polling support not needed
  // unique uri not needed

  // streaming throttling not implemented
  // observer (event handler) not implemented (or needed)

  private final Channel channel;
  private final Debugger channelDebug;
  private final Support support;
  private final StringBuilder responseText;

  private final long retryId;
  private final String rid;
  private final String sid;

  private Map<String, String> extraHeaders = null;
  private boolean successful = false;

  private long timeout;
  private Object watchDogTimer;
  private long watchDogTimeoutTime;
  private long requestStartTime;
  private Type type;
  private Uri baseUri;
  private UriBuilder requestUri;
  private String postData;

  private HttpRequest httpRequest;

  private int chunkStart;
  private String verb;
  private ErrorEnum lastError;
  private int lastStatusCode;
  private boolean sendClose;
  private boolean cancelled;
  private boolean decodeChunks;

  private enum Type {
    HTTP_REQUEST,
    CLOSE_REQUEST
  }

  public enum ErrorEnum {
    STATUS,
    NO_DATA,
    TIMEOUT,
    UNKNOWN_SESSION_ID,
    BAD_DATA,
    HANDLER_EXCEPTION,
    BROWSER_OFFLINE
  }

  public ChannelRequest(Support support, Channel channel, String sessionId, String requestId) {
    this(support, channel, sessionId, requestId, 1);
  }

  public ChannelRequest(
      Support support, Channel channel, String sessionId, String requestId, long retryId) {
    this.support = support;
    this.channel = channel;
    this.channelDebug = support.getDebugger();
    this.sid = sessionId;
    this.rid = requestId;
    this.retryId = retryId;
    this.timeout = TIMEOUT_MS;

    this.extraHeaders = null;
    this.successful = false;

    this.watchDogTimer = null;
    this.watchDogTimeoutTime = 0;
    this.requestStartTime = 0;
    this.type = null;
    this.baseUri = null;
    this.requestUri = null;
    this.postData = null;

    this.httpRequest = null;
    this.responseText = new StringBuilder();

    this.chunkStart = 0;
    this.verb = null;

    this.lastError = null;
    this.lastStatusCode = -1;
    this.sendClose = true;

    this.cancelled = false;
    this.decodeChunks = false;
  }

  public static ChannelRequest createChannelRequest(
      Support support, Channel channel, String sessionId, String requestId, long retryId) {
    return new ChannelRequest(support, channel, sessionId, requestId, retryId);
  }

  public static ChannelRequest createChannelRequest(
      Support support, Channel channel, String sessionId, String requestId) {
    return new ChannelRequest(support, channel, sessionId, requestId);
  }

  public static boolean supportsHttpStreaming() {
    return true;
  }

  public void setExtraHeaders(Map<String, String> extraHeaders) {
    this.extraHeaders = extraHeaders;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  public void cancel() {
    this.cancelled = true;
    this.cleanup();
  }

  public boolean isClosed() {
    return false;
  }

  public int getLastStatusCode() {
    return this.lastStatusCode;
  }

  public boolean getSuccess() {
    return this.successful;
  }

  public ErrorEnum getLastError() {
    return this.lastError;
  }

  public String getSessionId() {
    return this.sid;
  }

  public String getRequestId() {
    return this.rid;
  }

  public HttpRequest getHttpRequest() {
    return this.httpRequest;
  }

  public String getPostData() {
    return postData;
  }

  public long getRequestStartTime() {
    return this.requestStartTime;
  }

  public static String errorStringFromCode(ErrorEnum errorCode, int statusCode) {
    switch (errorCode) {
      case STATUS:
        return "Non-200 return code (" + statusCode + ")";
      case NO_DATA:
        return "HTTP failure (no data)";
      case TIMEOUT:
        return "HttpConnection timeout";
      default:
        return "Unknown error";
    }
  }

  private static final String INVALID_CHUNK = new String();

  private static final String INCOMPLETE_CHUNK_ = new String();

  public void httpPost(Uri uri, String postData, boolean decodeChunks) {
    this.type = Type.HTTP_REQUEST;
    this.baseUri = uri;
    this.postData = postData;
    this.decodeChunks = decodeChunks;

    sendHttp();
  }

  public void httpGet(Uri uri, boolean decodeChunks, boolean noClose) {
    this.type = Type.HTTP_REQUEST;
    this.baseUri = uri;
    this.postData = null;
    this.decodeChunks = decodeChunks;
    this.sendClose = !noClose;

    sendHttp();
  }

  private void sendHttp() {
    this.requestStartTime = System.currentTimeMillis();
    ensureWatchDogTimer();

    this.requestUri =
        support.newUriBuilder(this.baseUri).addQueryParameter("t", Long.toString(this.retryId));
    this.chunkStart = 0;

    this.httpRequest = this.channel.createHttpRequest();

    this.httpRequest.setReadyStateChangeHandler(this);

    Map<String, String> headers = new HashMap<>();
    if (this.extraHeaders != null) {
      headers.putAll(this.extraHeaders);
    }

    if (this.postData != null) {
      this.verb = "POST";
      headers.put("Content-Type", "application/x-www-form-urlencoded");
      this.httpRequest.send(this.requestUri, this.verb, this.postData, headers);
    } else {
      this.verb = "GET";
      if (this.sendClose) {
        headers.put("Connection", "close");
      }
      this.httpRequest.send(this.requestUri, this.verb, null, headers);
    }

    support.notifyServerReachabilityEvent(ServerReachability.REQUEST_MADE);
    channelDebug.httpRequest(this.verb, this.requestUri, this.rid, this.retryId, this.postData);
  }

  @Override
  public void onReadyStateChangeEvent(HttpRequest request) {
    support.onStartExecution();

    try {
      if (request == this.httpRequest) {
        this.httpRequest.drainResponseText(this.responseText);
        this.onReadyStateChanged();
      } else {
        channelDebug.warning("Called back with an unexpected http request");
      }
    } catch (Exception ex) {
      channelDebug.debug("Failed call to onReadyStateChangeEvent.");
      if (this.responseText.length() > 0) {
        channelDebug.dumpException(ex, "ResponseText: " + this.responseText);
      } else {
        channelDebug.dumpException(ex, "No response text");
      }
    } finally {
      support.onEndExecution();
    }
  }

  private void onReadyStateChanged() {
    RequestReadyState readyState = this.httpRequest.getReadyState();
    RequestErrorCode errorCode = this.httpRequest.getLastErrorCode();
    int statusCode = this.httpRequest.getStatus();

    if (readyState != RequestReadyState.COMPLETE
        && (readyState == RequestReadyState.INTERACTIVE
            && this.responseText.length() == 0)) {
      return;
    }

    if (!this.cancelled
        && readyState == RequestReadyState.COMPLETE
        && errorCode != RequestErrorCode.ABORT) {
      if (errorCode == RequestErrorCode.TIMEOUT || statusCode <= 0) {
        support.notifyServerReachabilityEvent(ServerReachability.REQUEST_FAILED);
      } else {
        support.notifyServerReachabilityEvent(ServerReachability.REQUEST_SUCCEEDED);
      }
    }

    cancelWatchDogTimer();

    int status = this.httpRequest.getStatus();
    this.lastStatusCode = status;
    if (responseText.length() == 0) {
      channelDebug.debug("No response text for uri " + this.requestUri + " status " + status);
    }
    this.successful = (status == 200);

    channelDebug.httpChannelResponseMetaData(
        this.verb, this.requestUri, this.rid, this.retryId, readyState, status);

    if (!this.successful) {
      if (status == 400 && responseText.indexOf("Unknown SID") > 0) {
        this.lastError = ErrorEnum.UNKNOWN_SESSION_ID;
        support.notifyStatEvent(RequestStat.REQUEST_UNKNOWN_SESSION_ID);
        channelDebug.warning("XMLHTTP Unknown SID (" + this.rid + ")");
      } else {
        this.lastError = ErrorEnum.STATUS;
        support.notifyStatEvent(RequestStat.REQUEST_BAD_STATUS);
        channelDebug.warning("XMLHTTP Bad status " + status + " (" + this.rid + ")");
      }
      this.cleanup();
      this.dispatchFailure();
      return;
    }

    if (this.decodeChunks) {
      this.decodeNextChunks(readyState, responseText);
    } else {
      channelDebug.httpChannelResponseText(this.rid, responseText, null);
      this.safeOnRequestData(responseText);
    }

    if (readyState == RequestReadyState.COMPLETE) {
      this.cleanup();
    }

    if (!this.successful) {
      return;
    }

    if (!this.cancelled) {
      if (readyState == RequestReadyState.COMPLETE) {
        this.channel.onRequestComplete(this);
      } else {
        this.successful = false;
        this.ensureWatchDogTimer();
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void decodeNextChunks(RequestReadyState readyState, StringBuilder responseText) {
    boolean decodeNextChunksSuccessful = true;
    while (!this.cancelled && this.chunkStart < responseText.length()) {
      String chunkText = this.getNextChunk(responseText);
      if (chunkText == ChannelRequest.INCOMPLETE_CHUNK_) {
        if (readyState == RequestReadyState.COMPLETE) {
          this.lastError = ErrorEnum.BAD_DATA;
          support.notifyStatEvent(RequestStat.REQUEST_INCOMPLETE_DATA);
          decodeNextChunksSuccessful = false;
        }
        channelDebug.httpChannelResponseText(this.rid, null, "[Incomplete Response]");
        break;
      } else if (chunkText == ChannelRequest.INVALID_CHUNK) {
        this.lastError = ErrorEnum.BAD_DATA;
        support.notifyStatEvent(RequestStat.REQUEST_BAD_DATA);
        channelDebug.httpChannelResponseText(this.rid, responseText, "[Invalid Chunk]");
        decodeNextChunksSuccessful = false;
        break;
      } else {
        StringBuilder chunkTextBuffer = new StringBuilder(chunkText);
        channelDebug.httpChannelResponseText(this.rid, chunkTextBuffer, null);
        this.safeOnRequestData(chunkTextBuffer);
      }
    }
    if (readyState == RequestReadyState.COMPLETE && responseText.length() == 0) {
      this.lastError = ErrorEnum.NO_DATA;
      support.notifyStatEvent(RequestStat.REQUEST_NO_DATA);
      decodeNextChunksSuccessful = false;
    }
    this.successful = this.successful && decodeNextChunksSuccessful;
    if (!decodeNextChunksSuccessful) {
      channelDebug.httpChannelResponseText(
          this.rid, responseText, "[Invalid Chunked Response]");
      this.cleanup();
      this.dispatchFailure();
    }
  }

  private String getNextChunk(StringBuilder responseText) {
    int sizeStartIndex = this.chunkStart;
    int sizeEndIndex = responseText.indexOf("\n", sizeStartIndex);
    if (sizeEndIndex == -1) {
      return INCOMPLETE_CHUNK_;
    }

    String sizeAsString = responseText.substring(sizeStartIndex, sizeEndIndex);
    int size;
    try {
      size = Integer.parseInt(sizeAsString);
    } catch (Exception ex) {
      return INVALID_CHUNK;
    }

    int chunkStartIndex = sizeEndIndex + 1;
    if (chunkStartIndex + size > responseText.length()) {
      return INCOMPLETE_CHUNK_;
    }

    // TODO: Do not buffer the response body when it's no longer necessary.
    String chunkText = responseText.substring(chunkStartIndex, chunkStartIndex + size);
    this.chunkStart = chunkStartIndex + size;
    return chunkText;
  }

  private void safeOnRequestData(StringBuilder data) {
    try {
      this.channel.onRequestData(this, data.toString());
      support.notifyServerReachabilityEvent(ServerReachability.BACK_CHANNEL_ACTIVITY);
    } catch (Exception ex) {
      channelDebug.dumpException(ex, "Error in httprequest callback");
    }
  }

  private void dispatchFailure() {
    if (this.channel.isClosed() || this.cancelled) {
      return;
    }

    this.channel.onRequestComplete(this);
  }

  private void cleanup() {
    cancelWatchDogTimer();

    if (this.httpRequest != null) {
      this.httpRequest.setReadyStateChangeHandler(null);

      HttpRequest request = this.httpRequest;
      this.httpRequest = null;
      request.abort();
    }
  }

  private void ensureWatchDogTimer() {
    this.watchDogTimeoutTime = System.currentTimeMillis() + this.timeout;

    if (this.watchDogTimer != null) {
      throw new IllegalStateException("WatchDog timer not null");
    }

    this.watchDogTimer = support.setTimeout(this, this.timeout);
  }

  private void cancelWatchDogTimer() {
    if (this.watchDogTimer != null) {
      support.clearTimeout(this.watchDogTimer);
      this.watchDogTimer = null;
    }
  }

  public void sendCloseRequest(UriBuilder uri) {
    this.type = Type.CLOSE_REQUEST;
    this.baseUri = uri.getUri();
    this.requestUri = uri;
    this.verb = "GET";

    this.httpRequest = this.channel.createHttpRequest();
    this.httpRequest.send(this.requestUri, this.verb, null, null);

    this.requestStartTime = System.currentTimeMillis();
    this.ensureWatchDogTimer();
  }

  @Override
  public void onTimeout() {
    this.watchDogTimer = null;
    long now = System.currentTimeMillis();
    if (now - this.watchDogTimeoutTime >= 0) {
      this.handleTimeout();
    } else {
      channelDebug.warning("WatchDog timer called too early");
      this.startWatchDogTimer(this.watchDogTimeoutTime - now);
    }
  }

  private void startWatchDogTimer(long timeMs) {
    if (this.watchDogTimer != null) {
      throw new IllegalStateException("WatchDog timer not null");
    }
    this.watchDogTimer = support.setTimeout(this, timeMs);
  }

  private void handleTimeout() {
    if (this.successful) {
      this.channelDebug.severe("Received watchdog timeout even though request loaded successfully");
    }

    channelDebug.info("TIMEOUT: " + this.requestUri.toString());

    if (this.type != ChannelRequest.Type.CLOSE_REQUEST) {
      support.notifyServerReachabilityEvent(ServerReachability.REQUEST_FAILED);
      support.notifyStatEvent(RequestStat.REQUEST_TIMEOUT);
    }

    this.cleanup();

    this.lastError = ErrorEnum.TIMEOUT;
    this.dispatchFailure();
  }
}
