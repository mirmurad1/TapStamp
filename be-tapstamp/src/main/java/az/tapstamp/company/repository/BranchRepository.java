package az.tapstamp.company.repository;

import java.util.List;
import java.util.UUID;

import az.tapstamp.company.domain.Branch;
import az.tapstamp.company.domain.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByStatusOrderByCreatedAtAsc(BranchStatus status);
}
