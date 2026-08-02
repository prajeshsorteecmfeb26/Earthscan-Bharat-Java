package com.earthscan.forum.messaging;

import com.earthscan.common.event.UserDeletedEvent;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.forum.domain.ForumComment;
import com.earthscan.forum.domain.ForumPost;
import com.earthscan.forum.repository.ForumPostRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Anonymises a deleted user's forum contributions.
 *
 * <p>Deliberately anonymises rather than deletes. Land listings belong to their owner and go with
 * them, but a forum thread is shared context: hard-deleting a question would leave three expert
 * answers hanging with nothing to answer, and deleting one person's replies would gut threads other
 * users are still relying on. Replacing the name while keeping the text preserves the knowledge and
 * still honours the account deletion.</p>
 *
 * <p>Idempotent: a redelivered event finds the documents already anonymised and the second pass is a
 * no-op with the same result.</p>
 */
@Component
public class ForumUserEventListener {

    private static final Logger log = LoggerFactory.getLogger(ForumUserEventListener.class);

    private final ForumPostRepository repository;

    public ForumUserEventListener(ForumPostRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_FORUM_USER_EVENTS)
    public void onUserDeleted(UserDeletedEvent event) {
        Long userId = event.getUserId();
        if (userId == null) {
            log.error("Discarding UserDeletedEvent {} with no userId", event.getEventId());
            return;
        }

        // A post may need updating because the user authored it, replied to it, or both — collect
        // into a set so a document touched on both counts is still saved exactly once.
        Set<ForumPost> affected = new LinkedHashSet<>();

        List<ForumPost> authored = repository.findByAuthorId(userId);
        authored.forEach(post -> {
            post.anonymise();
            affected.add(post);
        });

        List<ForumPost> commentedOn = repository.findByCommentAuthorId(userId);
        for (ForumPost post : commentedOn) {
            post.getComments().stream()
                    .filter(comment -> userId.equals(comment.getAuthorId()))
                    .forEach(ForumComment::anonymise);
            affected.add(post);
        }

        if (!affected.isEmpty()) {
            repository.saveAll(affected);
        }

        log.info("Handled user.deleted for user id={} [event {}]: anonymised {} authored post(s) "
                        + "and replies across {} thread(s)",
                userId, event.getEventId(), authored.size(), commentedOn.size());
    }
}
