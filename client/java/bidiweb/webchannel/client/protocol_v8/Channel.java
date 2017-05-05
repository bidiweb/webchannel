package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.support.Support.HttpRequest;
import bidiweb.webchannel.client.support.Support.UriBuilder;

interface Channel {

  HttpRequest createHttpRequest();

  void onRequestComplete(ChannelRequest request);

  boolean isClosed();

  void onRequestData(ChannelRequest request, String responseText);

  boolean isActive();

  UriBuilder getForwardChannelUri(String path);

  UriBuilder getBackChannelUri(String path);

  UriBuilder createDataUri(String path, Integer overidePort);

  void testConnectionFinished(BaseTestChannel testChannel, boolean useChunked);

  void testConnectionFailure(BaseTestChannel testChannel, ChannelRequest.ErrorEnum errorCode);

  ConnectionState getConnectionState();

  void setHttpSessionIdParam(String httpSessionIdParam);

  String getHttpSessionIdParam();

  void setHttpSessionId(String httpSessionId);

  String getHttpSessionId();

  boolean getBackgroundChannelTest();

  Object getWireCodec();       // extra for Java
}
