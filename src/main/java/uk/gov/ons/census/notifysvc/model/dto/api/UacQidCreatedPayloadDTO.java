package uk.gov.ons.census.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class UacQidCreatedPayloadDTO {
  private String uac;
  private String qid;
}
