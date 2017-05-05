package bidiweb.webchannel.client;

/**
 * The channel abstraction. See closure API spec.
 */
public interface AsyncWebChannel {

  /**
   * Events are fired in the order as they are generated, e.g. from the
   * communication with the network or server.
   */
  abstract class EventHandler {
    public void onOpen() {}
    public void onClose() {}
    public void onError(ErrorStatus error) {}

    /**
     * Messages are to be delivered in order. The callback should be
     * a non-blocking operation. New messages will not be delivered
     * before the current callback returns.
     *
     * @param message The message decoded from the wire.
     * @param <T>
     */
    public <T> void onMessage(T message) {}
  }

  /**
   * To be set to the channel before open() is called.
   *
   * @param eventHandler
   */
  void setChannelHandler(EventHandler eventHandler);

  /**
   * Opens the channel against the URL specified when the channel is created.
   * @see WebChannelTransport
   */
  void open();

  /**
   * Closes the channel.
   */
  void close();

  /**
   * Sends a message to the server. This is a non-blocking operation.
   *
   * @param message The message to send
   * @throws IllegalArgumentException if the implementation does not support
   * the message type
   */
  <T> void send(T message) throws IllegalArgumentException;

  /**
   * @return a reference to the (mutable) runtime properties of the channel
   * object.
   */
  WebChannelRuntimeProperties getRuntimeProperties();
}
