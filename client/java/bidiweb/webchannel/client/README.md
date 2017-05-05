## design goals

* minimum dependency on non-JDK libs
* use the JDK Java client, which means two dedicated I/O threads are required to manage a single channel
* strictly follow the JS client API (goog.net.WebChannel) and the protocol,
  so the Java client can be used to test the wire protocol as well as the client/server implementation in different languages
* JDK 6+
