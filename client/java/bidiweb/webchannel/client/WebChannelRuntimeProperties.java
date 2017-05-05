package bidiweb.webchannel.client;

import javax.annotation.concurrent.ThreadSafe;

/**
 * The runtime properties of a channel.
 *
 * This is a mutable object associated with the underlying channel object.
 */
@ThreadSafe
public class WebChannelRuntimeProperties {

  private int concurrentRequestLimit;
  private boolean spdyEnabled;
  private boolean serverFlowControl;
  private int nonAckedMessageCount;
  private int lastStatusCode;
  private String httpSessionId;

  public WebChannelRuntimeProperties() {
  }

  public synchronized int getConcurrentRequestLimit() {
    return concurrentRequestLimit;
  }

  public synchronized void setConcurrentRequestLimit(
      int concurrentRequestLimit) {
    this.concurrentRequestLimit = concurrentRequestLimit;
  }

  public synchronized boolean isSpdyEnabled() {
    return spdyEnabled;
  }

  public synchronized void setSpdyEnabled(boolean spdyEnabled) {
    this.spdyEnabled = spdyEnabled;
  }

  public interface AckCommitCallback {
    void ackCommit();
  }

  public synchronized void commit(AckCommitCallback callback) {
    throw new UnsupportedOperationException();
  }

  public synchronized int getNonAckedMessageCount() {
    return nonAckedMessageCount;
  }

  public synchronized void setNonAckedMessageCount(int nonAckedMessageCount) {
    this.nonAckedMessageCount = nonAckedMessageCount;
  }

  public interface NotifyNonAckedMessageCountCallback {
    void notifyNonAckedMessageCount();
  }

  public synchronized void notifyNonAckedMessageCount(long count,
      NotifyNonAckedMessageCountCallback callback) {
    throw new UnsupportedOperationException();
  }

  public interface OnCommitCallback {
    void onCommit(Object commitId);
  }

  public synchronized void onCommit(OnCommitCallback callback) {
    throw new UnsupportedOperationException();
  }

  public synchronized void ackCommit(Object commitId) {
    throw new UnsupportedOperationException();
  }

  public synchronized int getLastStatusCode() {
    return lastStatusCode;
  }

  public synchronized void setLastStatusCode(int lastStatusCode) {
    this.lastStatusCode = lastStatusCode;
  }

  public synchronized String getHttpSessionId() {
    return httpSessionId;
  }

  public synchronized void setHttpSessionId(String httpSessionId) {
    this.httpSessionId = httpSessionId;
  }

  @Override
  public synchronized String toString() {
    return "WebChannelRuntimeProperties{"
        + "concurrentRequestLimit=" + getConcurrentRequestLimit()
        + ", spdyEnabled=" + isSpdyEnabled()
        + ", nonAckedMessageCount=" + getNonAckedMessageCount()
        + ", lastStatusCode=" + getLastStatusCode()
        + ", httpSessionId=" + getHttpSessionId()
        + '}';
  }
}
