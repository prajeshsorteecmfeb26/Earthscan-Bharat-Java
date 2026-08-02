package com.earthscan.forum.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.earthscan.forum.domain.ForumComment;
import com.earthscan.forum.domain.ForumPost;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * MongoDB repository slice tests against an embedded server.
 *
 * <p>The assertion that most needed a real database is {@link #findsPostsByCommentAuthor()}: the
 * {@code @Query("{ 'comments.authorId': ?0 }")} that matches on a field inside an embedded array
 * cannot be verified by any mock, and it is the query the account-deletion anonymisation depends on.
 * If it silently stopped matching, a deleted user's name would remain visible on every reply they
 * ever wrote — a privacy failure with no error anywhere.</p>
 */
@DataMongoTest
@ActiveProfiles("test")
@DisplayName("ForumPostRepository (Mongo slice)")
class ForumPostRepositoryTest {

    private static final Long FARMER_ID = 10L;
    private static final Long EXPERT_ID = 20L;

    @Autowired
    private ForumPostRepository repository;

    @BeforeEach
    void clean() {
        // Unlike @DataJpaTest, @DataMongoTest does not roll back — Mongo has no ambient transaction
        // here. Explicit cleanup is what stops these tests becoming order-dependent.
        repository.deleteAll();
    }

    private ForumPost post(String title, String category, Long authorId, Instant createdAt) {
        ForumPost p = new ForumPost(title, "Body of " + title, category, authorId,
                "Author " + authorId, "Farmer");
        p.setCreatedAt(createdAt);
        return repository.save(p);
    }

    @Test
    @DisplayName("persists a thread with its replies embedded in one document")
    void embedsComments() {
        ForumPost p = new ForumPost("Yellowing soybean", "Lower leaves yellow", "Crop Health",
                FARMER_ID, "Ravi Patil", "Farmer");
        p.addComment(new ForumComment("Nitrogen deficiency", EXPERT_ID, "Dr Kulkarni",
                "Agriculture Expert"));
        p.addComment(new ForumComment("Thank you", FARMER_ID, "Ravi Patil", "Farmer"));

        ForumPost saved = repository.save(p);
        ForumPost reloaded = repository.findById(saved.getId()).orElseThrow();

        // One document, two replies inside it - no second collection and no join.
        assertThat(reloaded.getComments()).hasSize(2);
        assertThat(reloaded.getCommentCount()).isEqualTo(2);
        assertThat(reloaded.getComments().get(0).getAuthorName()).isEqualTo("Dr Kulkarni");
    }

    @Test
    @DisplayName("orders the feed newest first")
    void ordersNewestFirst() {
        post("Oldest", "Crop Health", FARMER_ID, Instant.parse("2026-07-01T10:00:00Z"));
        post("Newest", "Crop Health", FARMER_ID, Instant.parse("2026-07-20T10:00:00Z"));
        post("Middle", "Crop Health", FARMER_ID, Instant.parse("2026-07-10T10:00:00Z"));

        List<ForumPost> feed = repository.findAllByOrderByCreatedAtDesc();

        assertThat(feed).extracting(ForumPost::getTitle)
                .containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    @DisplayName("filters the feed by category and paginates")
    void filtersByCategory() {
        post("Soybean issue", "Crop Health", FARMER_ID, Instant.now());
        post("Borewell advice", "Water", FARMER_ID, Instant.now());
        post("Leaf spot", "Crop Health", FARMER_ID, Instant.now());

        Page<ForumPost> cropHealth =
                repository.findByCategoryOrderByCreatedAtDesc("Crop Health", PageRequest.of(0, 10));

        assertThat(cropHealth.getTotalElements()).isEqualTo(2);
        assertThat(cropHealth.getContent()).allMatch(p -> p.getCategory().equals("Crop Health"));
    }

    @Test
    @DisplayName("finds threads a given user authored")
    void findsPostsByAuthor() {
        post("By farmer", "Crop Health", FARMER_ID, Instant.now());
        post("By expert", "Crop Health", EXPERT_ID, Instant.now());

        assertThat(repository.findByAuthorId(FARMER_ID))
                .extracting(ForumPost::getTitle)
                .containsExactly("By farmer");
    }

    @Test
    @DisplayName("finds threads containing a reply by a given user, matching inside the array")
    void findsPostsByCommentAuthor() {
        ForumPost withExpertReply = new ForumPost("Needs an expert", "Body", "Crop Health",
                FARMER_ID, "Ravi", "Farmer");
        withExpertReply.addComment(
                new ForumComment("Try urea spray", EXPERT_ID, "Dr Kulkarni", "Agriculture Expert"));
        repository.save(withExpertReply);

        ForumPost withoutExpertReply = new ForumPost("Unanswered", "Body", "Crop Health",
                FARMER_ID, "Ravi", "Farmer");
        repository.save(withoutExpertReply);

        // This is the query the user.deleted anonymisation relies on. A mock cannot verify that a
        // match on an embedded array element actually works against a real server.
        List<ForumPost> found = repository.findByCommentAuthorId(EXPERT_ID);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getTitle()).isEqualTo("Needs an expert");
    }

    @Test
    @DisplayName("finds unanswered, unresolved threads for the expert queue")
    void findsUnanswered() {
        repository.save(new ForumPost("No replies", "Body", "Crop Health", FARMER_ID, "Ravi",
                "Farmer"));

        ForumPost answered = new ForumPost("Has a reply", "Body", "Crop Health", FARMER_ID, "Ravi",
                "Farmer");
        answered.addComment(new ForumComment("Answer", EXPERT_ID, "Expert", "Agriculture Expert"));
        repository.save(answered);

        ForumPost resolved = new ForumPost("Resolved", "Body", "Crop Health", FARMER_ID, "Ravi",
                "Farmer");
        resolved.setResolved(true);
        repository.save(resolved);

        Page<ForumPost> queue =
                repository.findByCommentCountAndResolvedFalse(0, PageRequest.of(0, 10));

        assertThat(queue.getContent()).extracting(ForumPost::getTitle)
                .containsExactly("No replies");
    }

    @Test
    @DisplayName("commentCount stays in step with the embedded array")
    void commentCountStaysConsistent() {
        ForumPost p = new ForumPost("Counting", "Body", "Crop Health", FARMER_ID, "Ravi", "Farmer");
        ForumComment first = new ForumComment("One", EXPERT_ID, "Expert", "Agriculture Expert");
        p.addComment(first);
        p.addComment(new ForumComment("Two", EXPERT_ID, "Expert", "Agriculture Expert"));
        ForumPost saved = repository.save(p);

        assertThat(saved.getCommentCount()).isEqualTo(2);

        saved.removeComment(first.getId());
        ForumPost afterRemoval = repository.save(saved);

        // The count is denormalised for feed performance, so drift between it and the array would
        // make the feed display a lie.
        assertThat(afterRemoval.getCommentCount()).isEqualTo(1);
        assertThat(afterRemoval.getComments()).hasSize(1);
    }

    @Test
    @DisplayName("anonymisation survives a persistence round trip")
    void anonymisationPersists() {
        ForumPost p = new ForumPost("To anonymise", "Body", "Crop Health", FARMER_ID, "Ravi Patil",
                "Farmer");
        p.addComment(new ForumComment("A reply", EXPERT_ID, "Dr Kulkarni", "Agriculture Expert"));
        ForumPost saved = repository.save(p);

        saved.anonymise();
        saved.getComments().forEach(ForumComment::anonymise);
        repository.save(saved);

        ForumPost reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAuthorName()).isEqualTo("Deleted user");
        assertThat(reloaded.getAuthorId()).isNull();
        assertThat(reloaded.isAnonymised()).isTrue();
        // Content preserved: deleting it would orphan the answers below.
        assertThat(reloaded.getContent()).isEqualTo("Body");
        assertThat(reloaded.getComments().get(0).getContent()).isEqualTo("A reply");
        assertThat(reloaded.getComments().get(0).getAuthorName()).isEqualTo("Deleted user");
        // Role kept: "an Agriculture Expert said this" is still useful provenance.
        assertThat(reloaded.getComments().get(0).getAuthorRole()).isEqualTo("Agriculture Expert");
    }

    @Test
    @DisplayName("counts unresolved threads")
    void countsUnresolved() {
        repository.save(new ForumPost("A", "Body", "Crop Health", FARMER_ID, "Ravi", "Farmer"));
        ForumPost resolved = new ForumPost("B", "Body", "Crop Health", FARMER_ID, "Ravi", "Farmer");
        resolved.setResolved(true);
        repository.save(resolved);

        assertThat(repository.countByResolvedFalse()).isEqualTo(1);
    }
}
