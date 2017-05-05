package bidiweb.webchannel.client;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

/**
 * For configuring the webchannel runtime behavior.
 * <p/>
 * See the WebChannel JS API spec.
 *
 * @see WebChannelTransport#createWebChannel(String, WebChannelOptions)
 */
@ThreadSafe
public final class WebChannelOptions {
  private Map<String, String> messageHeaders;
  private Map<String, String> initMessageHeaders;
  private Map<String, String> messageUrlParams;
  private boolean clientProtocolHeaderRequired = false;
  private int concurrentRequestLimit = 0;   // default per implementation
  private String testUrl;
  private boolean sendRawJson = false;
  private String httpSessionIdParam;
  private boolean backgroundChannelTest = false;

  private WebChannelOptions() {
  }

  public Map<String, String> getMessageHeaders() {
    return this.messageHeaders;
  }

  public Map<String, String> getInitMessageHeaders() {
    return this.initMessageHeaders;
  }

  public Map<String, String> getMessageUrlParams() {
    return this.messageUrlParams;
  }

  public boolean getClientProtocolHeaderRequired() {
    return this.clientProtocolHeaderRequired;
  }

  public int getConcurrentRequestLimit() {
    return this.concurrentRequestLimit;
  }

  public String getTestUrl() {
    return this.testUrl;
  }

  public boolean getSendRawJson() {
    return this.sendRawJson;
  }

  public String getHttpSessionIdParam() {
    return this.httpSessionIdParam;
  }

  public boolean getBackgroundChannelTest() {
    return this.backgroundChannelTest;
  }

  /**
   * The builder class.
   */
  public static class Builder {
    private WebChannelOptions options = new WebChannelOptions();

    public Builder() {
    }

    //TODO: match JS in mutability
    public Builder messageHeaders(Map<String, String> val) {
      if (val != null) {
        options.messageHeaders = new HashMap<>(val);
      }
      return this;
    }

    public Builder initMessageHeaders(Map<String, String> val) {
      if (val != null) {
        options.initMessageHeaders = new HashMap<>(val);
      }
      return this;
    }

    public Builder messageUrlParams(Map<String, String> val) {
      if (val != null) {
        options.messageUrlParams = new HashMap<>(val);
      }
      return this;
    }

    public Builder clientProtocolHeaderRequired(boolean val) {
      options.clientProtocolHeaderRequired = val;
      return this;
    }

    public Builder concurrentRequestLimit(int val) {
      options.concurrentRequestLimit = val;
      return this;
    }

    public Builder testUrl(String val) {
      options.testUrl = val;
      return this;
    }

    public Builder sendRawJson(boolean val) {
      options.sendRawJson = val;
      return this;
    }

    public Builder httpSessionIdParam(String val) {
      options.httpSessionIdParam = val;
      return this;
    }

    public Builder backgroundChannelTest(boolean val) {
      options.backgroundChannelTest = val;
      return this;
    }

    public WebChannelOptions build() {
      return options;
    }
  }
}

