import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleValidator {

    public static Rule[] validate(
            JsonObject data,
            String criteria,
            String expectedVariable) {

        boolean supported = readBoolean(data, "supported");
        String reason = readString(data, "reason");

        if (!supported) {
            throw new IllegalArgumentException(
                    "Needs clarification or unsupported input: " + reason
            );
        }

        if (!readString(data, "variable").equals(expectedVariable)) {
            throw new IllegalArgumentException(
                    "The extracted variable does not match the input."
            );
        }

        JsonElement rulesElement = required(data, "rules");

        if (!rulesElement.isJsonArray()) {
            throw new IllegalArgumentException(
                    "rules must be an array."
            );
        }

        // كل معيار في سطر مستقل، مثل AC1: ...
        List<String> lines = criteria.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        JsonArray items = rulesElement.getAsJsonArray();

        if (lines.isEmpty() || items.size() != lines.size()) {
            throw new IllegalArgumentException(
                    "The number of extracted rules must match the criteria."
            );
        }

        Rule[] rules = new Rule[items.size()];
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {

            if (!items.get(i).isJsonObject()) {
                throw new IllegalArgumentException(
                        "Each rule must be an object."
                );
            }

            JsonObject item = items.get(i).getAsJsonObject();

            String id = readString(item, "id");
            String source = readString(item, "source");
            String outcome = readString(item, "outcome");

            String originalLine = lines.get(i);
            int colon = originalLine.indexOf(':');

            if (colon < 1) {
                throw new IllegalArgumentException(
                        "Each input line must start with an ID such as AC1:"
                );
            }

            String expectedId =
                    originalLine.substring(0, colon).trim();

            if (!expectedId.matches("AC[0-9]+")
                    || !id.equals(expectedId)
                    || !ids.add(id)) {

                throw new IllegalArgumentException(
                        "Invalid, duplicated or changed criterion ID."
                );
            }

            if (!source.equals(originalLine)) {
                throw new IllegalArgumentException(
                        id + ": source text changed."
                );
            }

            if (outcome.isBlank()) {
                throw new IllegalArgumentException(
                        id + ": outcome is missing."
                );
            }

            Integer lower = readBound(item, "lower");
            Integer upper = readBound(item, "upper");

            boolean includeLower = readBoolean(item, "includeLower");
            boolean includeUpper = readBoolean(item, "includeUpper");

            if ((lower == null && includeLower)
                    || (upper == null && includeUpper)) {

                throw new IllegalArgumentException(
                        id + ": a missing bound cannot be inclusive."
                );
            }

            // استخدام long يمنع تجاوز حدود int عند إضافة أو طرح 1
            long first = lower == null
                    ? Integer.MIN_VALUE
                    : (long) lower + (includeLower ? 0 : 1);

            long last = upper == null
                    ? Integer.MAX_VALUE
                    : (long) upper - (includeUpper ? 0 : 1);

            if (first > last) {
                throw new IllegalArgumentException(
                        id + ": the interval contains no integer values."
                );
            }

            rules[i] = new Rule(
                    id,
                    lower, includeLower,
                    upper, includeUpper,
                    outcome
            );
        }

        return rules;
    }

    private static JsonElement required(
            JsonObject object, String field) {

        if (!object.has(field)) {
            throw new IllegalArgumentException(
                    "Missing field: " + field
            );
        }

        return object.get(field);
    }

    private static String readString(
            JsonObject object, String field) {

        JsonElement value = required(object, field);

        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {

            throw new IllegalArgumentException(
                    field + " must be a string."
            );
        }

        return value.getAsString();
    }

    private static boolean readBoolean(
            JsonObject object, String field) {

        JsonElement value = required(object, field);

        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {

            throw new IllegalArgumentException(
                    field + " must be a boolean."
            );
        }

        return value.getAsBoolean();
    }

    private static Integer readBound(
            JsonObject object, String field) {

        JsonElement value = required(object, field);

        if (value.isJsonNull()) {
            return null;
        }

        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {

            throw new IllegalArgumentException(
                    field + " must be an integer or null."
            );
        }

        try {
            return Integer.valueOf(value.getAsString());

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " is outside the supported integer range."
            );
        }
    }
}
