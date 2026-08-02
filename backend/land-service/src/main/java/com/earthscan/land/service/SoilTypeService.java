package com.earthscan.land.service;

import com.earthscan.land.dto.SoilTypeResponse;
import com.earthscan.land.repository.SoilTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to the soil reference table. */
@Service
public class SoilTypeService {

    private final SoilTypeRepository repository;

    public SoilTypeService(SoilTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SoilTypeResponse> findAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(SoilTypeResponse::from)
                .toList();
    }
}
