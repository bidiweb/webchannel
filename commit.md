#Commit Protocol (experimental)

An experimental protocol to support explicit in-band commits over
a bidirectional channel.

##Use cases and requirements
1. Support in-band commits with explicit acks from the application, similar to
   situation when an application needs to issue a snapshot or sync/flush command.
2. Enable end-to-end flow-control, assuming messages are delivered in order
   and reliably over a single channel.
3. Allow recovery of in-flight messages from a (permanently) failed channel,
   likely caused by a failed peer.

##The algorithm
1. Define a new control message: commit, with a monolithically increasing seqId
   that is independent of the message seqId.
2. The client may issue a commit message, which is to be delivered in order
   on the server side with other messages (data or control messages).
3. The client may issue any new messages after a commit message; and may issue
   a new commit message too which will effectively cancel the previous commit
   as decided by the implementation.
4. The client is expected to receive a commit-ack message (with the same
   seqId as the commit message) from the server. Any messages sent before the
   acked commit can be garbaged collected (permanently) by the implementation.
   The protocol doesn't specify any timeout for the commit-ack message, and
   it's the application's responsibility to abort the channel should a commit
   be timed out.
5. The server will deliver the commit message to the application; and expect
   an ack callback. Obsoleted commits may be ignored. New messages will
   be delivered as usual when there is a commit pending to be acked.
6. This algorithm is a symmetric one, i.e. 2-5 applies to server-to-client
   communication too.

##Flow control
1. For this discussion, we assume client and server message-delivery are
   push-based, async APIs (similar to WebSocket), and delivered messages
   are saved in an ordered queue of some kind owned by the application.
2. The sender, once a commit is issued, should check the "pending message
   queue" (issued after the last commit has been acked), and enter into a
   suspending state to stop or slow down if the queue reaches a certain limit.
   When an ack comes back, the sender may exit from such a state and resume.
3. The receiver may generate a push-back signal to the sender by delaying
   the ack for the most recent commit. Newly-arrived messages still need to be
   delivered to the application while an ack is pending.

##Failure recovery
1. When a channel is permanently failed, e.g. the peer is gone or the network
   is partitioned, all the pending messages should be made available to the
   application, which may save and retry those messages with a new channel.
2. Messages buffered at the HTTP/WebChannel level constitute only a subset of
   unacked pending messages.

##Client-side APIs (for WebChannel)

```
1. channel.commit(callback())                  # client is the sender
   channel.getPendingMessages()
2. channel.onCommit(commitId)                  # client is the receiver
   channel.ackCommit(commitId)
```
