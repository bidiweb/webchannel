package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.WebChannelConstants;
import bidiweb.webchannel.client.support.Support;
import bidiweb.webchannel.client.support.Support.Debugger;
import bidiweb.webchannel.client.support.Support.HttpRequest;
import bidiweb.webchannel.client.support.Support.RequestStat;
import bidiweb.webchannel.client.support.Support.UriBuilder;

import java.util.List;
import java.util.Map;

class BaseTestChannel implements Channel {
  private Support support;

  private Channel channel;
  private Debugger channelDebug;

  private Map<String, String> extraHeaders = null;
  private ChannelRequest request = null;
  private boolean receivedIntermediateResult = false;

  private String path = null;
  private int lastStatusCode = -1;

  // sub-domains removed

  private String clientProtocol = null;

  private State state = null;

  private enum State {
    INIT,
    CONNECTION_TESTING
  };

  public BaseTestChannel(Support support, Channel channel) {
    this.support = support;
    this.channel = channel;
    this.channelDebug = support.getDebugger();
  }

  /**
   * Take the ownership.
   */
  public void setExtraHeaders(Map<String, String> extraHeaders) {
    this.extraHeaders = extraHeaders;
  }

  public void connect(String path) {
    this.path = path;
    UriBuilder sendDataUri = channel.getForwardChannelUri(this.path);

    support.notifyStatEvent(RequestStat.TEST_STAGE_ONE_START);

    List<String> handshakeResult = this.channel.getConnectionState().getHandshakeResult();

    if (handshakeResult != null) {
      this.state = State.CONNECTION_TESTING;
      checkBufferingProxy();
      return;
    }

    sendDataUri.addQueryParameter("MODE", "init");

    if (!channel.getBackgroundChannelTest() && channel.getHttpSessionIdParam() != null) {
      sendDataUri.addQueryParameter(WebChannelConstants.X_HTTP_SESSION_ID,
          channel.getHttpSessionIdParam());
    }

    this.request = ChannelRequest.createChannelRequest(support, this, null, null, 0);

    this.request.setExtraHeaders(this.extraHeaders);

    this.request.httpGet(sendDataUri.getUri(), false, true);
    this.state = State.INIT;
  }

  private void checkBufferingProxy() {
    channelDebug.debug("TestConnection: starting stage 2");

    Boolean bufferingProxyResult = channel.getConnectionState().getBufferingProxyResult();
    if (bufferingProxyResult != null) {
      channelDebug.debug(
          "TestConnection: skipping stage 2, precomputed result is "
              + (bufferingProxyResult ? "Buffered" : "Unbuffered"));
      support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_START);
      if (bufferingProxyResult) {
        support.notifyStatEvent(RequestStat.PROXY);
        this.channel.testConnectionFinished(this, false);
      } else {
        support.notifyStatEvent(RequestStat.NOPROXY);
        this.channel.testConnectionFinished(this, true);
      }
      return; // Skip the test
    }
    this.request = ChannelRequest.createChannelRequest(support, this, null, null, 0);
    this.request.setExtraHeaders(this.extraHeaders);
    UriBuilder recvDataUri = this.channel.getBackChannelUri(this.path);

