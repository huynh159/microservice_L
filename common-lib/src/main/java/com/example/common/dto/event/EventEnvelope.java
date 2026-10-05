package com.example.common.dto.event;

import java.time.Instant;

public class EventEnvelope<T> {
    private String eventId;
    private String eventType;
    private int version = 1;
    private Instant occurredAt;
    private String source;
    private T payload;

    public EventEnvelope() {
    }

    public EventEnvelope(String eventId, String eventType, int version, Instant occurredAt, String source, T payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.version = version;
        this.occurredAt = occurredAt;
        this.source = source;
        this.payload = payload;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public static class Builder<T> {
        private String eventId;
        private String eventType;
        private int version = 1;
        private Instant occurredAt;
        private String source;
        private T payload;

        public Builder<T> eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder<T> eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<T> version(int version) {
            this.version = version;
            return this;
        }

        public Builder<T> occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder<T> source(String source) {
            this.source = source;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public EventEnvelope<T> build() {
            return new EventEnvelope<>(eventId, eventType, version, occurredAt, source, payload);
        }
    }
}
