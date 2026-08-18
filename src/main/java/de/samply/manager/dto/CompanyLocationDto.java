package de.samply.manager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyLocationDto {
    private Long id;
    @NotBlank(message = "{error.company.location.street.blank}")
    private String street;
    @NotBlank(message = "{error.company.location.city.blank}")
    private String city;
}
