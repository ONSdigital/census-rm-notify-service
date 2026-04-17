package uk.gov.ons.census.notifysvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.common.model.entity.EmailTemplate;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {}