    support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_START);
    recvDataUri.addQueryParameter("TYPE", "xmlhttp");

    if (channel.getHttpSessionIdParam() != null && channel.getHttpSessionId() != null) {
      recvDataUri.addQueryParameter(channel.getHttpSessionIdParam(), channel.getHttpSessionId());
    }

    this.request.httpGet(recvDataUri.getUri(), false, false);
  }

  public void abort() {
    if (this.request != null) {
      this.request.cancel();
      this.request = null;
    }

    this.lastStatusCode = -1;
  }

  public void onRequestData(ChannelRequest request, String responseText) {
    this.lastStatusCode = request.getLastStatusCode();
    if (this.state == State.INIT) {
      channelDebug.debug("TestConnection: Got data for stage 1");

      applyControlHeaders(request);

      if (responseText == null || responseText.isEmpty()) {
        channelDebug.debug("TestConnection: Null responseText");
        channel.testConnectionFailure(this, ChannelRequest.ErrorEnum.BAD_DATA);
        return;
      }

      try {
        List<?> respArray = ((WireV8) channel.getWireCodec()).decodeMessage(responseText, 1);
      } catch (Exception ex) {
        channelDebug.dumpException(ex, "codec error");
        channel.testConnectionFailure(this, ChannelRequest.ErrorEnum.BAD_DATA);
        return;
      }
    } else if (this.state == State.CONNECTION_TESTING) {
      if (this.receivedIntermediateResult) {
        support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_DATA_TWO);
      } else {
        if (responseText.equals("11111")) {
          support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_DATA_ONE);
          this.receivedIntermediateResult = true;
          if (this.checkForEarlyNonBuffered()) {
            this.lastStatusCode = 200;
            this.request.cancel();
            channelDebug.debug("Test connection succeeded; using streaming connection");
            support.notifyStatEvent(RequestStat.NOPROXY);
            this.channel.testConnectionFinished(this, true);
          }
        } else {
          support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_DATA_BOTH);
          this.receivedIntermediateResult = false;
        }
      }
    }
  }

  private boolean checkForEarlyNonBuffered() {
    return ChannelRequest.supportsHttpStreaming();
  }

  public HttpRequest createHttpRequest() {
    return this.channel.createHttpRequest();
  }

  public void onRequestComplete(ChannelRequest request) {
    this.lastStatusCode = this.request.getLastStatusCode();
    if (!this.request.getSuccess()) {
      channelDebug.debug("TestConnection: request failed, in state " + this.state);
      if (this.state == State.INIT) {
        support.notifyStatEvent(RequestStat.TEST_STAGE_ONE_FAILED);
      } else if (this.state == State.CONNECTION_TESTING) {
        support.notifyStatEvent(RequestStat.TEST_STAGE_TWO_FAILED);
      }
      this.channel.testConnectionFailure(this, this.request.getLastError());
      return;
    }

    if (this.state == State.INIT) {
      this.state = State.CONNECTION_TESTING;

      channelDebug.debug("TestConnection: request complete for initial check");

      this.checkBufferingProxy();
    } else if (this.state == State.CONNECTION_TESTING) {
      channelDebug.debug("TestConnection: request complete for stage 2");

      boolean goodConn = this.receivedIntermediateResult;
      if (goodConn) {
        channelDebug.debug("Test connection succeeded; using streaming connection");
        support.notifyStatEvent(RequestStat.NOPROXY);
        this.channel.testConnectionFinished(this, true);
      } else {
        channelDebug.debug("Test connection failed; not using streaming");
        support.notifyStatEvent(RequestStat.PROXY);
        this.channel.testConnectionFinished(this, false);
      }
    }
  }

  public String getClientProtocol() {
    return this.clientProtocol;
  }

  public int getLastStatusCode() {
    return this.lastStatusCode;
  }

  public boolean isClosed() {
    return false;
  }

  public boolean isActive() {
    return this.channel.isActive();
  }

  public UriBuilder getForwardChannelUri(String path) {
    return null;
  }

  public UriBuilder getBackChannelUri(String path) {
    return null;
  }

  public UriBuilder createDataUri(String path, Integer overidePort) {
    return null;
  }

  public void testConnectionFinished(BaseTestChannel testChannel, boolean useChunked) {}

  public void testConnectionFailure(
      BaseTestChannel testChannel, ChannelRequest.ErrorEnum errorCode) {}

  public ConnectionState getConnectionState() {
    return null;
  }

  public Object getWireCodec() {
    return null;
  }

  private void applyControlHeaders(ChannelRequest request) {
    if (this.channel.getBackgroundChannelTest()) {
      return;
    }

    HttpRequest httpRequest = request.getHttpRequest();
    if (httpRequest != null) {
      this.clientProtocol = httpRequest.getResponseHeader(
          WebChannelConstants.X_CLIENT_WIRE_PROTOCOL);

      if (this.channel.getHttpSessionIdParam() != null) {
        String httpSessionIdHeader = httpRequest.getResponseHeader(
            WebChannelConstants.X_HTTP_SESSION_ID);
        if (httpSessionIdHeader != null) {
          this.channel.setHttpSessionId(httpSessionIdHeader);
        } else {
          this.channelDebug.warning("Missing X_HTTP_SESSION_ID in the handshake response");
        }
      }
    }
  }

  @Override
  public void setHttpSessionIdParam(String httpSessionIdParam) {
  }

  @Override
  public String getHttpSessionIdParam() {
    return null;
  }

  @Override
  public void setHttpSessionId(String httpSessionId) {
  }

  @Override
  public String getHttpSessionId() {
    return null;
  }

  @Override
  public boolean getBackgroundChannelTest() {
    return false;
  }
}
