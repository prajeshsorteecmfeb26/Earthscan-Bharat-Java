package com.earthscan.forum.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A reply, embedded inside its {@link ForumPost} document rather than stored in its own collection.
 *
 * <p>The id is a client-generated UUID because embedded documents get no id from MongoDB — only the
 * top-level document does. A UUID is needed so a specific reply can still be addressed for editing
 * or deletion.</p>
 */
public class ForumComment {

    private String id = UUID.randomUUID().toString();
    private String content;
    private Long authorId;
    private String authorName;
    private String authorRole;
    private Instant createdAt = Instant.now();
    private boolean anonymised;

    public ForumComment() {
    }

    public ForumComment(String content, Long authorId, String authorName, String authorRole) {
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorRole = authorRole;
    }

    /**
     * Strips identifying details while keeping the content, so a thread stays readable after its
     * contributor deletes their account. Hard-deleting replies would leave answers referring to
     * questions that no longer make sense.
     */
    public void anonymise() {
        this.authorName = "Deleted user";
        this.authorId = null;
        this.anonymised = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorRole() {
        return authorRole;
    }

    public void setAuthorRole(String authorRole) {
        this.authorRole = authorRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAnonymised() {
        return anonymised;
    }

    public void setAnonymised(boolean anonymised) {
        this.anonymised = anonymised;
    }
}
