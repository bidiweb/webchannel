package bidiweb.webchannel.client.support.basic;

import com.google.common.net.Uri;

/**
 * Implementation of WebChannel Uri interface.
 *
 * <p>Light wrapper around Guava Uri to match the WebChannel Support API.
 */
class BasicWebChannelSupportUri extends bidiweb.webchannel.client.support.Support.Uri {
  private final Uri uri;

  public BasicWebChannelSupportUri(Uri uri) {
    this.uri = uri;
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
