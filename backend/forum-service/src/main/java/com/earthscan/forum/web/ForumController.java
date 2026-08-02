package com.earthscan.forum.web;

import com.earthscan.common.exception.ApiErrorResponse;
import com.earthscan.common.security.SecurityUtils;
import com.earthscan.forum.dto.CommentResponse;
import com.earthscan.forum.dto.CreateCommentRequest;
import com.earthscan.forum.dto.CreatePostRequest;
import com.earthscan.forum.dto.PostResponse;
import com.earthscan.forum.service.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Community Q&amp;A endpoints.
 *
 * <p>Every endpoint requires authentication, matching the original {@code [Authorize]} on the class.
 * The forum is a members-only space: it carries location-specific farming details that should not be
 * scraped anonymously.</p>
 */
@RestController
@RequestMapping("/api/forum")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Forum", description = "Discussion threads between farmers and agriculture experts")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/posts")
    @Operation(summary = "List all threads with their replies",
            description = "Newest first, unpaged. Preserves the exact response shape the React "
                    + "Forum page already consumes. Use /posts/feed for a paged, lighter alternative.")
    public ResponseEntity<List<PostResponse>> listPosts() {
        return ResponseEntity.ok(forumService.findAll());
    }

    @GetMapping("/posts/feed")
    @Operation(summary = "Paged thread feed without reply bodies",
            description = "Optionally filtered by category. Reply bodies are omitted; fetch a single "
                    + "thread to read them.")
    public ResponseEntity<Page<PostResponse>> feed(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(forumService.feed(category, pageable));
    }

    @GetMapping("/posts/unanswered")
    @Operation(summary = "Threads with no replies yet",
            description = "Backs the Agriculture Expert work queue on the Answer Queries page.")
    public ResponseEntity<Page<PostResponse>> unanswered(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(forumService.unanswered(pageable));
    }

    @GetMapping("/categories")
    @Operation(summary = "List categories currently in use")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(forumService.categories());
    }

    @GetMapping("/posts/{id}")
    @Operation(summary = "Fetch a single thread with all replies")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thread found"),
            @ApiResponse(responseCode = "404", description = "No such thread",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PostResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(forumService.findById(id));
    }

    @PostMapping("/posts")
    @Operation(summary = "Open a new thread",
            description = "Author is taken from the token. Publishes a ForumPostCreatedEvent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Thread created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        PostResponse created = forumService.createPost(request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Reply to a thread",
            description = "Publishes a ForumCommentAddedEvent, which notification-service turns into "
                    + "a 'your question was answered' notice for the thread author.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reply added"),
            @ApiResponse(responseCode = "404", description = "No such thread",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse created =
                forumService.addComment(postId, request, SecurityUtils.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/posts/{postId}/resolved")
    @Operation(summary = "Mark a question resolved",
            description = "Restricted to the thread author, or an administrator.")
    public ResponseEntity<PostResponse> setResolved(@PathVariable String postId,
                                                    @RequestParam boolean resolved) {
        return ResponseEntity.ok(
                forumService.setResolved(postId, resolved, SecurityUtils.requireCurrentUser()));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Delete a thread",
            description = "Restricted to the author, or an administrator moderating content.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "Not the author",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deletePost(@PathVariable String postId) {
        forumService.deletePost(postId, SecurityUtils.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @Operation(summary = "Delete a reply",
            description = "Restricted to the reply's author, or an administrator.")
    public ResponseEntity<Void> deleteComment(@PathVariable String postId,
                                              @PathVariable String commentId) {
        forumService.deleteComment(postId, commentId, SecurityUtils.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
