package uk.gov.ons.census.notifysvc.messaging;

import static uk.gov.ons.census.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.census.common.model.entity.SmsTemplate;
import uk.gov.ons.census.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.SmsRequest;
import uk.gov.ons.census.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.census.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.census.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.notifysvc.service.SmsRequestService;
import uk.gov.ons.census.notifysvc.utils.Constants;
import uk.gov.ons.census.notifysvc.utils.PubSubHelper;

@MessageEndpoint
public class SmsRequestReceiver {

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final SmsRequestService smsRequestService;
  private final PubSubHelper pubSubHelper;

  public SmsRequestReceiver(
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      SmsRequestService smsRequestService,
      PubSubHelper pubSubHelper) {
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.smsRequestService = smsRequestService;
    this.pubSubHelper = pubSubHelper;
  }

  private static final Logger log = LoggerFactory.getLogger(SmsRequestReceiver.class);

  @ServiceActivator(inputChannel = "smsRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO smsRequestEvent = convertJsonBytesToEvent(message.getPayload());
    EventHeaderDTO smsRequestHeader = smsRequestEvent.getHeader();
    SmsRequest smsRequest = smsRequestEvent.getPayload().getSmsRequest();

    if (!smsRequestService.validatePhoneNumber(smsRequest.getPhoneNumber())) {
      throw new RuntimeException("Invalid phone number on SMS request message");
    }

    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(smsRequest.getPackCode())
            .orElseThrow(
                () -> new RuntimeException("SMS Template not found: " + smsRequest.getPackCode()));

    if (!caseRepository.existsById(smsRequest.getCaseId())) {
      throw new RuntimeException("Case not found with ID: " + smsRequest.getCaseId());
    }

    Optional<UacQidCreatedPayloadDTO> newUacQidPair;
    try {
      newUacQidPair =
          smsRequestService.fetchNewUacQidPairIfRequired(
              smsTemplate.getQuestionnaireType(), smsTemplate.getTemplate());
    } catch (IllegalArgumentException illegalArgumentException) {
      log.atError()
          .setMessage("Failed to fetch UAC QID pair for SMS request event")
          .addKeyValue("messageId", smsRequestHeader.getMessageId())
          .addKeyValue("correlationId", smsRequestHeader.getCorrelationId())
          .addKeyValue("caseId", smsRequest.getCaseId())
          .addKeyValue("packCode", smsTemplate.getPackCode())
          .log();
      throw new RuntimeException(
          String.format(
              "Failed to fetch UAC/QID pair for SMS request event for pack code: %s",
              smsTemplate.getPackCode()),
          illegalArgumentException);
    }
    EventDTO smsRequestEnrichedEvent =
        buildSmsRequestEnrichedEvent(smsRequest, smsRequestHeader, newUacQidPair);

    // Send the event, including the UAC/QID pair if required, to be linked and logged
    smsRequestService.buildAndSendSmsConfirmation(
        smsRequest.getCaseId(),
        smsRequest.getPackCode(),
        smsRequest.getUacMetadata(),
        smsRequest.getPersonalisation(),
        newUacQidPair,
        smsRequest.isScheduled(),
        smsRequestHeader.getSource(),
        smsRequestHeader.getChannel(),
        smsRequestHeader.getCorrelationId(),
        smsRequestHeader.getOriginatingUser());

    // Send the enriched SMS Request, now including the UAC/QID pair if required.
    // This enriched message can then safely be retried multiple times without potentially
    // generating and linking more, unnecessary UAC/QID pairs
    pubSubHelper.publishAndConfirm(smsRequestEnrichedTopic, smsRequestEnrichedEvent);
  }

  private EventDTO buildSmsRequestEnrichedEvent(
      SmsRequest smsRequest,
      EventHeaderDTO smsRequestHeader,
      Optional<UacQidCreatedPayloadDTO> uacQidPair) {
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(smsRequest.getCaseId());
    smsRequestEnriched.setPhoneNumber(smsRequest.getPhoneNumber());
    smsRequestEnriched.setPackCode(smsRequest.getPackCode());
    smsRequestEnriched.setScheduled(smsRequest.isScheduled());
    smsRequestEnriched.setPersonalisation(smsRequest.getPersonalisation());

    if (uacQidPair.isPresent()) {
      smsRequestEnriched.setUac(uacQidPair.get().getUac());
      smsRequestEnriched.setQid(uacQidPair.get().getQid());
    }

    EventHeaderDTO enrichedEventHeader = new EventHeaderDTO();
    enrichedEventHeader.setMessageId(UUID.randomUUID());
    enrichedEventHeader.setCorrelationId(smsRequestHeader.getCorrelationId());
    enrichedEventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    enrichedEventHeader.setChannel(smsRequestHeader.getChannel());
    enrichedEventHeader.setSource(smsRequestHeader.getSource());
    enrichedEventHeader.setOriginatingUser(smsRequestHeader.getOriginatingUser());
    enrichedEventHeader.setTopic(smsRequestEnrichedTopic);
    enrichedEventHeader.setDateTime(OffsetDateTime.now());

    PayloadDTO enrichedPayload = new PayloadDTO();
    enrichedPayload.setSmsRequestEnriched(smsRequestEnriched);

    EventDTO smsRequestEnrichedEvent = new EventDTO();
    smsRequestEnrichedEvent.setHeader(enrichedEventHeader);
    smsRequestEnrichedEvent.setPayload(enrichedPayload);
    return smsRequestEnrichedEvent;
  }
}
