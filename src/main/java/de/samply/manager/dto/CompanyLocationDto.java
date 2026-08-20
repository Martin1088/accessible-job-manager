package de.samply.manager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyLocationDto {
    private Long id;
    private String street;
    @NotBlank(message = "{error.company.location.city.blank}")
    private String city;
    private String postcode;
    private String country;
}
