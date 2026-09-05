package de.samply.manager.services;

import de.samply.manager.dto.QueuedPositionDto;
import de.samply.manager.dto.TriageResultDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.CompanyPositionRepository;
import de.samply.manager.types.TriageState;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * The review queue: positions that have been found but not yet looked at.
 *
 * <p>The queue exists because finding a position and deciding to apply for one
 * are different acts. Without it the catalogue fills up with everything that
 * was ever imported, and the entries that were meant seriously are no longer
 * distinguishable from the ones that were worth a glance.
 *
 * <p>There is no state machine here on purpose. Accepting and dismissing are
 * two writes of one column, both idempotent, and neither has a transition to
 * forbid: accepting an already accepted position is what a second click on a
 * stale page does, and answering it with an error would be answering a
 * question nobody asked.
 */
@Service
public class PositionTriageService {

    private final CompanyPositionRepository positionRepository;
    private final MessageSource messageSource;

    public PositionTriageService(CompanyPositionRepository positionRepository, MessageSource messageSource) {
        this.positionRepository = positionRepository;
        this.messageSource = messageSource;
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    public List<QueuedPositionDto> queue(String userId) {
        return positionRepository
                .findByCompanyUserIdAndTriageStateOrderByCreatedAtAsc(userId, TriageState.NEW)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TriageResultDto accept(Long positionId, String userId) {
        return set(positionId, userId, TriageState.ACCEPTED);
    }

    @Transactional
    public TriageResultDto dismiss(Long positionId, String userId) {
        return set(positionId, userId, TriageState.DISMISSED);
    }

    private TriageResultDto set(Long positionId, String userId, TriageState state) {
        CompanyPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.position.notFound", positionId)));

        if (!position.getCompany().getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }

        position.setTriageState(state);
        positionRepository.save(position);

        return new TriageResultDto(positionRepository.countByCompanyUserIdAndTriageState(userId, TriageState.NEW));
    }

    private QueuedPositionDto toDto(CompanyPosition position) {
        QueuedPositionDto dto = new QueuedPositionDto();
        dto.setId(position.getId());
        dto.setTitle(position.getTitle());
        dto.setCompanyId(position.getCompany().getId());
        dto.setCompanyName(position.getCompany().getName());
        dto.setCity(position.getCompany().getLocations().stream()
                .map(CompanyLocation::getCity)
                .filter(city -> city != null && !city.isBlank())
                .findFirst()
                .orElse(null));
        dto.setCreatedAt(position.getCreatedAt());
        return dto;
    }
}
