package com.earthscan.common.event;

/**
 * Published by forum-service when a reply is added.
 *
 * <p>Carries {@code postAuthorId} so notification-service can tell the thread owner that their
 * question was answered without having to call back into forum-service.</p>
 */
public class ForumCommentAddedEvent extends IntegrationEvent {

    private String postId;
    private String postTitle;
    private String commentId;
    private Long postAuthorId;
    private Long commenterId;
    private String commenterName;
    private String commenterRole;

    public ForumCommentAddedEvent() {
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public Long getPostAuthorId() {
        return postAuthorId;
    }

    public void setPostAuthorId(Long postAuthorId) {
        this.postAuthorId = postAuthorId;
    }

    public Long getCommenterId() {
        return commenterId;
    }

    public void setCommenterId(Long commenterId) {
        this.commenterId = commenterId;
    }

    public String getCommenterName() {
        return commenterName;
    }

    public void setCommenterName(String commenterName) {
        this.commenterName = commenterName;
    }

    public String getCommenterRole() {
        return commenterRole;
    }

    public void setCommenterRole(String commenterRole) {
        this.commenterRole = commenterRole;
    }
}
