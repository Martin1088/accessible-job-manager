package de.samply.manager.repository;

import de.samply.manager.types.Role;
import de.samply.manager.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);
    List<UserProfile> findAllByRole(Role role);
    List<UserProfile> findByAdvisors_UserId(String advisorId);
}
