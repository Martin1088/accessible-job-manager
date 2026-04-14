package de.samply.angulartemplate.controller;

import de.samply.angulartemplate.dto.CompanyDto;
import de.samply.angulartemplate.services.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public List<CompanyDto> getAll() {
        return companyService.getAllCompanies();
    }

    @PostMapping
    public ResponseEntity<CompanyDto> create(@RequestBody CompanyDto dto) {
        return ResponseEntity.ok(companyService.createCompany(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyDto> update(@PathVariable Long id, @RequestBody CompanyDto dto) {
        return ResponseEntity.ok(companyService.updateCompany(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}