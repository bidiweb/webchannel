package bidiweb.webchannel.client.support.basic;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import bidiweb.webchannel.client.support.Support.JsonDecoder;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** Implementation of WebChannel JsonDecoder interface using the org.json.* classes. */
class BasicWebChannelSupportJsonDecoder extends JsonDecoder {
  @Override
  public List<?> decodeArray(String data, int maxDepth) throws IllegalArgumentException {
    Preconditions.checkArgument(maxDepth > 0, "The maxDepth must be positive");
    try {
      return flattenJsonArrayToList(new JSONArray(data), maxDepth - 1);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Unable to decode given data into JSON: " + data, e);
    }
  }

  /**
   * Converts and returns the given JSON array as a list of objects.
   *
   * @param maxDepth the max number of levels to recursively convert JSONArray elements as list. If
   *     not greater than 0, returns inner element as is.
   */
  private List<Object> flattenJsonArrayToList(JSONArray array, int maxDepth) throws JSONException {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (int i = 0; i < array.length(); ++i) {
      Object element = array.get(i);
      if (maxDepth > 0 && element instanceof JSONArray) {
        builder.add(flattenJsonArrayToList((JSONArray) element, maxDepth - 1));
      } else {
        builder.add(element);
      }
    }
    return builder.build();
  }
}
