package com.sapphireai.model.api.confluence;

import java.time.LocalDateTime;

/**
 * Model representing a Confluence page from API.
 */
public class ConfluencePage {

    private String id;
    private String type;
    private String title;
    private ConfluenceSpace space;
    private int version;
    private Body body;
    private History history;

    // Nested classes for complex structure
    public static class Body {
        private Storage storage;

        public Storage getStorage() {
            return storage;
        }

        public void setStorage(Storage storage) {
            this.storage = storage;
        }
    }

    public static class Storage {
        private String value;
        private String representation;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getRepresentation() {
            return representation;
        }

        public void setRepresentation(String representation) {
            this.representation = representation;
        }
    }

    public static class History {
        private LastUpdated lastUpdated;

        public LastUpdated getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(LastUpdated lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
    }

    public static class LastUpdated {
        private User by;
        private LocalDateTime when;

        public User getBy() {
            return by;
        }

        public void setBy(User by) {
            this.by = by;
        }

        public LocalDateTime getWhen() {
            return when;
        }

        public void setWhen(LocalDateTime when) {
            this.when = when;
        }
    }

    public static class User {
        private String displayName;
        private String email;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    // Constructors
    public ConfluencePage() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ConfluenceSpace getSpace() {
        return space;
    }

    public void setSpace(ConfluenceSpace space) {
        this.space = space;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history;
    }
}