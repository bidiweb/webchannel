package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.WebChannelTransport;
import bidiweb.webchannel.client.support.Support;

public class WebChannelTransports {

  public static WebChannelTransport createTransport(Support support) {
    return new WebChannelBaseTransport(support);
  }
}
