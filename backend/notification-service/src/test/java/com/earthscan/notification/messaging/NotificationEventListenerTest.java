package com.earthscan.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.earthscan.common.event.ForumCommentAddedEvent;
import com.earthscan.common.event.ForumPostCreatedEvent;
import com.earthscan.common.event.LandListedEvent;
import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.event.UserRegisteredEvent;
import com.earthscan.common.event.UserRoleChangedEvent;
import com.earthscan.notification.domain.Notification;
import com.earthscan.notification.domain.NotificationType;
import com.earthscan.notification.factory.ListingPublishedNotificationFactory;
import com.earthscan.notification.factory.NotificationFactory;
import com.earthscan.notification.factory.NotificationFactoryRegistry;
import com.earthscan.notification.factory.PostCreatedNotificationFactory;
import com.earthscan.notification.factory.QuestionAnsweredNotificationFactory;
import com.earthscan.notification.factory.RoleChangedNotificationFactory;
import com.earthscan.notification.factory.WelcomeNotificationFactory;
import com.earthscan.notification.repository.NotificationRepository;
import com.earthscan.notification.service.NotificationService;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Tests for the event listeners and the idempotency guarantee they depend on.
 *
 * <p>The real {@link NotificationService} is used with a mocked repository, because the behaviour
 * under test — "a redelivered event must not produce a second notification" — lives in the service,
 * and mocking it away would leave nothing meaningful to assert.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Notification event listeners")
class NotificationEventListenerTest {

    private static final Long FARMER_ID = 10L;
    private static final Long EXPERT_ID = 20L;

    @Mock
    private NotificationRepository repository;

    private NotificationService notificationService;
    private UserEventListener userEventListener;
    private LandEventListener landEventListener;
    private ForumEventListener forumEventListener;

    @BeforeEach
    void setUp() {
        // Real factories with a mocked repository. The factories are pure functions, so mocking
        // them would leave nothing to assert about the message content they exist to produce.
        List<NotificationFactory<?>> factories = List.of(
                new WelcomeNotificationFactory(),
                new RoleChangedNotificationFactory(),
                new ListingPublishedNotificationFactory(),
                new QuestionAnsweredNotificationFactory(),
                new PostCreatedNotificationFactory());
        notificationService =
                new NotificationService(repository, new NotificationFactoryRegistry(factories));
        userEventListener = new UserEventListener(notificationService);
        landEventListener = new LandEventListener(notificationService);
        forumEventListener = new ForumEventListener(notificationService);
    }

