package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.AsyncWebChannel;
import bidiweb.webchannel.client.ErrorStatus;
import bidiweb.webchannel.client.WebChannel;
import bidiweb.webchannel.client.WebChannelConstants;
import bidiweb.webchannel.client.WebChannelOptions;
import bidiweb.webchannel.client.WebChannelRuntimeProperties;
import bidiweb.webchannel.client.WebChannelTransport;
import bidiweb.webchannel.client.support.Support;
import bidiweb.webchannel.client.support.Support.Debugger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// no disposeInternal (eventHandler, so not needed)
// sendrawjson automatically
class WebChannelBaseTransport extends WebChannelTransport {
  private Support support;

  public WebChannelBaseTransport(Support support) {
    this.support = support;
  }

  public WebChannel createWebChannel(String urlPath, WebChannelOptions options) {
    throw new UnsupportedOperationException(
        "This implementation only " + "supports the async webchannel client API.");
  }

  public AsyncWebChannel createAsyncWebChannel(String urlPath, WebChannelOptions options) {
    return new InternalChannel(support, urlPath, options);
  }
}

class InternalChannel implements AsyncWebChannel {
  private Support support;
  private WebChannelBase channel;
  private String url;
  private String testUrl;
  private Debugger channelDebug;
  private Map<String, String> messageUrlParams;
  private boolean sendRawJson;
  private Handler channelHandler;
  private EventHandler eventHandler;

  public InternalChannel(Support support, String url, WebChannelOptions options) {
    this.support = support;

    this.channel = new WebChannelBase(support, options, WebChannelTransport.CLIENT_VERSION, null);
    this.url = url;

    if (options != null && options.getTestUrl() != null) {
      this.testUrl = options.getTestUrl();
    } else {
      this.testUrl = this.url + "/test";
    }

    this.channelDebug = channel.getChannelDebug();

    if (options != null && options.getMessageUrlParams() != null) {
      this.messageUrlParams = options.getMessageUrlParams();
    } else {
      this.messageUrlParams = null;
    }

    // FIXME (js)
    Map<String, String> messageHeaders = null;
    if (options != null && options.getMessageHeaders() != null) {
      messageHeaders = options.getMessageHeaders();
    }
    if (options != null && options.getClientProtocolHeaderRequired()) {
      if (messageHeaders == null) {
        messageHeaders = new HashMap<>();
      }
      messageHeaders.put(
          WebChannelConstants.X_CLIENT_PROTOCOL, WebChannelConstants.X_CLIENT_PROTOCOL_WEB_CHANNEL);
    }
    this.channel.setExtraHeaders(messageHeaders);

    if (options != null && options.getInitMessageHeaders() != null) {
      this.channel.setInitHeaders(options.getInitMessageHeaders());
    }

    this.sendRawJson = options != null && options.getSendRawJson();

    if (options != null && options.getHttpSessionIdParam() != null) {
      String httpSessionIdParam = options.getHttpSessionIdParam().trim();
      if (!httpSessionIdParam.isEmpty()) {
        this.channel.setHttpSessionIdParam(httpSessionIdParam);
        if (this.messageUrlParams != null
            && this.messageUrlParams.containsKey(httpSessionIdParam)) {
          this.messageUrlParams.remove(httpSessionIdParam);
          this.channelDebug.warning(
              "Ignore httpSessionIdParam also specified with messageUrlParams: "
                  + httpSessionIdParam);
        }
      }
    }

    this.channelHandler = new Handler();
  }

  public void setChannelHandler(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  public void open() {
    this.channel.setHandler(this.channelHandler);
    this.channel.connect(this.testUrl, this.url, this.messageUrlParams, null, null);
  }

  public void close() {
    this.channel.disconnect();
  }

  public <T> void send(T message) throws IllegalArgumentException {
    if (!(message instanceof String)) {
      throw new IllegalArgumentException(
          "Serialized JSON string only. " + message.getClass());
    }

    String json = (String) message;
    Map<String, String> rawJson = new HashMap<>();
    rawJson.put("__data__", json);
    this.channel.sendMap(rawJson, null);
  }

  // FIXME: make this a view to the channel state
  public WebChannelRuntimeProperties getRuntimeProperties() {
    WebChannelRuntimeProperties result = new WebChannelRuntimeProperties();
    result.setConcurrentRequestLimit(channel.getForwardChannelRequestPool().getMaxSize());
    result.setSpdyEnabled(true);
    result.setLastStatusCode(channel.getLastStatusCode());
    return result;
  }

  private class Handler extends WebChannelBase.Handler {

    public void channelOpened(WebChannelBase channel) {
      channelDebug.info("WebChannel opened on " + url);
      try {
        eventHandler.onOpen();
      } catch (Exception ex) {
        channelDebug.dumpException(ex, "event handler onOpen() exception");
      }
    }

    public void channelClosed(
        WebChannelBase channel,
        List<Wire.QueuedMap> pendingData,
        List<Wire.QueuedMap> undeliveredData) {
      channelDebug.info("WebChannel closed on " + url);
      try {
        eventHandler.onClose();
      } catch (Exception ex) {
        channelDebug.dumpException(ex, "event handler onClose() exception");
      }
    }

    public void channelError(WebChannelBase channel, WebChannelBase.ErrorEnum error) {
      channelDebug.info("WebChannel aborted on " + url + " due to channel error: " + error);
      try {
        eventHandler.onError(new ErrorStatus(ErrorStatus.StatusEnum.NETWORK_ERROR, error));
      } catch (Exception ex) {
        channelDebug.dumpException(ex, "event handler onError() exception");
      }
    }

    public void channelHandleArray(
        WebChannelBase channel, Object data, String responseTextForDebugging) {
      try {
        eventHandler.onMessage(data);
      } catch (Exception ex) {
        channelDebug.dumpException(
            ex, "event handler onMessage() exception! Payload: " + responseTextForDebugging);
      }
    }
  }
}
