package com.earthscan.forum.mapper;

import com.earthscan.forum.domain.ForumComment;
import com.earthscan.forum.domain.ForumPost;
import com.earthscan.forum.dto.CommentResponse;
import com.earthscan.forum.dto.PostResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Mapping between forum documents and their DTOs. */
@Mapper
public interface ForumMapper {

    /** Full thread, replies included and chronologically ordered. */
    @Mapping(target = "comments", source = "commentsInOrder")
    PostResponse toResponse(ForumPost post);

    /**
     * Feed projection with replies omitted.
     *
     * <p>A separate method rather than a flag, so the caller cannot accidentally ship every reply
     * body in a 50-thread feed response.</p>
     */
    @Named("summary")
    @Mapping(target = "comments", expression = "java(java.util.List.of())")
    PostResponse toSummary(ForumPost post);

    CommentResponse toCommentResponse(ForumComment comment);

    List<CommentResponse> toCommentResponses(List<ForumComment> comments);
}
