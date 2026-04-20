package uk.gov.ons.census.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class ExceptionReportResponse {
  private boolean peek;
  private boolean logIt;
  private boolean skipIt;
}
