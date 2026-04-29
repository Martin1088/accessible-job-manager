package de.samply.manager.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompanyDto {
    private Long id;
    private String name;
    private List<CompanyLocationDto> locations;
    private List<CompanyPositionDto> positions;
}
