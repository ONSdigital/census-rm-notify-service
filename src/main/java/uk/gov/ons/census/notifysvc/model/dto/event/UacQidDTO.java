package uk.gov.ons.census.notifysvc.model.dto.event;

import lombok.Data;

@Data
public class UacQidDTO {
  private String uac;
  private String qid;
}
