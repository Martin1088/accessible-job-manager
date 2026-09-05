package de.samply.manager.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row of the review queue: enough to decide on a position without opening
 * it, and nothing else. The company and its city come from the parent company
 * row, which is why this is not a {@link CompanyPositionDto} with two extra
 * fields.
 */
@Data
public class QueuedPositionDto {
    private Long id;
    private String title;
    private Long companyId;
    private String companyName;
    private String city;
    private LocalDateTime createdAt;
}
