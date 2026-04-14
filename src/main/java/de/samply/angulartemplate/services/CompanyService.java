package de.samply.angulartemplate.services;

import de.samply.angulartemplate.model.Company;
import de.samply.angulartemplate.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Company createCompany(Company dto) {
        Company company = toEntity(dto);
        return toDto(companyRepository.save(company));
    }

    public Company updateCompany(Long id, Company dto) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found: " + id));
        company.setName(dto.getName());
        company.setStreet(dto.getStreet());
        company.setCity(dto.getCity());
        company.setPosition(dto.getPosition());
        company.setContact(dto.getContact());
        company.setWebsite(dto.getWebsite());
        company.setNotes(dto.getNotes());
        return toDto(companyRepository.save(company));
    }

    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }

    // --- Mapping helpers ---
    private Company toDto(Company c) {
        Company dto = new Company();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setStreet(c.getStreet());
        dto.setCity(c.getCity());
        dto.setPosition(c.getPosition());
        dto.setContact(c.getContact());
        dto.setWebsite(c.getWebsite());
        dto.setNotes(c.getNotes());
        return dto;
    }

    private Company toEntity(Company dto) {
        Company c = new Company();
        c.setName(dto.getName());
        c.setStreet(dto.getStreet());
        c.setCity(dto.getCity());
        c.setPosition(dto.getPosition());
        c.setContact(dto.getContact());
        c.setWebsite(dto.getWebsite());
        c.setNotes(dto.getNotes());
        return c;
    }
}