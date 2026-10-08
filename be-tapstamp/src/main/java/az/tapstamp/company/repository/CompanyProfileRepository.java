package az.tapstamp.company.repository;

import java.util.Optional;

import az.tapstamp.company.domain.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Boolean> {

    /** Tenant bazasında yeganə profil sətri. */
    default Optional<CompanyProfile> findProfile() {
        return findById(Boolean.TRUE);
    }
}
