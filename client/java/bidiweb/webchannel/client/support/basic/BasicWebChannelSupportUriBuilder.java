package bidiweb.webchannel.client.support.basic;

import com.google.common.net.UriBuilder;
import bidiweb.webchannel.client.support.Support;

/**
 * Implementation of WebChannel UriBuilder interface.
 *
 * <p>Light wrapper around Guava UriBuilder to match the WebChannel Support API.
 */
class BasicWebChannelSupportUriBuilder extends Support.UriBuilder {
  private final UriBuilder builder;

  public static BasicWebChannelSupportUriBuilder parse(String url) {
    return new BasicWebChannelSupportUriBuilder(UriBuilder.parse(url));
  }

  private BasicWebChannelSupportUriBuilder(UriBuilder builder) {
    this.builder = builder;
  }

  @Override
  public BasicWebChannelSupportUriBuilder addQueryParameter(String name, String value) {
    builder.addQueryParameter(name, value);
    return this;
  }

  @Override
  public String getAuthority() {
    return builder.getAuthority();
  }

  @Override
  public BasicWebChannelSupportUri getUri() {
    return new BasicWebChannelSupportUri(builder.getUri());
  }

  @Override
  public BasicWebChannelSupportUriBuilder clone() {
    return new BasicWebChannelSupportUriBuilder(builder.clone());
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