    private void allowSave() {
        when(repository.existsBySourceEventIdAndUserId(anyString(), anyLong())).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId("generated-id");
            return saved;
        });
    }

    @Nested
    @DisplayName("user events")
    class UserEvents {

        @Test
        @DisplayName("creates a role-specific welcome notification on registration")
        void createsWelcomeNotification() {
            allowSave();
            UserRegisteredEvent event = new UserRegisteredEvent(
                    FARMER_ID, "Ravi Patil", "ravi@example.com", "Farmer");

            userEventListener.onUserRegistered(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(FARMER_ID);
            assertThat(saved.getType()).isEqualTo(NotificationType.WELCOME);
            assertThat(saved.getTitle()).contains("Ravi Patil");
            assertThat(saved.getMessage()).contains("water and crop advisory");
            assertThat(saved.getSourceEventId()).isEqualTo(event.getEventId());
        }

        @Test
        @DisplayName("tailors the welcome message to a Land Buyer")
        void tailorsWelcomeToLandBuyer() {
            allowSave();

            userEventListener.onUserRegistered(new UserRegisteredEvent(
                    FARMER_ID, "Anita", "anita@example.com", "Land Buyer"));

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getMessage()).contains("verified listings");
        }

        @Test
        @DisplayName("tells the user to re-authenticate after a role change")
        void createsRoleChangeNotification() {
            allowSave();

            userEventListener.onUserRoleChanged(new UserRoleChangedEvent(
                    FARMER_ID, "Ravi", "ravi@example.com", "Farmer", "Agriculture Expert"));

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getType()).isEqualTo(NotificationType.ROLE_CHANGED);
            // The old JWT still carries the old role until it is reissued, so this instruction is
            // load-bearing rather than decorative.
            assertThat(saved.getMessage()).contains("Sign out and back in");
        }

        @Test
        @DisplayName("purges every notification when an account is deleted")
        void purgesOnDelete() {
            when(repository.deleteByUserId(FARMER_ID)).thenReturn(7L);

            userEventListener.onUserDeleted(
                    new UserDeletedEvent(FARMER_ID, "Ravi", "ravi@example.com"));

            verify(repository).deleteByUserId(FARMER_ID);
        }

        @Test
        @DisplayName("discards a malformed delete event rather than looping it through the DLQ")
        void discardsMalformedDeleteEvent() {
            userEventListener.onUserDeleted(new UserDeletedEvent(null, "?", "?"));

            verify(repository, never()).deleteByUserId(any());
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("ignores a redelivered event instead of duplicating the notification")
        void ignoresRedelivery() {
            // At-least-once delivery makes this the normal case after a consumer restart, not an edge.
            when(repository.existsBySourceEventIdAndUserId(anyString(), eq(FARMER_ID)))
                    .thenReturn(true);

            userEventListener.onUserRegistered(new UserRegisteredEvent(
                    FARMER_ID, "Ravi Patil", "ravi@example.com", "Farmer"));

            verify(repository, never()).save(any(Notification.class));
        }

        @Test
        @DisplayName("survives losing a race to a concurrent delivery of the same event")
        void survivesConcurrentDuplicate() {
            // Two consumer threads can both pass the exists() check before either writes; the unique
            // index is what actually enforces uniqueness, and the resulting error must not propagate.
            when(repository.existsBySourceEventIdAndUserId(anyString(), anyLong())).thenReturn(false);
            when(repository.save(any(Notification.class)))
                    .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

            Notification result = notificationService.create(
                    FARMER_ID, NotificationType.WELCOME, "Welcome", "Body", "/", "event-1");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("skips a notification with no target user")
        void skipsNullUser() {
            Notification result = notificationService.create(
                    null, NotificationType.WELCOME, "Welcome", "Body", "/", "event-1");

            assertThat(result).isNull();
            verify(repository, never()).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("land events")
    class LandEvents {

        private LandListedEvent listedEvent(double score) {
            LandListedEvent event = new LandListedEvent();
            event.setLandId(42L);
            event.setTitle("Fertile black cotton farmland");
            event.setLocation("Baramati, Pune");
            event.setOwnerId(FARMER_ID);
            event.setPrice(new BigDecimal("6500000"));
            event.setSizeInAcres(5.0);
            event.setLandIntelligenceScore(score);
            event.setBorewellSuccessProbability(82.0);
            return event;
        }

        @Test
        @DisplayName("confirms publication to the listing owner with both scores")
        void notifiesOwner() {
            allowSave();

            landEventListener.onLandListed(listedEvent(81.5));

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(FARMER_ID);
            assertThat(saved.getType()).isEqualTo(NotificationType.LISTING_PUBLISHED);
            assertThat(saved.getMessage()).contains("81.5/100").contains("82%");
            assertThat(saved.getActionPath()).isEqualTo("/lands/42");
        }

        @Test
        @DisplayName("handles a listing with no score without failing the message")
        void handlesMissingScore() {
            allowSave();
            LandListedEvent event = listedEvent(0);
            event.setLandIntelligenceScore(null);
            event.setBorewellSuccessProbability(null);

            landEventListener.onLandListed(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getMessage()).contains("not available");
        }
    }

    @Nested
    @DisplayName("forum events")
    class ForumEvents {

        private ForumCommentAddedEvent commentEvent(Long postAuthorId, Long commenterId) {
            ForumCommentAddedEvent event = new ForumCommentAddedEvent();
            event.setPostId("66a3f1c2e8b4a51d3c7f9012");
            event.setPostTitle("Yellowing leaves on soybean");
            event.setCommentId("comment-1");
            event.setPostAuthorId(postAuthorId);
            event.setCommenterId(commenterId);
            event.setCommenterName("Dr Kulkarni");
            event.setCommenterRole("Agriculture Expert");
            return event;
        }

        @Test
        @DisplayName("tells the thread author their question was answered")
        void notifiesThreadAuthor() {
            allowSave();

            forumEventListener.onCommentAdded(commentEvent(FARMER_ID, EXPERT_ID));

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(FARMER_ID);
            assertThat(saved.getType()).isEqualTo(NotificationType.QUESTION_ANSWERED);
            assertThat(saved.getMessage())
                    .contains("Dr Kulkarni")
                    .contains("Agriculture Expert")
                    .contains("Yellowing leaves on soybean");
            assertThat(saved.getActionPath()).isEqualTo("/forum/66a3f1c2e8b4a51d3c7f9012");
        }

        @Test
        @DisplayName("does not notify someone about their own reply to their own thread")
        void skipsSelfReply() {
            forumEventListener.onCommentAdded(commentEvent(FARMER_ID, FARMER_ID));

            verify(repository, never()).save(any(Notification.class));
        }

        @Test
        @DisplayName("acknowledges a newly opened thread back to its author")
        void acknowledgesNewPost() {
            allowSave();
            ForumPostCreatedEvent event = new ForumPostCreatedEvent();
            event.setPostId("66a3f1c2e8b4a51d3c7f9012");
            event.setTitle("Yellowing leaves on soybean");
            event.setCategory("Crop Health");
            event.setAuthorId(FARMER_ID);
            event.setAuthorName("Ravi Patil");
            event.setAuthorRole("Farmer");

            forumEventListener.onPostCreated(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getType())
                    .isEqualTo(NotificationType.NEW_QUESTION_IN_CATEGORY);
            assertThat(captor.getValue().getMessage()).contains("Crop Health");
        }
    }

    @Nested
    @DisplayName("markAllRead")
    class MarkAllRead {

        @Test
        @DisplayName("marks every unread notification and reports the count")
        void marksAll() {
            Notification first = new Notification(
                    FARMER_ID, NotificationType.WELCOME, "A", "B", "/", "e1");
            Notification second = new Notification(
                    FARMER_ID, NotificationType.QUESTION_ANSWERED, "C", "D", "/", "e2");
            when(repository.findByUserIdAndReadFalse(FARMER_ID))
                    .thenReturn(java.util.List.of(first, second));

            int updated = notificationService.markAllRead(FARMER_ID);

            assertThat(updated).isEqualTo(2);
            assertThat(first.isRead()).isTrue();
            assertThat(first.getReadAt()).isNotNull();
            assertThat(second.isRead()).isTrue();
            verify(repository, times(1)).saveAll(any());
        }
    }
}
