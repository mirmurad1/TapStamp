package az.cafeloyalty.company.repository;

import java.util.List;
import java.util.UUID;

import az.cafeloyalty.company.domain.Branch;
import az.cafeloyalty.company.domain.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByStatusOrderByCreatedAtAsc(BranchStatus status);
}
