# Subtub

A stupid little pubsub server built with scala, fs2, and cats effect.

## Installation with docker

```
docker run -p 8080:80 \
  --env SUBTUB_BIND_HOST="0.0.0.0" \
  --env SUBTUB_BIND_PORT=80 \
  --env SUBTUB_SHARD_COUNT=100 \
  stefanostouf/subtub:latest
```

## Usage

TODO
