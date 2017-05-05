package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.WebChannelConstants;
import bidiweb.webchannel.client.WebChannelOptions;
import bidiweb.webchannel.client.support.Support;
import bidiweb.webchannel.client.support.Support.Debugger;
import bidiweb.webchannel.client.support.Support.HttpRequest;
import bidiweb.webchannel.client.support.Support.RequestStat;
import bidiweb.webchannel.client.support.Support.TimeoutHandler;
import bidiweb.webchannel.client.support.Support.Uri;
import bidiweb.webchannel.client.support.Support.UriBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class WebChannelBase implements Channel, NetUtils.TestNetworkCallback {
  // no sub-domains
  // no streaming throttling
  // no batch delivery
  // no port-override or relative data path
  // no logsaver

  private Support support;

  private int clientVersion;
  private int serverVersion;

  private List<Wire.QueuedMap> outgoingMaps;
  private List<Wire.QueuedMap> pendingMaps;
  private Debugger channelDebug;
  private ConnectionState connState;
  private Map<String, String> extraHeaders;
  private Map<String, String> initHeaders;
  private Map<String, String> extraParams;
  private String httpSessionIdParam;
  private String httpSessionId;
  private ChannelRequest backChannelRequest;
  private String path;
  private UriBuilder forwardChannelUri;
  private UriBuilder backChannelUri;
  private long nextRid;
  private long nextMapId;
  private boolean failFast;
  private Handler handler;
  private Object forwardChannelTimer;
  private Object backChannelTimer;
  private Object deadBackChannelTimer;
  private BaseTestChannel connectionTest;
  private boolean useChunked;
  private boolean allowChunkedMode;
  private long lastArrayId;
  private long lastPostResponseArrayId;
  private int lastStatusCode;
  private int forwardChannelRetryCount;
  private int backChannelRetryCount;
  private long backChannelAttemptId;
  private long baseRetryDelayMs;
  private long retryDelaySeedMs;
  private int forwardChannelMaxRetries;
  private long forwardChannelRequestTimeoutMs;
  private String sid;
  private ForwardChannelRequestPool forwardChannelRequestPool;
  private WireV8 wireCodec;
  private boolean backgroundChannelTest;
  private int channelVersion;
  private State state;


  public WebChannelBase(
      Support support, WebChannelOptions options, int clientVersion, ConnectionState conn) {
    this.support = support;
    this.clientVersion = clientVersion;
    this.serverVersion = 0;
    this.outgoingMaps = new ArrayList<>();
    this.pendingMaps = new ArrayList<>();
    this.channelDebug = support.getDebugger();
    this.connState = conn == null ? new ConnectionState() : conn;
    this.extraHeaders = null;
    this.initHeaders = null;
    this.extraParams = null;
    this.httpSessionIdParam = null;
    this.httpSessionId = null;
    this.backChannelRequest = null;
    this.path = null;
    this.forwardChannelUri = null;
    this.backChannelUri = null;
    this.nextRid = 0;
    this.nextMapId = 0;
    this.failFast = false;
    this.handler = null;
    this.forwardChannelTimer = null;
    this.backChannelTimer = null;
    this.deadBackChannelTimer = null;
    this.connectionTest = null;
    this.useChunked = true; // default changed
    this.allowChunkedMode = true;
    this.lastArrayId = -1;
    this.lastPostResponseArrayId = -1;
    this.lastStatusCode = -1;
    this.forwardChannelRetryCount = 0;
    this.backChannelRetryCount = 0;
    this.backChannelAttemptId = 0;
    this.baseRetryDelayMs = 5 * 1000;
    this.retryDelaySeedMs = 10 * 1000;
    this.forwardChannelMaxRetries = 2;
    this.forwardChannelRequestTimeoutMs = 20 * 1000;
    this.sid = "";
    this.forwardChannelRequestPool =
        new ForwardChannelRequestPool(options == null ? 0 : options.getConcurrentRequestLimit());
    this.wireCodec = new WireV8(this.support);
    this.backgroundChannelTest = options != null && options.getBackgroundChannelTest();
    this.channelVersion = Wire.LATEST_CHANNEL_VERSION;
    this.state = State.INIT;
  }

  public enum State {
    CLOSED,
    INIT,
    OPENING,
    OPENED
  }

  public enum ErrorEnum {
    OK,
    REQUEST_FAILED,
    LOGGED_OUT,
    NO_DATA,
    UNKNOWN_SESSION_ID,
    STOP,
    NETWORK,
    BAD_DATA,
    BAD_RESPONSE
  }

  public enum ChannelType {
    FORWARD_CHANNEL,
    BACK_CHANNEL
  }

  public static final long FORWARD_CHANNEL_RETRY_TIMEOUT = 20 * 1000;

  public static final int BACK_CHANNEL_MAX_RETRIES = 3;

  public static final long RTT_ESTIMATE = 3 * 1000;

  public static final int INACTIVE_CHANNEL_RETRY_FACTOR = 2;

  private static final int MAX_MAPS_PER_REQUEST = 1000;

  public static final long OUTSTANDING_DATA_BACKCHANNEL_RETRY_CUTOFF = 37500;

  public ForwardChannelRequestPool getForwardChannelRequestPool() {
    return this.forwardChannelRequestPool;
  }

  public Object getWireCodec() {
    return wireCodec;
  }

  public Debugger getChannelDebug() {
    return this.channelDebug;
  }

  public void setChannelDebug(Debugger channelDebug) {
    this.channelDebug = channelDebug;
  }

  public String getSessionId() {
    return this.sid;
  }

  public Map<String, String> getExtraHeaders() {
    return this.extraHeaders;
  }

  public void setExtraHeaders(Map<String, String> extraHeaders) {
    this.extraHeaders = extraHeaders;
  }

  public Map<String, String> getInitHeaders() {
    return this.initHeaders;
  }

  public void setInitHeaders(Map<String, String> initHeaders) {
    this.initHeaders = initHeaders;
  }

  public void setHttpSessionIdParam(String httpSessionIdParam) {
    this.httpSessionIdParam = httpSessionIdParam;
  }

  public String getHttpSessionIdParam() {
    return this.httpSessionIdParam;
  }

  public void setHttpSessionId(String httpSessionId) {
    this.httpSessionId = httpSessionId;
  }

  public String getHttpSessionId() {
    return this.httpSessionId;
  }

  @Override
  public boolean getBackgroundChannelTest() {
    return this.backgroundChannelTest;
  }

  public Handler getHandler() {
    return this.handler;
  }

  public void setHandler(Handler handler) {
    this.handler = handler;
  }

  public boolean isBuffered() {
    return !this.useChunked;
  }

  public boolean getAllowChunkedMode() {
    return this.allowChunkedMode;
  }

  public void setAllowChunkedMode(boolean allowChunkedMode) {
    this.allowChunkedMode = allowChunkedMode;
  }

  public int getForwardChannelMaxRetries() {
    return this.failFast ? 0 : this.forwardChannelMaxRetries;
  }

  public void setForwardChannelMaxRetries(int retries) {
    this.forwardChannelMaxRetries = retries;
  }

  public void setForwardChannelRequestTimeout(long timeoutMs) {
    this.forwardChannelRequestTimeoutMs = timeoutMs;
  }

  public int getBackChannelMaxRetries() {
    return BACK_CHANNEL_MAX_RETRIES;
  }

  public boolean isClosed() {
    return this.state == State.CLOSED;
  }

  public State getState() {
    return this.state;
  }

  public int getLastStatusCode() {
    return this.lastStatusCode;
  }

  public long getLastArrayId() {
    return this.lastArrayId;
  }

  public boolean hasOutstandingRequests() {
    return this.getOutstandingRequests() != 0;
  }

  public int getOutstandingRequests() {
    int count = 0;
    if (this.backChannelRequest != null) {
      count++;
    }
    count += this.forwardChannelRequestPool.getRequestCount();
    return count;
  }

  public void connect(
      String testPath,
      String channelPath,
      Map<String, String> extraParams,
      String oldSessionId,
      String oldArrayId) {
    channelDebug.debug("connect()");

    support.notifyStatEvent(RequestStat.CONNECT_ATTEMPT);

    this.path = channelPath;
    this.extraParams = extraParams != null ? extraParams : new HashMap<String, String>();

    if (oldSessionId != null && oldArrayId != null) {
      this.extraParams.put("OSID", oldSessionId);
      this.extraParams.put("OAID", oldArrayId);
    }

    if (this.backgroundChannelTest) {
      this.channelDebug.debug("connect() bypassed channel-test.");
      this.connState.setHandshakeResult(Collections.<String>emptyList());
      this.connState.setBufferingProxyResult(false);
    }

    this.connectTest(testPath);
  }

  private void connectTest(String testPath) {
    channelDebug.debug("connectTest_()");
    if (!this.okToMakeRequest()) {
      return;
    }
    this.connectionTest = new BaseTestChannel(support, this);
    this.connectionTest.setExtraHeaders(this.extraHeaders);
    this.connectionTest.connect(testPath);
  }

  public void disconnect() {
    channelDebug.debug("disconnect()");

    this.cancelRequests();

    if (this.state == State.OPENED) {
      long rid = this.nextRid++;
      UriBuilder uri = forwardChannelUri.clone();
      uri.addQueryParameter("SID", this.sid);
      uri.addQueryParameter("RID", Long.toString(rid));
      uri.addQueryParameter("TYPE", "terminate");

      // Add the reconnect parameters.
      this.addAdditionalParams(uri);

      ChannelRequest request =
          ChannelRequest.createChannelRequest(support, this, this.sid, Long.toString(rid));
      request.sendCloseRequest(uri);
    }

    this.onClose();
  }

  private void addAdditionalParams(UriBuilder uri) {
    if (this.handler != null) {
      Map<String, String> params = this.handler.getAdditionalParams(this);
      if (params != null) {
        for (String key : params.keySet()) {
          uri.addQueryParameter(key, params.get(key));
        }
      }
    }
  }

  private boolean okToMakeRequest() {
    if (this.handler != null) {
      ErrorEnum result = this.handler.okToMakeRequest(this);
      if (result != WebChannelBase.ErrorEnum.OK) {
        channelDebug.debug("Handler returned error code from okToMakeRequest");
        this.signalError(result);
        return false;
      }
    }
    return true;
  }

  private void signalError(ErrorEnum error) {
    channelDebug.info("Error code " + error);
    if (error == WebChannelBase.ErrorEnum.REQUEST_FAILED) {
      // Create a separate Internet connection to check
      // if it"s a server error or user"s network error.
      Uri imageUri = null;
      if (this.handler != null) {
        imageUri = this.handler.getNetworkTestImageUri(this);
      }
      NetUtils.testNetwork(this, imageUri);
    } else {
      support.notifyStatEvent(RequestStat.ERROR_OTHER);
    }
    this.onError(error);
  }

  public void onTestNetworkResult(boolean networkUp) {
    if (networkUp) {
      channelDebug.info("Successfully pinged google.com");
      support.notifyStatEvent(RequestStat.ERROR_OTHER);
    } else {
      channelDebug.info("Failed to ping google.com");
      support.notifyStatEvent(RequestStat.ERROR_NETWORK);
    }
  }

  private void onSuccess() {
    if (this.handler != null) {
      this.handler.channelSuccess(this, this.pendingMaps);
    }
  }

  private void onError(ErrorEnum error) {
    channelDebug.debug("HttpChannel: error - " + error);
    this.state = State.CLOSED;
    if (this.handler != null) {
      this.handler.channelError(this, error);
    }
    this.onClose();
    this.cancelRequests();
  }

  private void onClose() {
    this.state = WebChannelBase.State.CLOSED;
    this.lastStatusCode = -1;
    if (this.handler != null) {
      if (this.pendingMaps.size() == 0 && this.outgoingMaps.size() == 0) {
        this.handler.channelClosed(this, null, null);
      } else {
        channelDebug.debug(
            "Number of undelivered maps"
                + ", pending: "
                + this.pendingMaps.size()
                + ", outgoing: "
                + this.outgoingMaps.size());

        List<Wire.QueuedMap> copyOfPendingMaps = new ArrayList<>(this.pendingMaps);
        List<Wire.QueuedMap> copyOfUndeliveredMaps = new ArrayList<>(outgoingMaps);
        this.pendingMaps.clear();
        this.outgoingMaps.clear();

        this.handler.channelClosed(this, copyOfPendingMaps, copyOfUndeliveredMaps);
      }
    }
  }

  private void cancelRequests() {
    if (this.connectionTest != null) {
      this.connectionTest.abort();
      this.connectionTest = null;
    }

    if (this.backChannelRequest != null) {
      this.backChannelRequest.cancel();
      this.backChannelRequest = null;
    }

    if (this.backChannelTimer != null) {
      support.clearTimeout(this.backChannelTimer);
      this.backChannelTimer = null;
    }

    this.clearDeadBackchannelTimer();

    this.forwardChannelRequestPool.cancel();

    if (this.forwardChannelTimer != null) {
      support.clearTimeout(this.forwardChannelTimer);
      this.forwardChannelTimer = null;
    }
  }

  private void clearDeadBackchannelTimer() {
    if (deadBackChannelTimer != null) {
      support.clearTimeout(this.deadBackChannelTimer);
      this.deadBackChannelTimer = null;
    }
  }

  private void connectChannel() {
    channelDebug.debug("connectChannel()");
    this.ensureInState(State.INIT, State.CLOSED);
    this.forwardChannelUri = this.getForwardChannelUri(this.path);
    this.ensureForwardChannel();
  }

  private void ensureInState(State... states) {
    for (State state : states) {
      if (this.state == state) {
        return;
      }
    }
    channelDebug.assertCondition(false, "Unexpected channel state: " + this.state);
  }

  private void ensureForwardChannel() {
    if (this.forwardChannelRequestPool.isFull()) {
      return;
    }

    if (this.forwardChannelTimer != null) {
      return;
    }

    this.forwardChannelTimer =
        support.setTimeout(
            new TimeoutHandler() {
              public void onTimeout() {
                WebChannelBase.this.onStartForwardChannelTimer(null);
              }
            },
            0);
    this.forwardChannelRetryCount = 0;
  }

  private void onStartForwardChannelTimer(ChannelRequest retryRequest) {
    this.forwardChannelTimer = null;
    this.startForwardChannel(retryRequest);
  }

  private void startForwardChannel(ChannelRequest retryRequest) {
    channelDebug.debug("startForwardChannel");
    if (!this.okToMakeRequest()) {
      return; // channel is cancelled
    } else if (this.state == State.INIT) {
      if (retryRequest != null) {
        channelDebug.severe("Not supposed to retry the open");
        return;
      }
      this.open();
      this.state = State.OPENING;
    } else if (this.state == State.OPENED) {
      if (retryRequest != null) {
        this.makeForwardChannelRequest(retryRequest);
        return;
      }

      if (this.outgoingMaps.size() == 0) {
        channelDebug.debug("startForwardChannel_ returned: " + "nothing to send");
        return;
      }

      if (this.forwardChannelRequestPool.isFull()) {
        channelDebug.severe("startForwardChannel_ returned: " + "connection already in progress");
        return;
      }

      this.makeForwardChannelRequest(null);
      channelDebug.debug("startForwardChannel_ finished, sent request");
    }
  }

  public void makeForwardChannelRequest(ChannelRequest retryRequest) {
    long rid;
    String requestText;
    if (retryRequest != null) {
      this.requeuePendingMaps();
      rid = this.nextRid - 1; // Must use last RID
      requestText = this.dequeueOutgoingMaps();
    } else {
      rid = this.nextRid++;
      requestText = this.dequeueOutgoingMaps();
    }

    UriBuilder uri = this.forwardChannelUri.clone();
    uri.addQueryParameter("SID", this.sid);
    uri.addQueryParameter("RID", Long.toString(rid));
    uri.addQueryParameter("AID", Long.toString(this.lastArrayId));

    this.addAdditionalParams(uri);

    ChannelRequest request =
        ChannelRequest.createChannelRequest(
            support, this, this.sid, Long.toString(rid), this.forwardChannelRetryCount + 1);
    request.setExtraHeaders(this.extraHeaders);

    request.setTimeout(
        Math.round(this.forwardChannelRequestTimeoutMs * 0.50)
            + Math.round(this.forwardChannelRequestTimeoutMs * 0.50 * Math.random()));
    this.forwardChannelRequestPool.addRequest(request);
    request.httpPost(uri.getUri(), requestText, true);
  }

  private void open() {
    this.channelDebug.debug("open_()");
    this.nextRid = (long) Math.floor(Math.random() * 100000); // FIXME

    long rid = this.nextRid++;
    ChannelRequest request =
        ChannelRequest.createChannelRequest(support, this, "", Long.toString(rid));

    // mix the init headers
    Map<String, String> extraHeaders = this.extraHeaders;
    if (this.initHeaders != null && !this.initHeaders.isEmpty()) {
      if (extraHeaders != null && !extraHeaders.isEmpty()) {
        extraHeaders = new HashMap<>(extraHeaders);
        extraHeaders.putAll(this.initHeaders);
      } else {
        extraHeaders = this.initHeaders;
      }
    }

    request.setExtraHeaders(this.extraHeaders);
    String requestText = this.dequeueOutgoingMaps();
    UriBuilder uri = this.forwardChannelUri.clone();
    uri.addQueryParameter("RID", Long.toString(rid));
    if (this.clientVersion > 0) {
      uri.addQueryParameter("CVER", Integer.toString(this.clientVersion));
    }

    if (this.getBackgroundChannelTest() && this.getHttpSessionIdParam() != null) {
      uri.addQueryParameter(
          WebChannelConstants.X_HTTP_SESSION_ID, this.getHttpSessionIdParam());
    }

    this.addAdditionalParams(uri);

    this.forwardChannelRequestPool.addRequest(request);
    request.httpPost(uri.getUri(), requestText, true);
  }

  private void requeuePendingMaps() {
    this.outgoingMaps.addAll(0, this.pendingMaps);
    this.pendingMaps.clear();
  }

  private String dequeueOutgoingMaps() {
    int count = Math.min(this.outgoingMaps.size(), MAX_MAPS_PER_REQUEST);
    String result =
        this
            .wireCodec.encodeMessageQueue(
                this.outgoingMaps,
                count,
                new Wire.BadMessageHandler() {
                  public void onBadMessage(Object message) {
                    if (WebChannelBase.this.handler != null) {
                      WebChannelBase.this.handler.badMapError(WebChannelBase.this, message);
                    }
                  }
                });

    this.pendingMaps.addAll(this.outgoingMaps.subList(0, count));
    this.outgoingMaps.subList(0, count).clear();

    return result;
  }

  public void sendMap(Map<String, String> map, Object context) {
    channelDebug.assertCondition(
        this.state != State.CLOSED, "Invalid operation: sending map when state is closed");

    if (this.outgoingMaps.size() == MAX_MAPS_PER_REQUEST) {
      this
          .channelDebug.severe(
              "Already have "
                  + MAX_MAPS_PER_REQUEST
                  + " queued maps upon queueing "
                  + map.toString()); // FIXME: string
    }

    this.outgoingMaps.add(new Wire.QueuedMap(this.nextMapId++, map, context));
    if (this.state == State.OPENING || this.state == State.OPENED) {
      this.ensureForwardChannel();
    }
  }

  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
    channelDebug.info("setFailFast: " + failFast);
    if ((this.forwardChannelRequestPool.hasPendingRequest() || this.forwardChannelTimer != null)
        && this.forwardChannelRetryCount > this.getForwardChannelMaxRetries()) {
      channelDebug.info(
          "Retry count "
              + this.forwardChannelRetryCount
              + " > new maxRetries "
              + this.getForwardChannelMaxRetries()
              + ". Fail immediately!");

      if (!this
          .forwardChannelRequestPool.forceComplete(
              new ForwardChannelRequestPool.CompletionCallback() {
                public void onComplete(ChannelRequest request) {
                  WebChannelBase.this.onRequestComplete(request);
                }
              })) {
        support.clearTimeout(this.forwardChannelTimer);
        this.forwardChannelTimer = null;
        this.signalError(WebChannelBase.ErrorEnum.REQUEST_FAILED);
      }
    }
  }

  public void onRequestComplete(ChannelRequest request) {
    channelDebug.debug("Request complete");
    ChannelType type;
    if (this.backChannelRequest == request) {
      this.clearDeadBackchannelTimer();
      this.backChannelRequest = null;
      type = ChannelType.BACK_CHANNEL;
    } else if (this.forwardChannelRequestPool.hasRequest(request)) {
      this.forwardChannelRequestPool.removeRequest(request);
      type = ChannelType.FORWARD_CHANNEL;
    } else {
      return;
    }

    this.lastStatusCode = request.getLastStatusCode();

    if (this.state == State.CLOSED) {
      return;
    }

    if (request.getSuccess()) {
      if (type == ChannelType.FORWARD_CHANNEL) {
        int size = request.getPostData() != null ? request.getPostData().length() : 0;
        support.notifyTimingEvent(
            size,
            System.currentTimeMillis() - request.getRequestStartTime(),
            this.forwardChannelRetryCount);
        this.ensureForwardChannel();
        this.onSuccess();
        this.pendingMaps.clear();
      } else {
        this.ensureBackChannel();
      }
      return;
    }
    // Else unsuccessful. Fall through.

    ChannelRequest.ErrorEnum lastError = request.getLastError();
    if (!WebChannelBase.isFatalError(lastError, this.lastStatusCode)) {
      // Maybe retry.
      channelDebug.debug(
          "Maybe retrying, last error: "
              + ChannelRequest.errorStringFromCode(lastError, this.lastStatusCode));
      if (type == ChannelType.FORWARD_CHANNEL) {
        if (this.maybeRetryForwardChannel(request)) {
          return;
        }
      }
      if (type == ChannelType.BACK_CHANNEL) {
        if (this.maybeRetryBackChannel()) {
          return;
        }
      }
      channelDebug.debug("Exceeded max number of retries");
    } else {
      channelDebug.debug("Not retrying due to error type");
    }

    channelDebug.debug("Error: HTTP request failed");
    switch (lastError) {
      case NO_DATA:
        this.signalError(WebChannelBase.ErrorEnum.NO_DATA);
        break;
      case BAD_DATA:
        this.signalError(WebChannelBase.ErrorEnum.BAD_DATA);
        break;
      case UNKNOWN_SESSION_ID:
        this.signalError(WebChannelBase.ErrorEnum.UNKNOWN_SESSION_ID);
        break;
      default:
        this.signalError(WebChannelBase.ErrorEnum.REQUEST_FAILED);
        break;
    }
  }

  private void ensureBackChannel() {
    if (this.backChannelRequest != null) {
      return;
    }

    if (this.backChannelTimer != null) {
      return;
    }

    this.backChannelAttemptId = 1;
    this.backChannelTimer =
        support.setTimeout(
            new TimeoutHandler() {
              public void onTimeout() {
                WebChannelBase.this.onStartBackChannelTimer();
              }
            },
            0);
    this.backChannelRetryCount = 0;
  }

  private void onStartBackChannelTimer() {
    this.backChannelTimer = null;
    this.startBackChannel();
  }

  private void startBackChannel() {
    if (!this.okToMakeRequest()) {
      // channel is cancelled
      return;
    }

    channelDebug.debug("Creating new HttpRequest");
    this.backChannelRequest =
        ChannelRequest.createChannelRequest(
            support, this, this.sid, "rpc", this.backChannelAttemptId);
    this.backChannelRequest.setExtraHeaders(this.extraHeaders);

    UriBuilder uri = this.backChannelUri.clone();
    uri.addQueryParameter("RID", "rpc");
    uri.addQueryParameter("SID", this.sid);
    uri.addQueryParameter("CI", this.useChunked ? "0" : "1");
    uri.addQueryParameter("AID", Long.toString(this.lastArrayId));

    this.addAdditionalParams(uri);

    uri.addQueryParameter("TYPE", "xmlhttp");
    this.backChannelRequest.httpGet(uri.getUri(), true, false);

    channelDebug.debug("New Request created");
  }

  private boolean maybeRetryForwardChannel(final ChannelRequest request) {
    if (this.forwardChannelRequestPool.isFull() || this.forwardChannelTimer != null) {
      this.channelDebug.severe("Request already in progress");
      return false;
    }

    if (this.state == State.INIT
        || // no retry open_()
        (this.forwardChannelRetryCount >= this.getForwardChannelMaxRetries())) {
      return false;
    }

    channelDebug.debug("Going to retry POST");

    this.forwardChannelTimer =
        support.setTimeout(
            new TimeoutHandler() {
              public void onTimeout() {
                WebChannelBase.this.onStartForwardChannelTimer(request);
              }
            },
            this.getRetryTime(this.forwardChannelRetryCount));

    this.forwardChannelRetryCount++;
    return true;
  }

  private long getRetryTime(int retryCount) {
    long retryTime =
        this.baseRetryDelayMs + (long) Math.floor(Math.random() * this.retryDelaySeedMs);
    if (!this.isActive()) {
      channelDebug.debug("Inactive channel");
      retryTime = retryTime * INACTIVE_CHANNEL_RETRY_FACTOR;
    }
    // Backoff for subsequent retries
    retryTime *= retryCount;
    return retryTime;
  }

  public void setRetryDelay(long baseDelayMs, long delaySeedMs) {
    this.baseRetryDelayMs = baseDelayMs;
    this.retryDelaySeedMs = delaySeedMs;
  }

  private boolean maybeRetryBackChannel() {
    if (this.backChannelRequest != null || this.backChannelTimer != null) {
      channelDebug.severe("Request already in progress");
      return false;
    }

    if (this.backChannelRetryCount >= this.getBackChannelMaxRetries()) {
      return false;
    }

    channelDebug.debug("Going to retry GET");

    this.backChannelAttemptId++;
    this.backChannelTimer =
        support.setTimeout(
            new TimeoutHandler() {
              public void onTimeout() {
                WebChannelBase.this.onStartBackChannelTimer();
              }
            },
            this.getRetryTime(this.backChannelRetryCount));

    this.backChannelRetryCount++;
    return true;
  }

  private static boolean isFatalError(ChannelRequest.ErrorEnum error, int statusCode) {
    return error == ChannelRequest.ErrorEnum.UNKNOWN_SESSION_ID
        || (error == ChannelRequest.ErrorEnum.STATUS && statusCode > 0);
  }

  public void testConnectionFinished(BaseTestChannel testChannel, boolean useChunked) {
    channelDebug.debug("Test Connection Finished");

    String clientProtocol = testChannel.getClientProtocol();
    if (clientProtocol != null) {
      this.forwardChannelRequestPool.applyClientProtocol(clientProtocol);
    }

    this.useChunked = this.allowChunkedMode && useChunked;
    this.lastStatusCode = testChannel.getLastStatusCode();

    this.connectChannel();
  }

  public void testConnectionFailure(
      BaseTestChannel testChannel, ChannelRequest.ErrorEnum errorCode) {
    channelDebug.debug("Test Connection Failed");
    this.lastStatusCode = testChannel.getLastStatusCode();
    this.signalError(ErrorEnum.REQUEST_FAILED);
  }

  public void onRequestData(ChannelRequest request, String responseText) {
    if (this.state == State.CLOSED
        || (this.backChannelRequest != request
            && !this.forwardChannelRequestPool.hasRequest(request))) {
      return;
    }
    this.lastStatusCode = request.getLastStatusCode();

    if (this.forwardChannelRequestPool.hasRequest(request)
        && this.state == State.OPENED) {
      List<?> response;
      try {
        response = this.wireCodec.decodeMessage(responseText, 1);
      } catch (Exception ex) {
        channelDebug.dumpException(ex, "Failed to decode " + responseText);
        response = null;
      }
      if (response != null && response.size() == 3) {
        this.handlePostResponse(response, request);
      } else {
        channelDebug.debug("Bad POST response data returned");
        this.signalError(ErrorEnum.BAD_RESPONSE);
      }
    } else {
      if (this.backChannelRequest == request) {
        this.clearDeadBackchannelTimer();
      }

      if (responseText != null) {
        responseText = responseText.trim();
        if (!responseText.isEmpty()) {
          try {
            List<?> decodedResponse = this.wireCodec.decodeMessage(responseText, 3);
            this.onInput(decodedResponse, responseText, request);
          } catch (Exception ex) {
            channelDebug.dumpException(ex, "Failed to decode " + responseText);
            this.signalError(ErrorEnum.BAD_RESPONSE);
          }
        }
      }
    }
  }

  private void applyControlHeaders(ChannelRequest request) {
    if (!this.backgroundChannelTest) {
      return;
    }

    HttpRequest req = request.getHttpRequest();
    if (req != null) {
      String clientProtocol = req.getResponseHeader(WebChannelConstants.X_CLIENT_WIRE_PROTOCOL);
      if (clientProtocol != null) {
        this.forwardChannelRequestPool.applyClientProtocol(clientProtocol);
      }

      if (this.getHttpSessionIdParam() != null) {
        String httpSessionIdHeader =
            req.getResponseHeader(WebChannelConstants.X_HTTP_SESSION_ID);
        if (httpSessionIdHeader != null) {
          this.setHttpSessionId(httpSessionIdHeader);
          String httpSessionIdParam = this.getHttpSessionIdParam();
          this.forwardChannelUri.addQueryParameter(httpSessionIdParam, httpSessionIdHeader);
        } else {
          this.channelDebug.warning(
              "Missing X_HTTP_SESSION_ID in the handshake response");
        }
      }
    }
  };

  @SuppressWarnings("unchecked")
  private void onInput(List<?> responseJsonArray, String responseTextForDebugging,
      ChannelRequest request) throws ClassCastException {
    // channelHandleMultipleArrays ignored

    List<Object> batch = null;

    for (int i = 0; i < responseJsonArray.size(); i++) {
      List<Object> nextArray = (List<Object>) responseJsonArray.get(i);
      this.lastArrayId = ((Number) nextArray.get(0)).longValue();
      Object nextArrayObject = nextArray.get(1);
      if (this.state == WebChannelBase.State.OPENING) {
        nextArray = (List<Object>) nextArrayObject;
        if (nextArray.get(0).equals("c")) {
          this.sid = (String) nextArray.get(1);
          // this.hostPrefix_ = this.correctHostPrefix(nextArray[2]);

          if (nextArray.size() >= 4) {
            Number negotiatedVersion = (Number) nextArray.get(3);
            if (negotiatedVersion != null) {
              this.channelVersion = negotiatedVersion.intValue();
              channelDebug.debug("VER=" + this.channelVersion);
            }
          }
          if (nextArray.size() >= 5) {
            Number negotiatedServerVersion = (Number) nextArray.get(4);
            if (negotiatedServerVersion != null) {
              this.serverVersion = negotiatedServerVersion.intValue();
              channelDebug.debug("SVER=" + this.serverVersion);
            }
          }

          this.applyControlHeaders(request);

          this.state = State.OPENED;
          if (this.handler != null) {
            this.handler.channelOpened(this);
          }
          this.backChannelUri = this.getBackChannelUri(this.path);
          // Open connection to receive data
          this.ensureBackChannel();
        } else if (nextArray.get(0).equals("stop") || nextArray.get(0).equals("close")) {
          this.signalError(ErrorEnum.STOP);
        }
      } else if (this.state == State.OPENED) {
        if (nextArrayObject instanceof List) {
          nextArray = (List<Object>) nextArrayObject;
        }
        if (nextArrayObject instanceof List
            && (nextArray.get(0).equals("stop") || nextArray.get(0).equals("close"))) {
          if (batch != null && !batch.isEmpty()) {
            this.handler.channelHandleMultipleArrays(this, batch);
            batch.clear();
          }
          if (nextArray.get(0).equals("stop")) {
            this.signalError(ErrorEnum.STOP);
          } else {
            this.disconnect();
          }
        } else if (nextArrayObject instanceof List && nextArray.get(0).equals("noop")) {
          // ignore - noop to keep connection happy
        } else {
          if (batch != null) {
            batch.add(nextArray);
          } else if (this.handler != null) {
            this.handler.channelHandleArray(this, nextArrayObject, responseTextForDebugging);
          }
        }
        this.backChannelRetryCount = 0;
      }
    }
    if (batch != null && !batch.isEmpty()) {
      this.handler.channelHandleMultipleArrays(this, batch);
    }
  }

  private void handlePostResponse(List<?> responseValues, ChannelRequest forwardReq) {
    // The first response value is set to 0 if server is missing backchannel.
    if (responseValues.get(0).equals(0)) {
      this.handleBackchannelMissing(forwardReq);
      return;
    }
    this.lastPostResponseArrayId = ((Number) responseValues.get(1)).longValue();
    long outstandingArrays = this.lastPostResponseArrayId - this.lastArrayId;
    if (0 < outstandingArrays) {
      long numOutstandingBackchannelBytes =
          ((Number) responseValues.get(2)).longValue();
      channelDebug.debug(
          numOutstandingBackchannelBytes
              + " bytes (in "
              + outstandingArrays
              + " arrays) are outstanding on the BackChannel");
      if (!this.shouldRetryBackChannel(numOutstandingBackchannelBytes)) {
        return;
      }
      if (this.deadBackChannelTimer == null) {
        this.deadBackChannelTimer =
            support.setTimeout(
                new TimeoutHandler() {
                  public void onTimeout() {
                    WebChannelBase.this.onBackChannelDead();
                  }
                },
                2 * WebChannelBase.RTT_ESTIMATE);
      }
    }
  }

  private void handleBackchannelMissing(ChannelRequest forwardReq) {
    channelDebug.debug("Server claims our backchannel is missing.");
    if (this.backChannelTimer != null) {
      channelDebug.debug("But we are currently starting the request.");
      return;
    } else if (this.backChannelRequest == null) {
      channelDebug.warning("We do not have a BackChannel established");
    } else if (this.backChannelRequest.getRequestStartTime() + WebChannelBase.RTT_ESTIMATE
        < forwardReq.getRequestStartTime()) {
      this.clearDeadBackchannelTimer();
      this.backChannelRequest.cancel();
      this.backChannelRequest = null;
    } else {
      return;
    }
    this.maybeRetryBackChannel();
    support.notifyStatEvent(RequestStat.BACKCHANNEL_MISSING);
  }

  private boolean shouldRetryBackChannel(long outstandingBytes) {
    return outstandingBytes < OUTSTANDING_DATA_BACKCHANNEL_RETRY_CUTOFF
        && !this.isBuffered()
        && this.backChannelRetryCount == 0;
  }

  private void onBackChannelDead() {
    if (this.deadBackChannelTimer != null) {
      this.deadBackChannelTimer = null;
      this.backChannelRequest.cancel();
      this.backChannelRequest = null;
      this.maybeRetryBackChannel();
      support.notifyStatEvent(RequestStat.BACKCHANNEL_DEAD);
    }
  }

  public UriBuilder getForwardChannelUri(String path) {
    UriBuilder uri = this.createDataUri(path, null);
    channelDebug.debug("GetForwardChannelUri: " + uri);
    return uri;
  }

  public ConnectionState getConnectionState() {
    return this.connState;
  }

  public UriBuilder createDataUri(String path, Integer overidePort) {
    UriBuilder uri = support.newUriBuilder(path);
    channelDebug.assertCondition(uri.getAuthority() != null, "No relative path.");

    if (this.extraParams != null) {
      for (String key : extraParams.keySet()) {
        uri.addQueryParameter(key, extraParams.get(key));
      }
    }

    if (this.getHttpSessionIdParam() != null && this.getHttpSessionId() != null) {
      uri.addQueryParameter(this.getHttpSessionIdParam(), this.getHttpSessionId());
    }

    uri.addQueryParameter("VER", Integer.toString(this.channelVersion));

    this.addAdditionalParams(uri);

    return uri;
  }

  public UriBuilder getBackChannelUri(String path) {
    UriBuilder uri = this.createDataUri(path, null);
    channelDebug.debug("GetBackChannelUri: " + uri);
    return uri;
  }

  public HttpRequest createHttpRequest() {
    return support.newHttpRequest();
  }

  public boolean isActive() {
    return this.handler != null && this.handler.isActive(this);
  }

  public static abstract class Handler {

    public void channelHandleMultipleArrays(WebChannelBase channel, List<Object> data) {
      // ignored
    }

    public ErrorEnum okToMakeRequest(WebChannelBase channel) {
      return WebChannelBase.ErrorEnum.OK;
    }

    public void channelOpened(WebChannelBase channel) {}

    public void channelHandleArray(
        WebChannelBase channel, Object data, String responseTextForDebugging) {}

    public void channelSuccess(WebChannelBase channel, List<Wire.QueuedMap> data) {}

    public void channelError(WebChannelBase channel, ErrorEnum error) {}

    public void channelClosed(
        WebChannelBase channel,
        List<Wire.QueuedMap> pendingData,
        List<Wire.QueuedMap> undeliveredData) {}

    public Map<String, String> getAdditionalParams(WebChannelBase channel) {
      return null;
    }

    public Uri getNetworkTestImageUri(WebChannelBase channel) {
      return null;
    }

    public boolean isActive(WebChannelBase channel) {
      return true;
    }

    public void badMapError(WebChannelBase channel, Object data) {}
  }
}
