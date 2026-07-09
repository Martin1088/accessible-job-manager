package de.samply.manager.controller;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.services.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class JobController {

    private final CompanyService companyService;

    @GetMapping
    public List<CompanyDto> getAll(@AuthenticationPrincipal OidcUser user) {
        return companyService.getAllCompanies(user.getSubject());
    }

    @PostMapping
    public ResponseEntity<CompanyDto> create(@RequestBody CompanyDto dto,
                                             @AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok(companyService.createCompany(dto, user.getSubject()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyDto> update(@PathVariable Long id,
                                             @RequestBody CompanyDto dto,
                                             @AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok(companyService.updateCompany(id, dto, user.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal OidcUser user) {
        companyService.deleteCompany(id, user.getSubject());
        return ResponseEntity.noContent().build();
    }
}
