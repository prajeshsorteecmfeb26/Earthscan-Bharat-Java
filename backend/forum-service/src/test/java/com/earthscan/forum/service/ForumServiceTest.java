package com.earthscan.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.earthscan.common.event.ForumCommentAddedEvent;
import com.earthscan.common.event.ForumPostCreatedEvent;
import com.earthscan.common.event.IntegrationEvent;
import com.earthscan.common.exception.ForbiddenException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.AuthenticatedUser;
import com.earthscan.common.security.RoleName;
import com.earthscan.forum.domain.ForumComment;
import com.earthscan.forum.domain.ForumPost;
import com.earthscan.forum.dto.CommentResponse;
import com.earthscan.forum.dto.CreateCommentRequest;
import com.earthscan.forum.dto.CreatePostRequest;
import com.earthscan.forum.dto.PostResponse;
import com.earthscan.forum.repository.ForumPostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForumService")
class ForumServiceTest {

    private static final Long FARMER_ID = 10L;
    private static final Long EXPERT_ID = 20L;
    private static final Long ADMIN_ID = 1L;
    private static final String POST_ID = "66a3f1c2e8b4a51d3c7f9012";

    @Mock
    private ForumPostRepository repository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private MongoTemplate mongoTemplate;

    private ForumService forumService;

    @BeforeEach
    void setUp() {
        forumService = new ForumService(repository, eventPublisher, mongoTemplate);
    }

    private static AuthenticatedUser user(Long id, String name, RoleName role) {
        return new AuthenticatedUser(id, name, name.toLowerCase() + "@example.com", List.of(role));
    }

    private static ForumPost post(Long authorId) {
        ForumPost post = new ForumPost(
                "Yellowing leaves on soybean", "Lower leaves are turning yellow.",
                "Crop Health", authorId, "Ravi Patil", "Farmer");
        post.setId(POST_ID);
        return post;
    }

    @Nested
    @DisplayName("createPost")
    class CreatePost {

        private final CreatePostRequest request = new CreatePostRequest(
                "Yellowing leaves on soybean at flowering",
                "My soybean crop in Latur has started yellowing from the lower leaves.",
                "Crop Health");

        @Test
        @DisplayName("takes author identity from the token, not the request")
        void assignsAuthorFromToken() {
            when(repository.save(any(ForumPost.class))).thenAnswer(invocation -> {
                ForumPost saved = invocation.getArgument(0);
                saved.setId(POST_ID);
                return saved;
            });

            PostResponse response = forumService.createPost(
                    request, user(FARMER_ID, "Ravi Patil", RoleName.FARMER));

            assertThat(response.authorName()).isEqualTo("Ravi Patil");
            assertThat(response.authorRole()).isEqualTo("Farmer");

            ArgumentCaptor<ForumPost> saved = ArgumentCaptor.forClass(ForumPost.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getAuthorId()).isEqualTo(FARMER_ID);
        }

        @Test
        @DisplayName("starts a new thread with no replies and unresolved")
        void startsEmptyAndUnresolved() {
            when(repository.save(any(ForumPost.class))).thenAnswer(invocation -> {
                ForumPost saved = invocation.getArgument(0);
                saved.setId(POST_ID);
                return saved;
            });

            PostResponse response = forumService.createPost(
                    request, user(FARMER_ID, "Ravi Patil", RoleName.FARMER));

            assertThat(response.commentCount()).isZero();
            assertThat(response.comments()).isEmpty();
            assertThat(response.resolved()).isFalse();
        }

        @Test
        @DisplayName("publishes ForumPostCreatedEvent")
        void publishesEvent() {
            when(repository.save(any(ForumPost.class))).thenAnswer(invocation -> {
                ForumPost saved = invocation.getArgument(0);
                saved.setId(POST_ID);
                return saved;
            });

            forumService.createPost(request, user(FARMER_ID, "Ravi Patil", RoleName.FARMER));

            ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
            verify(eventPublisher).publish(
                    eq(RabbitTopology.ROUTING_FORUM_POST_CREATED), captor.capture());
            ForumPostCreatedEvent event = (ForumPostCreatedEvent) captor.getValue();
            assertThat(event.getPostId()).isEqualTo(POST_ID);
            assertThat(event.getCategory()).isEqualTo("Crop Health");
            assertThat(event.getAuthorId()).isEqualTo(FARMER_ID);
        }
    }

    @Nested
    @DisplayName("addComment")
    class AddComment {

        private final CreateCommentRequest request = new CreateCommentRequest(
                "This looks like a nitrogen deficiency. Try a 2% urea foliar spray.");

        @Test
        @DisplayName("appends the reply and increments the stored comment count")
        void appendsReply() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            CommentResponse response = forumService.addComment(
                    POST_ID, request, user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT));

