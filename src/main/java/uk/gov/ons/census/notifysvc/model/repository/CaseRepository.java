package uk.gov.ons.census.notifysvc.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.common.model.entity.Case;

public interface CaseRepository extends JpaRepository<Case, UUID> {}
