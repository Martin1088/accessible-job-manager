package de.samply.manager.dto;

import lombok.Data;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Data
public class CompanyDto {
    private Long id;
    @NotBlank(message = "{error.company.name.blank}")
    private String name;
    private List<@Valid CompanyLocationDto> locations;
    private List<@Valid CompanyPositionDto> positions;
}
