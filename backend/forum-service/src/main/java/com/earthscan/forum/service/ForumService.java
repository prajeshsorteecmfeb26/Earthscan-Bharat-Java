package com.earthscan.forum.service;

import com.earthscan.common.event.ForumCommentAddedEvent;
import com.earthscan.common.event.ForumPostCreatedEvent;
import com.earthscan.common.exception.ForbiddenException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.AuthenticatedUser;
import com.earthscan.forum.domain.ForumComment;
import com.earthscan.forum.domain.ForumPost;
import com.earthscan.forum.dto.CommentResponse;
import com.earthscan.forum.dto.CreateCommentRequest;
import com.earthscan.forum.dto.CreatePostRequest;
import com.earthscan.forum.dto.PostResponse;
import com.earthscan.forum.repository.ForumPostRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Thread and reply operations.
 *
 * <p>Author identity always comes from the JWT, never from the request body. The original controller
 * read {@code User.FindFirstValue(ClaimTypes.Name)} with a fallback to {@code "Unknown"}; that
 * fallback is dropped here because an unauthenticated caller cannot reach these methods at all, so
 * silently attributing a post to "Unknown" would only mask a broken security chain.</p>
 */
@Service
public class ForumService {

    private static final Logger log = LoggerFactory.getLogger(ForumService.class);

    private final ForumPostRepository repository;
    private final EventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;

    public ForumService(ForumPostRepository repository,
                        EventPublisher eventPublisher,
                        MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Every thread with its replies, newest first.
     *
     * <p>Kept unpaged for compatibility with the existing {@code Forum.jsx}, which fetches the whole
     * feed. That is fine at the current scale and will not remain so — {@link #feed} is the paged,
     * reply-free alternative the frontend should move to.</p>
     */
    public List<PostResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(PostResponse::from)
                .toList();
    }

    /** Paged feed without reply bodies. */
    public Page<PostResponse> feed(String category, Pageable pageable) {
        Page<ForumPost> page = (category == null || category.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByCategoryOrderByCreatedAtDesc(category.trim(), pageable);
        return page.map(PostResponse::summary);
    }

    /** Unanswered questions, for the Agriculture Expert work queue. */
    public Page<PostResponse> unanswered(Pageable pageable) {
        return repository.findByCommentCountAndResolvedFalse(0, pageable).map(PostResponse::summary);
    }

    public PostResponse findById(String id) {
        return PostResponse.from(getPostOrThrow(id));
    }

    /**
     * Distinct categories currently in use.
     *
     * <p>Uses {@link MongoTemplate} rather than a derived repository method: projecting a single
     * field to {@code List<String>} through a {@code findDistinct...By} query name is not reliably
     * supported by Spring Data MongoDB, and would fail at context startup rather than at compile
     * time. The explicit distinct call is unambiguous.</p>
     */
    public List<String> categories() {
        return mongoTemplate.findDistinct(
                new Query(), "category", ForumPost.class, String.class);
    }

    public PostResponse createPost(CreatePostRequest request, AuthenticatedUser author) {
        ForumPost post = new ForumPost(
                request.title().trim(),
                request.content().trim(),
                request.category().trim(),
                author.getId(),
                author.getName(),
                author.getPrimaryRole().getDisplayName());

        ForumPost saved = repository.save(post);
        log.info("Created forum post id={} by user id={} in category '{}'",
                saved.getId(), author.getId(), saved.getCategory());

        ForumPostCreatedEvent event = new ForumPostCreatedEvent();
        event.setPostId(saved.getId());
        event.setTitle(saved.getTitle());
        event.setCategory(saved.getCategory());
        event.setAuthorId(author.getId());
        event.setAuthorName(author.getName());
        event.setAuthorRole(author.getPrimaryRole().getDisplayName());
        eventPublisher.publish(RabbitTopology.ROUTING_FORUM_POST_CREATED, event);

        return PostResponse.from(saved);
    }

    public CommentResponse addComment(String postId,
                                      CreateCommentRequest request,
                                      AuthenticatedUser author) {
        ForumPost post = getPostOrThrow(postId);

        ForumComment comment = new ForumComment(
                request.content().trim(),
                author.getId(),
                author.getName(),
                author.getPrimaryRole().getDisplayName());

        post.addComment(comment);
        repository.save(post);

        log.info("Added comment id={} to post id={} by user id={}",
                comment.getId(), postId, author.getId());

        // Carries the thread author's id so notification-service can tell them their question was
        // answered without calling back into this service.
        ForumCommentAddedEvent event = new ForumCommentAddedEvent();
        event.setPostId(postId);
        event.setPostTitle(post.getTitle());
        event.setCommentId(comment.getId());
        event.setPostAuthorId(post.getAuthorId());
        event.setCommenterId(author.getId());
        event.setCommenterName(author.getName());
        event.setCommenterRole(author.getPrimaryRole().getDisplayName());
        eventPublisher.publish(RabbitTopology.ROUTING_FORUM_COMMENT_ADDED, event);

        return CommentResponse.from(comment);
    }

    /** Only the thread author may mark their own question resolved. */
    public PostResponse setResolved(String postId, boolean resolved, AuthenticatedUser caller) {
        ForumPost post = getPostOrThrow(postId);
        if (!post.isAuthoredBy(caller.getId()) && !caller.isAdmin()) {
            throw new ForbiddenException(
                    "Only the author of a question may mark it resolved");
        }
        post.setResolved(resolved);
        return PostResponse.from(repository.save(post));
    }

    public void deletePost(String postId, AuthenticatedUser caller) {
        ForumPost post = getPostOrThrow(postId);
        if (!post.isAuthoredBy(caller.getId()) && !caller.isAdmin()) {
            log.warn("User id={} attempted to delete post id={} authored by id={}",
                    caller.getId(), postId, post.getAuthorId());
            throw new ForbiddenException("You may only delete your own posts");
        }
        repository.delete(post);
        log.info("Deleted forum post id={} by user id={}", postId, caller.getId());
    }

    public void deleteComment(String postId, String commentId, AuthenticatedUser caller) {
        ForumPost post = getPostOrThrow(postId);

        ForumComment comment = post.getComments().stream()
                .filter(candidate -> candidate.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Comment", commentId));

        boolean isCommentAuthor = comment.getAuthorId() != null
                && comment.getAuthorId().equals(caller.getId());
        if (!isCommentAuthor && !caller.isAdmin()) {
            throw new ForbiddenException("You may only delete your own comments");
        }

        post.removeComment(commentId);
        repository.save(post);
        log.info("Deleted comment id={} from post id={} by user id={}",
                commentId, postId, caller.getId());
    }

    private ForumPost getPostOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Forum post", id));
    }
}
