package de.samply.manager.repository;

import de.samply.manager.model.UserProfile;
import de.samply.manager.security.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);
    List<UserProfile> findByRolesContaining(AppRole role);
}
