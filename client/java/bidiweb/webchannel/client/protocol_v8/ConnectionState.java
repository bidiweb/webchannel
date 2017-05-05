package bidiweb.webchannel.client.protocol_v8;

import java.util.List;

class ConnectionState {

  private List<String> handshakeResult = null;

  private Boolean bufferingProxyResult = null;

  public List<String> getHandshakeResult() {
    return handshakeResult;
  }

  public void setHandshakeResult(List<String> handshakeResult) {
    this.handshakeResult = handshakeResult;
  }

  public Boolean getBufferingProxyResult() {
    return bufferingProxyResult;
  }

  public void setBufferingProxyResult(Boolean bufferingProxyResult) {
    this.bufferingProxyResult = bufferingProxyResult;
  }

  public ConnectionState() {}
}
