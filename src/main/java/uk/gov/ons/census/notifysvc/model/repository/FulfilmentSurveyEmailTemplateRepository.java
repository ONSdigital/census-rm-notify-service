package uk.gov.ons.census.notifysvc.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.common.model.entity.EmailTemplate;
import uk.gov.ons.census.common.model.entity.FulfilmentSurveyEmailTemplate;
import uk.gov.ons.census.common.model.entity.Survey;

public interface FulfilmentSurveyEmailTemplateRepository
    extends JpaRepository<FulfilmentSurveyEmailTemplate, UUID> {

  boolean existsByEmailTemplateAndSurvey(EmailTemplate emailTemplate, Survey survey);
}
