package com.earthscan.land.service;

import com.earthscan.common.exception.BadRequestException;
import com.earthscan.common.exception.DuplicateResourceException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.land.domain.SavedSearch;
import com.earthscan.land.dto.SavedSearchRequest;
import com.earthscan.land.dto.SavedSearchResponse;
import com.earthscan.land.repository.SavedSearchRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's saved searches and shortlisted listings.
 *
 * <p>Every method is scoped by {@code userId} taken from the JWT. Reads use
 * {@code findByIdAndUserId} rather than {@code findById} followed by an ownership check: the
 * two-step version returns 403 for a row that exists but belongs to someone else, which confirms
 * that row's existence to an attacker enumerating ids. Scoping the query itself returns a plain 404
 * and leaks nothing.</p>
 */
@Service
public class SavedSearchService {

    private static final Logger log = LoggerFactory.getLogger(SavedSearchService.class);

    /** Guards against a scripted client filling the table on one account. */
    private static final int MAX_SAVED_PER_USER = 100;

    private final SavedSearchRepository repository;

    public SavedSearchService(SavedSearchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> findForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SavedSearchResponse::from)
                .toList();
    }

    @Transactional
    public SavedSearchResponse create(SavedSearchRequest request, Long userId) {
        if (request.minPrice() != null && request.maxPrice() != null
                && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }

        List<SavedSearch> existing = repository.findByUserIdOrderByCreatedAtDesc(userId);
        if (existing.size() >= MAX_SAVED_PER_USER) {
            throw new BadRequestException(
                    "You have reached the maximum of " + MAX_SAVED_PER_USER + " saved searches");
        }

        if (request.landId() != null && repository.existsByUserIdAndLandId(userId, request.landId())) {
            throw new DuplicateResourceException("This listing is already in your shortlist");
        }

        SavedSearch entity = new SavedSearch(userId, request.label().trim());
        entity.setLocationQuery(request.locationQuery());
        entity.setSoilTypeName(request.soilTypeName());
        entity.setMinPrice(request.minPrice());
        entity.setMaxPrice(request.maxPrice());
        entity.setMinScore(request.minScore());
        entity.setLandId(request.landId());
        entity.setNotifyOnMatch(request.notifyOnMatch() == null || request.notifyOnMatch());

        SavedSearch saved = repository.save(entity);
        log.info("User id={} saved search id={}", userId, saved.getId());
        return SavedSearchResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        SavedSearch entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Saved search", id));
        repository.delete(entity);
        log.info("User id={} deleted saved search id={}", userId, id);
    }
}
