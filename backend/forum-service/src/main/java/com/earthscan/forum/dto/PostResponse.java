package com.earthscan.forum.dto;

import com.earthscan.forum.domain.ForumPost;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Full thread with replies.
 *
 * <p>Mirrors the projection the ASP.NET {@code ForumController} produced, so {@code Forum.jsx}
 * continues to render without modification. Note the id is now a String — MongoDB {@code ObjectId}
 * rather than a MySQL auto-increment integer. The frontend uses it opaquely as a React key and a URL
 * segment, so the change is transparent there, but any code doing arithmetic on a post id would need
 * updating.</p>
 */
@Schema(name = "PostResponse")
public record PostResponse(
        @Schema(example = "66a3f1c2e8b4a51d3c7f9012",
                description = "MongoDB ObjectId as a 24-character hex string")
        String id,
        String title,
        String content,
        String authorName,
        String authorRole,
        String category,
        Instant createdAt,
        int commentCount,
        boolean resolved,
        List<CommentResponse> comments) {

    public static PostResponse from(ForumPost post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorName(),
                post.getAuthorRole(),
                post.getCategory(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.isResolved(),
                post.getCommentsInOrder().stream().map(CommentResponse::from).toList());
    }

    /** Feed variant that omits replies, so listing 50 threads does not ship every reply body. */
    public static PostResponse summary(ForumPost post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorName(),
                post.getAuthorRole(),
                post.getCategory(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.isResolved(),
                List.of());
    }
}
