package bidiweb.webchannel.client.protocol_v8;

import bidiweb.webchannel.client.support.Support.Uri;

class NetUtils {
  // to be implemented, to detect network v.s. server error

  public static final long NETWORK_TIMEOUT = 10000;

  public interface TestNetworkCallback {
    void onTestNetworkResult(boolean result);
  }

  public static void testNetwork(TestNetworkCallback callback, Uri uri) {}

  public static void testNetworkWithRetries(
      Uri uri,
      long timeout,
      TestNetworkCallback callback,
      int retries,
      long pauseBetweenRetriesMS) {}

  public void testLoadImage(String url, long timeout, TestNetworkCallback callback) {}
}
