package com.earthscan.forum.repository;

import com.earthscan.forum.domain.ForumPost;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link ForumPost}. */
@Repository
public interface ForumPostRepository extends MongoRepository<ForumPost, String> {

    List<ForumPost> findAllByOrderByCreatedAtDesc();

    Page<ForumPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ForumPost> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    List<ForumPost> findByAuthorId(Long authorId);

    /**
     * Threads containing at least one reply from the given author.
     *
     * <p>Written as an explicit {@code @Query} because the field is inside an embedded array; a
     * derived method name cannot express a match on an array element's property.</p>
     */
    @Query("{ 'comments.authorId': ?0 }")
    List<ForumPost> findByCommentAuthorId(Long authorId);

    /** Unanswered questions, for the expert's work queue. */
    Page<ForumPost> findByCommentCountAndResolvedFalse(int commentCount, Pageable pageable);

    long countByResolvedFalse();
}