            assertThat(response.authorRole()).isEqualTo("Agriculture Expert");
            // The denormalised count must move in step with the array, or the feed lies.
            assertThat(existing.getCommentCount()).isEqualTo(1);
            assertThat(existing.getComments()).hasSize(1);
        }

        @Test
        @DisplayName("publishes an event carrying the thread author's id for notification routing")
        void publishesEventWithPostAuthor() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            forumService.addComment(POST_ID, request,
                    user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT));

            ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
            verify(eventPublisher).publish(
                    eq(RabbitTopology.ROUTING_FORUM_COMMENT_ADDED), captor.capture());
            ForumCommentAddedEvent event = (ForumCommentAddedEvent) captor.getValue();
            // Without this, notification-service would have to call back into forum-service.
            assertThat(event.getPostAuthorId()).isEqualTo(FARMER_ID);
            assertThat(event.getCommenterId()).isEqualTo(EXPERT_ID);
        }

        @Test
        @DisplayName("returns 404 when the thread does not exist")
        void failsForMissingThread() {
            when(repository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> forumService.addComment("missing", request,
                    user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(eventPublisher, never()).publish(anyString(), any());
        }

        @Test
        @DisplayName("keeps replies in chronological order regardless of insertion order")
        void ordersRepliesChronologically() {
            ForumPost existing = post(FARMER_ID);
            ForumComment later = new ForumComment("Second", EXPERT_ID, "Expert", "Agriculture Expert");
            later.setCreatedAt(Instant.parse("2026-07-02T10:00:00Z"));
            ForumComment earlier = new ForumComment("First", EXPERT_ID, "Expert", "Agriculture Expert");
            earlier.setCreatedAt(Instant.parse("2026-07-01T10:00:00Z"));
            existing.addComment(later);
            existing.addComment(earlier);

            List<ForumComment> ordered = existing.getCommentsInOrder();

            assertThat(ordered).extracting(ForumComment::getContent)
                    .containsExactly("First", "Second");
        }
    }

    @Nested
    @DisplayName("deletePost")
    class DeletePost {

        @Test
        @DisplayName("allows the author to delete their own thread")
        void authorCanDelete() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            forumService.deletePost(POST_ID, user(FARMER_ID, "Ravi Patil", RoleName.FARMER));

            verify(repository).delete(existing);
        }

        @Test
        @DisplayName("allows an administrator to moderate any thread")
        void adminCanDelete() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            forumService.deletePost(POST_ID, user(ADMIN_ID, "Admin", RoleName.ADMIN));

            verify(repository).delete(existing);
        }

        @Test
        @DisplayName("refuses deletion by an unrelated user")
        void strangerCannotDelete() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> forumService.deletePost(
                    POST_ID, user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("your own posts");

            verify(repository, never()).delete(any(ForumPost.class));
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteComment {

        @Test
        @DisplayName("allows the reply's author to remove it and decrements the count")
        void commentAuthorCanDelete() {
            ForumPost existing = post(FARMER_ID);
            ForumComment comment = new ForumComment(
                    "My reply", EXPERT_ID, "Dr Kulkarni", "Agriculture Expert");
            existing.addComment(comment);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            forumService.deleteComment(POST_ID, comment.getId(),
                    user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT));

            assertThat(existing.getComments()).isEmpty();
            assertThat(existing.getCommentCount()).isZero();
        }

        @Test
        @DisplayName("refuses to let the thread author delete someone else's reply")
        void threadAuthorCannotDeleteOthersReply() {
            // Owning the thread must not confer moderation rights over other people's answers.
            ForumPost existing = post(FARMER_ID);
            ForumComment comment = new ForumComment(
                    "Expert reply", EXPERT_ID, "Dr Kulkarni", "Agriculture Expert");
            existing.addComment(comment);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> forumService.deleteComment(POST_ID, comment.getId(),
                    user(FARMER_ID, "Ravi Patil", RoleName.FARMER)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("returns 404 for a reply id that is not on the thread")
        void failsForUnknownComment() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> forumService.deleteComment(POST_ID, "no-such-id",
                    user(ADMIN_ID, "Admin", RoleName.ADMIN)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("setResolved")
    class SetResolved {

        @Test
        @DisplayName("allows the thread author to mark their question resolved")
        void authorCanResolve() {
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            PostResponse response = forumService.setResolved(
                    POST_ID, true, user(FARMER_ID, "Ravi Patil", RoleName.FARMER));

            assertThat(response.resolved()).isTrue();
        }

        @Test
        @DisplayName("refuses to let an answering expert resolve someone else's question")
        void expertCannotResolveOthersQuestion() {
            // Whether an answer actually solved the problem is the asker's call, not the answerer's.
            ForumPost existing = post(FARMER_ID);
            when(repository.findById(POST_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> forumService.setResolved(POST_ID, true,
                    user(EXPERT_ID, "Dr Kulkarni", RoleName.AGRICULTURE_EXPERT)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("anonymisation")
    class Anonymisation {

        @Test
        @DisplayName("keeps thread content readable after the author's account is deleted")
        void preservesContentOnAnonymise() {
            ForumPost existing = post(FARMER_ID);
            String originalContent = existing.getContent();

            existing.anonymise();

            // Deleting the text would orphan every expert answer on the thread.
            assertThat(existing.getContent()).isEqualTo(originalContent);
            assertThat(existing.getAuthorName()).isEqualTo("Deleted user");
            assertThat(existing.getAuthorId()).isNull();
            assertThat(existing.isAnonymised()).isTrue();
        }

        @Test
        @DisplayName("anonymising a reply keeps its text but drops the identity")
        void anonymisesReply() {
            ForumComment comment = new ForumComment(
                    "Try a 2% urea spray", EXPERT_ID, "Dr Kulkarni", "Agriculture Expert");

            comment.anonymise();

            assertThat(comment.getContent()).isEqualTo("Try a 2% urea spray");
            assertThat(comment.getAuthorName()).isEqualTo("Deleted user");
            assertThat(comment.getAuthorId()).isNull();
            // The role survives: "an Agriculture Expert said this" is still useful provenance.
            assertThat(comment.getAuthorRole()).isEqualTo("Agriculture Expert");
        }
    }
}
