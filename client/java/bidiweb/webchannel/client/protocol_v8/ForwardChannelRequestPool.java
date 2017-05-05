package bidiweb.webchannel.client.protocol_v8;

import java.util.HashSet;
import java.util.Set;

class ForwardChannelRequestPool {

  // spdy always enabled for mobile

  private static final int MAX_POOL_SIZE = 10;

  private int maxPoolSizeConfigured;
  private int maxSize;
  private Set<ChannelRequest> requestPool;
  private ChannelRequest request;

  public ForwardChannelRequestPool(int maxPoolSize) {
    if (maxPoolSize <= 0) {
      maxPoolSizeConfigured = MAX_POOL_SIZE;
    } else {
      maxPoolSizeConfigured = maxPoolSize;
    }

    this.maxSize = maxPoolSizeConfigured;

    this.requestPool = null;

    if (this.maxSize > 1) {
      this.requestPool = new HashSet<>();
    }

    this.request = null;
  }

  public void applyClientProtocol(String clientProtocol) {
    // no-op
  }

  public boolean isFull() {
    if (this.request != null) {
      return true;
    }

    if (this.requestPool != null) {
      return this.requestPool.size() >= this.maxSize;
    }

    return false;
  }

  public int getMaxSize() {
    return this.maxSize;
  }

  public int getRequestCount() {
    if (this.request != null) {
      return 1;
    }

    if (this.requestPool != null) {
      return this.requestPool.size();
    }

    return 0;
  }

  public boolean hasRequest(ChannelRequest req) {
    if (this.request != null) {
      return this.request == req;
    }

    if (this.requestPool != null) {
      return this.requestPool.contains(req);
    }

    return false;
  }

  public void addRequest(ChannelRequest req) {
    if (this.requestPool != null) {
      this.requestPool.add(req);
    } else {
      this.request = req;
    }
  }

  public boolean removeRequest(ChannelRequest req) {
    if (this.request != null && this.request == req) {
      this.request = null;
      return true;
    }

    if (this.requestPool != null && this.requestPool.contains(req)) {
      this.requestPool.remove(req);
      return true;
    }

    return false;
  }

  public void cancel() {
    if (this.request != null) {
      this.request.cancel();
      this.request = null;
      return;
    }

    if (this.requestPool != null && !this.requestPool.isEmpty()) {
      for (ChannelRequest req : requestPool) {
        req.cancel();
      }
      this.requestPool.clear();
    }
  }

  public boolean hasPendingRequest() {
    return (this.request != null) || (this.requestPool != null && !this.requestPool.isEmpty());
  }

  public boolean forceComplete(CompletionCallback callback) {
    if (this.request != null) {
      this.request.cancel();
      callback.onComplete(this.request);
      return true;
    }

    if (this.requestPool != null && !this.requestPool.isEmpty()) {
      for (ChannelRequest req : this.requestPool) {
        req.cancel();
        callback.onComplete(req);
      }
      return true;
    }

    return false;
  }

  public interface CompletionCallback {
    void onComplete(ChannelRequest request);
  }
}
