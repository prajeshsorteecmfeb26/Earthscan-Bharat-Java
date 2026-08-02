package com.earthscan.forum.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A discussion thread with its replies embedded.
 *
 * <p>{@code commentCount} is stored rather than derived. That is denormalisation, and it is
 * deliberate: the feed shows a reply count on every card, and computing it means loading the whole
 * comments array for every post in the list. Keeping the count on the document lets the feed
 * projection skip the array entirely. The cost is that the field must be maintained on every
 * mutation, which is why only {@link #addComment} and {@link #removeComment} touch it.</p>
 */
@Document(collection = "forum_posts")
@CompoundIndex(name = "idx_category_created", def = "{'category': 1, 'createdAt': -1}")
public class ForumPost {

    @Id
    private String id;

    private String title;

    private String content;

    @Indexed
    private String category;

    @Indexed
    private Long authorId;

    private String authorName;

    private String authorRole;

    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    private List<ForumComment> comments = new ArrayList<>();

    private int commentCount;

    /** Set by the thread author once an expert's reply has answered the question. */
    private boolean resolved;

    private boolean anonymised;

    public ForumPost() {
    }

    public ForumPost(String title, String content, String category,
                     Long authorId, String authorName, String authorRole) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorRole = authorRole;
    }

    public void addComment(ForumComment comment) {
        this.comments.add(comment);
        this.commentCount = this.comments.size();
        this.updatedAt = Instant.now();
    }

    public boolean removeComment(String commentId) {
        boolean removed = this.comments.removeIf(comment -> comment.getId().equals(commentId));
        if (removed) {
            this.commentCount = this.comments.size();
            this.updatedAt = Instant.now();
        }
        return removed;
    }

    /** Replies in the order they were written, which is how a thread must read. */
    public List<ForumComment> getCommentsInOrder() {
        return comments.stream()
                .sorted(Comparator.comparing(ForumComment::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** See {@link ForumComment#anonymise()} for why the content survives. */
    public void anonymise() {
        this.authorName = "Deleted user";
        this.authorId = null;
        this.anonymised = true;
        this.updatedAt = Instant.now();
    }

    public boolean isAuthoredBy(Long userId) {
        return authorId != null && authorId.equals(userId);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ForumComment> getComments() {
        return comments;
    }

    public void setComments(List<ForumComment> comments) {
        this.comments = comments == null ? new ArrayList<>() : comments;
        this.commentCount = this.comments.size();
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public boolean isAnonymised() {
        return anonymised;
    }

    public void setAnonymised(boolean anonymised) {
        this.anonymised = anonymised;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ForumPost post)) {
            return false;
        }
        return id != null && Objects.equals(id, post.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
