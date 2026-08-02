package com.earthscan.forum.dto;

import com.earthscan.forum.domain.ForumComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Field names match the shape {@code Forum.jsx} already renders. */
@Schema(name = "CommentResponse")
public record CommentResponse(
        String id,
        String content,
        String authorName,
        String authorRole,
        Instant createdAt) {

    public static CommentResponse from(ForumComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthorName(),
                comment.getAuthorRole(),
                comment.getCreatedAt());
    }
}
