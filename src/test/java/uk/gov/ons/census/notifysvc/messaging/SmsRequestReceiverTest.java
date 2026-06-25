package uk.gov.ons.census.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.census.notifysvc.testUtils.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.census.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.census.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.SmsTemplate;
import uk.gov.ons.census.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.SmsRequest;
import uk.gov.ons.census.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.census.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.census.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.census.notifysvc.service.SmsRequestService;
import uk.gov.ons.census.notifysvc.utils.PubSubHelper;

@ExtendWith(MockitoExtension.class)
class SmsRequestReceiverTest {

  @Mock SmsTemplateRepository smsTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock SmsRequestService smsRequestService;
  @Mock PubSubHelper pubSubHelper;

  @InjectMocks SmsRequestReceiver smsRequestReceiver;

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final String VALID_PHONE_NUMBER = "07123456789";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");

  @Test
  void testReceiveMessageHappyPathWithUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(
            smsTemplate.getQuestionnaireType(), smsTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQidCreated));
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(VALID_PHONE_NUMBER);
    smsRequest.setUacMetadata(TEST_UAC_METADATA);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When
    smsRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(smsRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    SmsRequestEnriched smsRequestEnriched = sentEvent.getPayload().getSmsRequestEnriched();
    assertThat(smsRequestEnriched.getPackCode()).isEqualTo(smsRequest.getPackCode());
    assertThat(smsRequestEnriched.getCaseId()).isEqualTo(smsRequest.getCaseId());
    assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
    assertThat(smsRequestEnriched.getUac()).isEqualTo(newUacQidCreated.getUac());
    assertThat(smsRequestEnriched.getQid()).isEqualTo(newUacQidCreated.getQid());

    verify(smsRequestService)
        .buildAndSendSmsConfirmation(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsRequestEvent.getPayload().getSmsRequest().getUacMetadata(),
            smsRequestEvent.getPayload().getSmsRequest().getPersonalisation(),
            Optional.of(newUacQidCreated),
            false,
            smsRequestEvent.getHeader().getSource(),
            smsRequestEvent.getHeader().getChannel(),
            smsRequestEvent.getHeader().getCorrelationId(),
            smsRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageExceptionOnNoQidTypeForUacQidTemplate() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});
    smsTemplate.setQuestionnaireType(null);

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(
            smsTemplate.getQuestionnaireType(), smsTemplate.getTemplate()))
        .thenThrow(
            new IllegalStateException(
                "Questionnaire type is required to generate a new UAC/QID pair"));
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(VALID_PHONE_NUMBER);
    smsRequest.setUacMetadata(TEST_UAC_METADATA);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When
    Exception thrown =
        assertThrows(RuntimeException.class, () -> smsRequestReceiver.receiveMessage(eventMessage));
    // Then
    assertThat(thrown.getMessage()).isEqualTo("Failed to generate UAC/QID pair for SMS message");
    verifyNoInteractions(pubSubHelper);
  }

  @Test
  void testReceiveMessageHappyPathWithoutUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {"foobar"});

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(
            smsTemplate.getQuestionnaireType(), smsTemplate.getTemplate()))
        .thenReturn(Optional.empty());
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(VALID_PHONE_NUMBER);
    smsRequest.setUacMetadata(TEST_UAC_METADATA);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When
    smsRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(smsRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    SmsRequestEnriched smsRequestEnriched = sentEvent.getPayload().getSmsRequestEnriched();
    assertThat(smsRequestEnriched.getPackCode()).isEqualTo(smsRequest.getPackCode());
    assertThat(smsRequestEnriched.getCaseId()).isEqualTo(smsRequest.getCaseId());
    assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
    assertThat(smsRequestEnriched.getUac()).isNull();
    assertThat(smsRequestEnriched.getQid()).isNull();

    verify(smsRequestService)
        .buildAndSendSmsConfirmation(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsRequestEvent.getPayload().getSmsRequest().getUacMetadata(),
            smsRequestEvent.getPayload().getSmsRequest().getPersonalisation(),
            Optional.empty(),
            false,
            smsRequestEvent.getHeader().getSource(),
            smsRequestEvent.getHeader().getChannel(),
            smsRequestEvent.getHeader().getCorrelationId(),
            smsRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageExceptionOnInvalidPhoneNumber() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    String invalidPhoneNumber = "blah";

    when(smsRequestService.validatePhoneNumber(invalidPhoneNumber)).thenReturn(false);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(invalidPhoneNumber);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(RuntimeException.class, () -> smsRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).isEqualTo("Invalid phone number on SMS request message");
    verifyNoInteractions(caseRepository);
    verifyNoInteractions(smsTemplateRepository);
    verifyNoInteractions(pubSubHelper);
  }

  @Test
  void testReceiveMessageExceptionOnCaseNotFound() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);
    when(caseRepository.existsById(testCase.getId())).thenReturn(false);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(VALID_PHONE_NUMBER);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(RuntimeException.class, () -> smsRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).isEqualTo("Case not found with ID: " + smsRequest.getCaseId());
    verifyNoInteractions(pubSubHelper);
  }

  @Test
  void testReceiveMessageExceptionOnMissingSMSTemplate() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);
    when(smsTemplateRepository.findById(smsTemplate.getPackCode())).thenReturn(Optional.empty());

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber(VALID_PHONE_NUMBER);
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(RuntimeException.class, () -> smsRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).isEqualTo("SMS Template not found: " + TEST_PACK_CODE);
    verifyNoInteractions(caseRepository);
    verifyNoInteractions(pubSubHelper);
  }
}
