package bidiweb.webchannel.client.protocol_v8;

import java.util.Map;

interface Wire {
  int LATEST_CHANNEL_VERSION = 8;

  class QueuedMap {
    public QueuedMap(long mapId, Map<String, String> map, Object context) {
      this.mapId = mapId;
      this.map = map;
      this.context = context;
    }

    public long mapId;
    public Map<String, String> map;
    public Object context;
  }

  interface BadMessageHandler {
    void onBadMessage(Object message);
  }
}
