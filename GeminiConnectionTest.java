import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiConnectionTest {

    public static void main(String[] args) {

        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println(
                    "Key is missing. Set GEMINI_API_KEY in this terminal."
            );
            return;
        }

        apiKey = apiKey.trim();

        String requestBody = """
                {
                  "model": "gemini-3.6-flash",
                  "input": "Reply with exactly: Connection successful.",
                  "store": false
                }
                """;

        HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(20))
        .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://generativelanguage.googleapis.com"
                                + "/v1beta/interactions"
                ))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .header("Api-Revision", "2026-05-20")
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody, StandardCharsets.UTF_8
                ))
                .build();

        System.out.println("Connecting to Gemini...");

        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            System.out.println("HTTP status: " + response.statusCode());

            // طباعة الرد مع إخفاء المفتاح إذا ظهر فيه
            System.out.println(
                    response.body().replace(apiKey, "[HIDDEN]")
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Connection interrupted.");

        } catch (Exception exception) {
            String message = String.valueOf(exception.getMessage());

            System.out.println(
                    "Connection failed: "
                            + message.replace(apiKey, "[HIDDEN]")
            );
        }
    }
}
