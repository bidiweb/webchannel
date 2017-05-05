package bidiweb.webchannel.client;

import java.io.IOException;
import java.nio.channels.InterruptibleChannel;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A WebChannel behaves like a virtual channel that reads or writes messages
 * over the Internet.
 * <p/>
 * This is the client side interface, and a channel instance is created
 * by the <code>Client</code>.
 * <p/>
 * This interface supports blocking read and write, and is interruptible.
 * <p/>
 * See closure goog.net.WebChannel for the equivalent JS APIs (async) and
 * their related semantics.
 */
@ThreadSafe
public interface WebChannel extends InterruptibleChannel {

  @Override
  void close() throws IOException;

  @Override
  boolean isOpen();

  /**
   * @return the error status (which causes the channel to be closed).
   */
  ErrorStatus getErrorStatus();

  /**
   * Open a newly created channel. This is a blocking operation.
   * <p/>
   * Unexpected calls will throw IllegalStateException.
   *
   * @throws java.nio.channels.ClosedChannelException
   * @throws IOException                              for any other errors
   */
  void open() throws IOException;

  /**
   * It's a blocking read and concurrent reads will be blocked too.
   *
   * @return an atomic message received from the server. Supported types
   * include Map<String, String>.
   * @throws java.nio.channels.NonReadableChannelException
   * @throws java.nio.channels.ClosedChannelException
   * @throws java.nio.channels.AsynchronousCloseException
   * @throws java.nio.channels.ClosedByInterruptException
   * @throws IOException                                   for any other errors
   */
  Object read() throws IOException;

  /**
   * It may block (subject to flow-control) and concurrent writes
   * will be blocked too.
   *
   * @param message Supported types include Map<String, String>.
   * @throws java.nio.channels.NonWritableChannelException
   * @throws java.nio.channels.ClosedChannelException
   * @throws java.nio.channels.AsynchronousCloseException
   * @throws java.nio.channels.ClosedByInterruptException
   * @throws IOException                                   for any other errors
   */
  void write(Object message) throws IOException;

  // runtime properties

  /**
   * @return The effective limit for the number of concurrent HTTP
   * requests that are allowed to be made for sending messages from the client
   * to the server. When SPDY (or any other multiplexing transport)
   * is not enabled, this limit will be one.
   */
  int getConcurrentRequestLimit();

  // #isSpdyEnabled from the JS API, which is only applicable for browsers

  /**
   * @return The last HTTP status code received by the channel.
   */
  int getLastStatusCode();
}
