package bidiweb.webchannel.client.support.basic;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.io.Closeables;
import bidiweb.webchannel.client.support.Support.RequestErrorCode;
import bidiweb.webchannel.client.support.Support.RequestReadyState;
import bidiweb.webchannel.client.support.Support.UriBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of WebChannel HttpRequest interface using {@link NetHttpTransport}.
 *
 * <p>All the incoming calls have to be done in the single thread of {@code apiThreadExecutor}. The
 * class then executes all the (blocking) network handling code in the given {@code networkExecutor}
 * and any callbacks are done in {@code apiThreadExecutor}.
 *
 * <p>TODO: Should portability become an issue, consider OkHttpClient as an alternative to {@link
 * NetHttpTransport}.
 */
@NotThreadSafe
class BasicWebChannelSupportHttpRequest
    extends bidiweb.webchannel.client.support.Support.HttpRequest {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final int READ_CHUNK_SIZE_BYTES = 1024;

  private final ExecutorService apiThreadExecutor;
  private final ExecutorService networkExecutor;
  private final HttpRequestFactory httpRequestFactory;

  @Nullable private Future<?> responseFuture = null;

  /**
   * Lock to protect all objects shared between the network handling threads {@code networkExecutor}
   * and the single WebChannel thread in {@code apiThreadExecutor}.
   */
  private final Object lock = new Object();

  @GuardedBy("lock")
  private HttpHeaders responseHeaders = new HttpHeaders();

  @GuardedBy("lock")
  private int status = 0;

  @GuardedBy("lock")
  private RequestReadyState readyState = RequestReadyState.UNINITIALIZED;

  @GuardedBy("lock")
  private RequestErrorCode lastErrorCode = RequestErrorCode.NO_ERROR;

  @GuardedBy("lock")
  private boolean aborted = false;

  @GuardedBy("lock")
  private final StringBuilder responseTextBuilder = new StringBuilder();

  public BasicWebChannelSupportHttpRequest(
      ExecutorService apiThreadExecutor, ExecutorService networkExecutor) {
    this(apiThreadExecutor, networkExecutor, new NetHttpTransport());
  }

  public BasicWebChannelSupportHttpRequest(
      ExecutorService apiThreadExecutor, ExecutorService networkExecutor, HttpTransport transport) {
    this.apiThreadExecutor = apiThreadExecutor;
    this.networkExecutor = networkExecutor;
    httpRequestFactory = transport.createRequestFactory();
  }

  @Override
  public String getResponseHeader(String name) {
    synchronized (lock) {
      return responseHeaders.getFirstHeaderStringValue(name);
    }
  }

  @Override
  public void drainResponseText(StringBuilder buffer) {
    synchronized (lock) {
      if (responseTextBuilder.length() == 0) {
        return;
      }

      buffer.append(responseTextBuilder);
      responseTextBuilder.setLength(0);
    }
  }

  @Override
  public RequestReadyState getReadyState() {
    synchronized (lock) {
      return readyState;
    }
  }

  @Override
  public RequestErrorCode getLastErrorCode() {
    synchronized (lock) {
      return lastErrorCode;
    }
  }

  @Override
  public int getStatus() {
    synchronized (lock) {
      return status;
    }
  }

  @Override
  public void send(
      final UriBuilder uri,
      final String verb,
      @Nullable final String postData,
      @Nullable final Map<String, String> headers) {
    Preconditions.checkState(
        responseFuture == null, "Send() was called twice on the same HttpRequest");
    this.responseFuture =
        networkExecutor.submit(new Runnable() {
          public void run() {
            sendSynchronously(uri, verb, postData, headers);
          }
        });
  }

  /** Sends the HTTP channel request synchronously, blocking until a reply comes. */
  private void sendSynchronously(
      UriBuilder uri,
      String verb,
      @Nullable String postData,
      @Nullable Map<String, String> headers) {
    try {
      logger.atFine().log(
          "Sending HTTP %s request: %s to url: %s with headers: %s (%s)",
          verb, postData, uri, headers, this);
      HttpContent content = prepareContent(postData, headers);
      HttpRequest request =
          httpRequestFactory.buildRequest(verb, new GenericUrl(uri.getUri().toString()), content);
      request.setThrowExceptionOnExecuteError(false);
      if (headers != null) {
        copyHeaders(headers, request.getHeaders());
      }
      HttpResponse response = request.execute();
      readHttpResponse(response);
    } catch (IOException e) {
      processRequestError(e);
    }
  }

  /**
   * Prepares {@link HttpContent} out of the given string.
   *
   * <p>Note that the Content-Type is taken from the given headers.
   */
  private static HttpContent prepareContent(
      @Nullable String content, @Nullable Map<String, String> headers) {
    if (content == null) {
      return new EmptyContent();
    }
    Preconditions.checkNotNull(headers, "headers can't be null when content is not null");
    String contentType = headers.get("Content-Type");
    Preconditions.checkNotNull(
        contentType, "Content-Type must be specified in headers when content is not null");
    final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    return new AbstractHttpContent(contentType) {
      @Override
      public void writeTo(OutputStream out) throws IOException {
        out.write(bytes);
      }
    };
  }

  /**
   * Copy the headers from the source map of strings to the target HTTP headers.
   *
   * <p>Note that HttpHeaders store some of the headers directly in its fields with additional type
   * information. These known fields are stored as a list, which is why we handle these separately
   * to prevent cast exceptions. Obviously, there is now no way to pass multiple values for these
   * known list fields. This is enforced by the current WebChannel API, but does not seem to be
   * limiting as we don't expect a need to pass more information other than Authentication cookies.
   */
  private static void copyHeaders(Map<String, String> source, HttpHeaders target) {
    for (Map.Entry<String, String> header : source.entrySet()) {
      if (target.getClassInfo().getFieldInfo(header.getKey()) != null) {
        // This is a known HttpHeaders field that is expected to be passed as a list.
        target.set(header.getKey(), ImmutableList.of(header.getValue()));
      } else {
        target.set(header.getKey(), header.getValue());
      }
    }
  }

  /**
   * Reads the HTTP response for the channel request and updates the channel state accordingly.
   *
   * <p>This method blocks waiting on the response content arriving over the pending Http request.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void readHttpResponse(HttpResponse response) throws IOException {
    Preconditions.checkArgument(response != null);
    readResponseHeaders(response);
    readResponseBody(response);
    finishReadingResponse();
  }

  /**
   * Changes the state to INTERACTIVE as currently reading the response.
   *
   * <p>Also exports all the other metadata about the response, e.g., headers and possible errors.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void readResponseHeaders(HttpResponse response) {
    synchronized (lock) {
      readyState = RequestReadyState.INTERACTIVE;
      responseHeaders = response.getHeaders();
      status = response.getStatusCode();
      if (response.isSuccessStatusCode()) {
        lastErrorCode = RequestErrorCode.NO_ERROR;
      } else {
        // TODO: Make sure this mapping covers the error space actually used in WebChannel
        // communication and that the default HTTP_ERROR does not hide any important case that has
        // to be distinguished.
        //
        // Error codes yet unmapped:
        // - FILE_NOT_FOUND;
        // - FF_SILENT_ERROR,
        // - CUSTOM_ERROR,
        // - ABORT,
        // - TIMEOUT,
        // - OFFLINE
        switch (status) {
          case 401: // Unauthorized
          case 403: // Forbidden
          case 404: // Not Found
            lastErrorCode = RequestErrorCode.ACCESS_DENIED;
            break;
          default:
            lastErrorCode = RequestErrorCode.HTTP_ERROR;
        }
        logger.atWarning().log("HTTP channel request failed with status: %d", status);
      }
    }
  }

  /**
   * Parses the contents of the HTTP response if any.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void readResponseBody(HttpResponse response) throws IOException {
    InputStream content = response.getContent();
    if (content == null) {
      logger.atFine().log("No content in channel response");
      return;
    }
    String encoding =
        MoreObjects.firstNonNull(response.getContentEncoding(), StandardCharsets.UTF_8.name());
    CharBuffer buffer = CharBuffer.allocate(READ_CHUNK_SIZE_BYTES);
    Reader reader = new InputStreamReader(content, encoding);
    try {
      while (readChunk(reader, buffer)) {
        processChunk(buffer);
      }
    } finally {
      Closeables.closeQuietly(reader);
    }
  }

  /**
   * Read a chunk of the data from the given reader.
   *
   * @param reader Reader to read the chunk from
   * @param buffer {@link CharBuffer} to fill the read data with
   * @return Returns {@code false} when at the end of the input.
   */
  private static boolean readChunk(Reader reader, CharBuffer buffer) throws IOException {
    buffer.clear();
    int numRead = reader.read(buffer);
    if (numRead == -1) {
      return false;
    }
    buffer.flip();
    return true;
  }

  /**
   * Process new chunk read from the response body.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void processChunk(CharBuffer chunk) {
    logger.atFine().log("Read %d chars from the channel: \"%s\"", chunk.length(), chunk);
    synchronized (lock) {
      responseTextBuilder.append(chunk);
    }
    apiThreadExecutor.execute(new Runnable() {
      public void run() {
        notifyReadyStateChange();
      }
    });
  }

  /**
   * Sets the state as completed as the response has been fully read now.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void finishReadingResponse() {
    logger.atFine().log("Finished reading channel response");
    synchronized (lock) {
      readyState = RequestReadyState.COMPLETE;
    }
    apiThreadExecutor.execute(new Runnable() {
      public void run() {
        notifyReadyStateChange();
      }
    });
  }

  /**
   * Handles the case when the HTTP request failed with an exception.
   *
   * <p>To be called from {@code networkExecutor}.
   */
  private void processRequestError(IOException exception) {
    logger.atWarning().withCause(exception).log("HTTP channel request failed");
    synchronized (lock) {
      if (aborted) {
        lastErrorCode = RequestErrorCode.ABORT;
      } else {
        lastErrorCode = RequestErrorCode.EXCEPTION;
      }
      readyState = RequestReadyState.COMPLETE;
    }
    apiThreadExecutor.execute(new Runnable() {
      public void run() {
        notifyReadyStateChange();
      }
    });
  }

  /**
   * Notifies the channel handler (if any) about a state change.
   *
   * <p>To be called from {@code apiThreadExecutor}.
   */
  private void notifyReadyStateChange() {
    if (getReadyStateChangeHandler() != null) {
      getReadyStateChangeHandler().onReadyStateChangeEvent(this);
    }
  }

  @Override
  public void abort() {
    synchronized (lock) {
      Preconditions.checkState(!aborted, "Duplicit abort call");
      Preconditions.checkNotNull(responseFuture, "Unexpected abort call before any send call");
      aborted = true;
      responseFuture.cancel(true);
    }
  }
}
