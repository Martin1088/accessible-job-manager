package de.samply.manager.services;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyLocationDto;
import de.samply.manager.dto.CompanyPositionDto;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;

    public CompanyService(CompanyRepository companyRepository, ApplicationRepository applicationRepository) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
    }

    public List<CompanyDto> getAllCompanies(String userId) {
        return companyRepository.findByUserId(userId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CompanyDto createCompany(CompanyDto dto, String userId) {
        Company company = toEntity(dto);
        company.setUserId(userId);
        return toDto(companyRepository.save(company));
    }

    @Transactional
    public CompanyDto updateCompany(Long id, CompanyDto dto, String userId) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFound("Company not found: " + id));
        if (!company.getUserId().equals(userId))
            throw new ApiException.Forbidden();
        company.setName(dto.getName());

        company.getLocations().clear();
        if (dto.getLocations() != null) {
            dto.getLocations().stream()
                    .map(l -> toLocationEntity(l, company))
                    .forEach(company.getLocations()::add);
        }

        Map<Long, CompanyPosition> existingById = company.getPositions().stream()
                .collect(Collectors.toMap(CompanyPosition::getId, p -> p));
        Set<Long> incomingIds = dto.getPositions() == null ? Set.of() :
                dto.getPositions().stream()
                        .map(CompanyPositionDto::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        for (Long existingId : existingById.keySet()) {
            if (!incomingIds.contains(existingId) && applicationRepository.existsByCompanyPositionId(existingId)) {
                throw new ApiException.Conflict("Cannot remove a position that has associated applications");
            }
        }

        company.getPositions().removeIf(p -> !incomingIds.contains(p.getId()));

        if (dto.getPositions() != null) {
            for (CompanyPositionDto pdto : dto.getPositions()) {
                if (pdto.getId() != null && existingById.containsKey(pdto.getId())) {
                    applyPositionFields(pdto, existingById.get(pdto.getId()));
                } else {
                    company.getPositions().add(toPositionEntity(pdto, company));
                }
            }
        }

        return toDto(companyRepository.save(company));
    }

    public void deleteCompany(Long id, String userId) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFound("Company not found: " + id));
        if (!company.getUserId().equals(userId))
            throw new ApiException.Forbidden();
        companyRepository.deleteById(id);
    }

    private CompanyDto toDto(Company c) {
        CompanyDto dto = new CompanyDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setLocations(c.getLocations().stream()
                .map(this::toLocationDto)
                .collect(Collectors.toList()));
        dto.setPositions(c.getPositions().stream()
                .map(this::toPositionDto)
                .collect(Collectors.toList()));
        return dto;
    }

    private CompanyLocationDto toLocationDto(CompanyLocation l) {
        CompanyLocationDto dto = new CompanyLocationDto();
        dto.setId(l.getId());
        dto.setStreet(l.getStreet());
        dto.setCity(l.getCity());
        return dto;
    }

    private CompanyPositionDto toPositionDto(CompanyPosition p) {
        CompanyPositionDto dto = new CompanyPositionDto();
        dto.setId(p.getId());
        dto.setTitle(p.getTitle());
        dto.setContactGender(p.getContactGender());
        dto.setContactTitle(p.getContactTitle());
        dto.setContactLastName(p.getContactLastName());
        dto.setEmail(p.getEmail());
        dto.setWebsite(p.getWebsite());
        dto.setNotes(p.getNotes());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }

    private Company toEntity(CompanyDto dto) {
        Company c = new Company();
        c.setName(dto.getName());
        if (dto.getLocations() != null) {
            dto.getLocations().stream()
                    .map(l -> toLocationEntity(l, c))
                    .forEach(c.getLocations()::add);
        }
        if (dto.getPositions() != null) {
            dto.getPositions().stream()
                    .map(p -> toPositionEntity(p, c))
                    .forEach(c.getPositions()::add);
        }
        return c;
    }

    private CompanyLocation toLocationEntity(CompanyLocationDto dto, Company company) {
        CompanyLocation l = new CompanyLocation();
        l.setStreet(dto.getStreet());
        l.setCity(dto.getCity());
        l.setCompany(company);
        return l;
    }

    private void applyPositionFields(CompanyPositionDto dto, CompanyPosition p) {
        p.setTitle(dto.getTitle());
        p.setContactGender(dto.getContactGender());
        p.setContactTitle(dto.getContactTitle());
        p.setContactLastName(dto.getContactLastName());
        p.setEmail(dto.getEmail());
        p.setWebsite(dto.getWebsite());
        p.setNotes(dto.getNotes());
    }

    private CompanyPosition toPositionEntity(CompanyPositionDto dto, Company company) {
        CompanyPosition p = new CompanyPosition();
        applyPositionFields(dto, p);
        p.setCompany(company);
        return p;
    }
}