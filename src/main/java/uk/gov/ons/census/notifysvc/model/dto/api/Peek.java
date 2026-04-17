package uk.gov.ons.census.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class Peek {
  private String messageHash;
  private byte[] messagePayload;
}
