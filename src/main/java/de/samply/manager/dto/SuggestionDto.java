package de.samply.manager.dto;

import de.samply.manager.model.SuggestionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SuggestionDto {
    private Long id;
    private String targetUserId;
    private String targetUserName;
    private String companyName;
    private String positionTitle;
    private Long companyPositionId;
    private String message;
    private SuggestionStatus status;
    private LocalDateTime createdAt;
}
