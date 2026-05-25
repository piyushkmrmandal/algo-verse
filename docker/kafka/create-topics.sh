#!/bin/bash
# AlgoVerse Kafka topic creation script.
# Run once after the broker is healthy.

set -e

KAFKA_BROKER="${KAFKA_BROKER:-kafka:9092}"
PARTITIONS=3
REPLICATION_FACTOR=1

TOPICS=(
  "algoverse.submission.graded"
  "algoverse.hints.requested"
  "algoverse.hints.response"
  "algoverse.xp.awarded"
  "algoverse.badge.earned"
  "algoverse.user.registered"
  "algoverse.execution.requested"
  "algoverse.execution.completed"
  "algoverse.collab.room.created"
  "algoverse.collab.room.ended"
)

echo "Waiting for Kafka broker at ${KAFKA_BROKER}..."
until kafka-topics --bootstrap-server "${KAFKA_BROKER}" --list > /dev/null 2>&1; do
  echo "Kafka not ready yet, retrying in 3 seconds..."
  sleep 3
done

echo "Kafka is ready. Creating topics..."

for TOPIC in "${TOPICS[@]}"; do
  if kafka-topics --bootstrap-server "${KAFKA_BROKER}" --list 2>/dev/null | grep -q "^${TOPIC}$"; then
    echo "Topic '${TOPIC}' already exists, skipping."
  else
    kafka-topics \
      --bootstrap-server "${KAFKA_BROKER}" \
      --create \
      --topic "${TOPIC}" \
      --partitions "${PARTITIONS}" \
      --replication-factor "${REPLICATION_FACTOR}"
    echo "Created topic: ${TOPIC}"
  fi
done

echo "All Kafka topics are ready."
