package uk.gov.ons.census.notifysvc.utils;

import static uk.gov.ons.census.notifysvc.utils.Constants.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.SampleField;

public class PersonalisationTemplateHelper {
  public static Map<String, String> buildPersonalisationFromTemplate(
      String[] template,
      Case caze,
      String uac,
      String qid,
      Map<String, String> requestPersonalisation) {
    Map<String, String> templateValues = new HashMap<>();

    for (String templateItem : template) {

      if (TEMPLATE_UAC_KEY.equals(templateItem)) {
        templateValues.put(TEMPLATE_UAC_KEY, uac);

      } else if (TEMPLATE_QID_KEY.equals(templateItem)) {
        templateValues.put(TEMPLATE_QID_KEY, qid);
      } else if (TEMPLATE_CASEREF_KEY.equals(templateItem)) {
        templateValues.put(TEMPLATE_CASEREF_KEY, String.valueOf(caze.getCaseRef()));
      } else if (templateItem.startsWith(TEMPLATE_REQUEST_PREFIX)) {
        if (requestPersonalisation != null
            && requestPersonalisation.containsKey(
                templateItem.substring(TEMPLATE_REQUEST_PREFIX.length()))) {
          templateValues.put(
              templateItem,
              requestPersonalisation.get(templateItem.substring(TEMPLATE_REQUEST_PREFIX.length())));
        }
      } else {
        templateValues.put(
            templateItem, caze.getSampleFieldValueAsString(SampleField.valueOf(templateItem)));
      }
    }

    return templateValues;
  }

  public static Map<String, String> buildPersonalisationFromTemplate(
      String[] template, Case caze, Map<String, String> requestPersonalisation) {
    return buildPersonalisationFromTemplate(template, caze, null, null, requestPersonalisation);
  }

  public static boolean doesTemplateRequireNewUacQid(String... template) {
    return CollectionUtils.containsAny(
        Arrays.asList(template), List.of(TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY));
  }
}
