import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiClient {

    public static String ask(String prompt) throws Exception {

        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is missing in this terminal."
            );
        }

        apiKey = apiKey.trim();

        // Gson يحول النص إلى JSON مع معالجة علامات التنصيص والأسطر
        JsonObject body = new JsonObject();
        body.addProperty("model", "models/gemini-3.5-flash-lite");
        body.addProperty("input", prompt);
        body.addProperty("store", false);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://generativelanguage.googleapis.com"
                                + "/v1beta/interactions"
                ))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> response = null;

for (int attempt = 1; attempt <= 3; attempt++) {

    response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8
            )
    );

    int status = response.statusCode();

    boolean temporaryFailure =
            status == 500 || status == 502
                    || status == 503 || status == 504;

    if (!temporaryFailure || attempt == 3) {
        break;
    }

    System.out.println(
            "Gemini is temporarily unavailable. Retrying..."
                    + " Attempt " + (attempt + 1) + " of 3"
    );

    Thread.sleep(10000L * attempt);
}

        if (response.statusCode() == 503) {
        throw new IllegalStateException(
                        "Gemini HTTP 503 after 3 attempts: "
                                        + response.body().replace(apiKey, "[HIDDEN]"));
}

        if (response.statusCode() == 429) {
        throw new IllegalStateException(
                        "Gemini usage limit reached. "
                                        + response.body().replace(apiKey, "[HIDDEN]"));
}

        if (response.statusCode() != 200) {
        throw new IllegalStateException(
                        "Gemini HTTP " + response.statusCode() + ": "
                                        + response.body().replace(apiKey, "[HIDDEN]"));
}

        JsonObject root =
                JsonParser.parseString(response.body()).getAsJsonObject();

        if (!root.has("status")
                || !"completed".equals(root.get("status").getAsString())) {
            throw new IllegalStateException(
                    "Gemini did not return a completed response."
            );
        }

        StringBuilder answer = new StringBuilder();

        // استخراج الإجابة النهائية فقط
        if (root.has("steps") && root.get("steps").isJsonArray()) {

            for (JsonElement item : root.getAsJsonArray("steps")) {

                JsonObject step = item.getAsJsonObject();

                if (!step.has("type")
                        || !"model_output".equals(
                                step.get("type").getAsString())
                        || !step.has("content")) {
                    continue;
                }

                for (JsonElement part : step.getAsJsonArray("content")) {

                    JsonObject content = part.getAsJsonObject();

                    if (content.has("type")
                            && "text".equals(
                                    content.get("type").getAsString())
                            && content.has("text")) {

                        answer.append(content.get("text").getAsString());
                    }
                }
            }
        }

        if (answer.toString().isBlank()) {
            throw new IllegalStateException(
                    "Gemini returned no final text."
            );
        }

        return answer.toString().trim();
    }

    // تجربة الملف بشكل مستقل
    public static void main(String[] args) {

        System.out.println("Connecting to Gemini...");

        try {
            String answer = ask(
                    "Reply with exactly: Connection successful."
            );

            System.out.println("Gemini reply: " + answer);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Request interrupted.");

        } catch (Exception exception) {
            System.out.println("Error: " + exception.getMessage());
        }
    }
}
