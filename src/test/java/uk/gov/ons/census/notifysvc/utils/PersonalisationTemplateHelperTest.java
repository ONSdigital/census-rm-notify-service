package uk.gov.ons.census.notifysvc.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.census.notifysvc.utils.Constants.TEMPLATE_REQUEST_PREFIX;
import static uk.gov.ons.census.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.common.model.entity.Case;

class PersonalisationTemplateHelperTest {
  private static final String TEST_UAC = "TEST_UAC";
  private static final String TEST_QID = "TEST_QID";
  private static final Map<String, String> TEST_PERSONALISATION =
      Map.of("fooRequest", "barRequest");

  @Test
  void testBuildPersonalisationFromTemplate() {
    // Given
    String[] template =
        new String[] {
          TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY, "UPRN",
        };

    Case testCase = getTestCase();

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID, TEST_PERSONALISATION);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_UAC_KEY, TEST_UAC)
        .containsEntry(TEMPLATE_QID_KEY, TEST_QID)
        .containsEntry("UPRN", "1234567890");
  }

  private static @NonNull Case getTestCase() {
    Case testCase = new Case();
    testCase.setTreatmentCode("HH_PSLE");
    testCase.setAddressType("H");
    testCase.setUprn("1234567890");
    testCase.setEstabUprn("1234567890");
    testCase.setEstabType("HOUSEHOLD");
    testCase.setAddressLine1("123 Fake Street");
    testCase.setTownName("Testington");
    testCase.setRegion("E");
    testCase.setPostcode("NP10 111");
    testCase.setAddressType("HH");
    testCase.setAddressLevel("U");
    testCase.setAbpCode("ABC123");
    testCase.setFieldCoordinatorId("ABCD1234");
    testCase.setFieldOfficerId("ABCD1234");
    testCase.setOa("A12345678");
    testCase.setLsoa("A12345678");
    testCase.setMsoa("A12345678");
    testCase.setLad("ABC123");
    testCase.setHtc("1");
    testCase.setLatitude("51.5074");
    testCase.setLongitude("0.1278");
    testCase.setPrintBatch("1");
    testCase.setSecureEstablishment(false);
    return testCase;
  }

  @Test
  void testBuildPersonalisationFromTemplateJustUac() {
    // Given
    String[] template = new String[] {TEMPLATE_UAC_KEY};

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID, TEST_PERSONALISATION);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_UAC_KEY, TEST_UAC)
        .containsOnlyKeys(TEMPLATE_UAC_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustQid() {
    // Given
    String[] template = new String[] {TEMPLATE_QID_KEY};

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID, TEST_PERSONALISATION);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_QID_KEY, TEST_QID)
        .containsOnlyKeys(TEMPLATE_QID_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustSampleFields() {
    // Given
    String[] template = new String[] {"UPRN", "ADDRESS_LINE1"};

    Case testCase = getTestCase();
    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID, TEST_PERSONALISATION);

    // Then
    assertThat(personalisationValues)
        .containsEntry("UPRN", testCase.getUprn())
        .containsEntry("ADDRESS_LINE1", testCase.getAddressLine1());
  }

  @Test
  void testBuildPersonalisationFromTemplateNoUacQidGiven() {
    // Given
    String[] template = new String[] {"UPRN", "ADDRESS_LINE1", "__request__.fooRequest"};

    Case testCase = getTestCase();
    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_PERSONALISATION);

    // Then
    assertThat(personalisationValues)
        .containsEntry("UPRN", testCase.getUprn())
        .containsEntry("ADDRESS_LINE1", testCase.getAddressLine1())
        .containsEntry("__request__.fooRequest", "barRequest");
  }

  @Test
  void testBuildPersonalisationFromTemplateNoPersonalisation() {
    // Given
    String[] template = new String[] {"UPRN", TEMPLATE_REQUEST_PREFIX + "foo"};

    Case testCase = getTestCase();
    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(template, testCase, null);

    // Then
    assertThat(personalisationValues).containsEntry("UPRN", testCase.getUprn());
  }
}
