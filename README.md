# Subtub

A basic pubsub server built with scala, fs2, and cats effect.

## Running with Docker

```bash
docker run -p 8080:80 \
  --env SUBTUB_BIND_HOST="0.0.0.0" \
  --env SUBTUB_BIND_PORT=80 \
  --env SUBTUB_SHARD_COUNT=100 \
  stefanostouf/subtub:latest
```

## Usage

### Endpoints

The server exposes two endpoints:
* `GET /subscribe?max_queued={MAX_QUEUED}&streams={STREAM_ID_1}&streams={STREAM_ID_2}&streams={STREAM_ID_3}&...` subscribes to the provided streams and initiates a websocket connection, emiting all published messages matching the streams.
* `POST /publish` publishes a batch of messages. The messages should be placed in the request body, which is expected to have the following structure:
```json
[
  { "publish_to": "{STREAM_ID_1}",  "payload": "{PAYLOAD_1}" },
  { "publish_to": "{STREAM_ID_2}",  "payload": "{PAYLOAD_2}" },
  { "publish_to": "{STREAM_ID_3}",  "payload": "{PAYLOAD_3}" }
]
```

### Routing

Each message is routed to a subscriber when there is a match between the published and the subscribed stream ids.

Stream ids are strings containing at least one colon `:`. The colons segment the string into parts that are used for matching.
Examples of stream ids:
* `first:` valid
* `first:second` valid
* `first:second:` valid
* `first:second:third` valid
* `first` invalid
* *empty string* invalid

stream ids match when they share any prefix of segments.
Examples of matching stream ids:
* `first:`, `first:second`, `first:second:`, `first:second:third` all match with each other.
* However, `first:second` and `second:first` do not match.
* Note that segments must match in their entirety, so `first:second` and `first:sec` also do not match.

If a client subscribes to multiple stream ids that match the same message, the message will be delivered once for each subscription.

Messages are not persisted, so if there is no subscriber to receive a message, it is lost.

### Max Queued

The max queued parameter can take any value from 1 to 250 (both sides inclusive) and assigns a buffer size to the subscriber's queue; if the subscriber is unable to keep up with the influx of messages and they exceed the buffer size, old messages will be dropped. The buffer is shared between all streams that are subscribed to in a single websocket connection.

### Utilities

This project contains a Dockerfile for creating a docker image running wscat. This can be used for testing the subscribe endpoint and is found [here](./docker/wscat/Dockerfile).

### Example

Assuming a subtub server is running and is reachable at localhost:8080, the following commands will create two subscribers, one of them listening to streams `first:second` and `second:first` and the other one listening to `first:`. Both subscribers use a buffer size of 10.
```bash
docker run -it --rm --net=host wscat:latest 'ws://localhost:8080/subscribe?max_queued=10&streams=first:second&streams=second:first'
Connected (press CTRL+C to quit)
>

# In another terminal
docker run -it --rm --net=host wscat:latest 'ws://localhost:8080/subscribe?max_queued=10&streams=first:'
Connected (press CTRL+C to quit)
>
```

Then, assuming the file `/tmp/messages.json` contains the following content:
```json
[
  {"publish_to":"first:","payload":"hello 1"},
  {"publish_to":"first:second","payload":"hello 2"},
  {"publish_to":"first:second:","payload":"hello 3"},
  {"publish_to":"first:second:third","payload":"hello 4"},
  {"publish_to":"first:third","payload":"hello 5"},
  {"publish_to":"second:","payload":"hello 6"},
  {"publish_to":"second:first","payload":"hello 7"},
  {"publish_to":"second:third","payload":"hello 8"}
]
```

The following command will publish the messages to subtub:
```bash
curl -v -d @/tmp/messages.json -X POST 'http://localhost:8080/publish'
```

Each subscriber will receive a subset of messages:
```bash
docker run -it --rm --net=host wscat:latest 'ws://localhost:8080/subscribe?max_queued=10&streams=first:second&streams=second:first'
Connected (press CTRL+C to quit)
< {"publish_to":"first","payload":"hello 1"}
< {"publish_to":"first:second","payload":"hello 2"}
< {"publish_to":"first:second","payload":"hello 3"}
< {"publish_to":"first:second:third","payload":"hello 4"}
< {"publish_to":"second","payload":"hello 6"}
< {"publish_to":"second:first","payload":"hello 7"}
```

```bash
docker run -it --rm --net=host wscat:latest 'ws://localhost:8080/subscribe?max_queued=10&streams=first:'
Connected (press CTRL+C to quit)
< {"publish_to":"first","payload":"hello 1"}
< {"publish_to":"first:second","payload":"hello 2"}
< {"publish_to":"first:second","payload":"hello 3"}
< {"publish_to":"first:second:third","payload":"hello 4"}
< {"publish_to":"first:third","payload":"hello 5"}
```

Notice that the messsage with payload `"hello 8"` is received by no subscribers.


## Future Work

As it stands, this repository contains a very bare-bones pubsub system, which may be extended in the future in several ways. An interesting future venture would be establishing a sharded architecture, where subtub would run in a cluster and each server within the cluster would take ownership of a part of the stream-set. Some work to that end has already been done, as the first segment of each stream id is internally treated as a partition key, which can be used to shard the stream-set. Ideally, a lot of the work done for the [stoufexis/raft](https://github.com/stoufexis/raft) project would be reused here, enabling the cluster to reach consensus on which server is assigned each shard.
