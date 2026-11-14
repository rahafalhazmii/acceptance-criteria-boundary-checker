import com.google.gson.JsonObject;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        int domainMin = 0;
        int domainMax = 1000;
        String variable = "orderValue";

        String criteria = """
                AC1: If orderValue is less than 100, the shipping fee is 15.
                AC2: If orderValue is greater than 100, shipping is free.
                """;

        try {
            System.out.println("Extracting rules using Gemini...");

            JsonObject extracted = RuleExtractor.extract(
                    criteria, variable, domainMin, domainMax
            );

            Rule[] rules = RuleValidator.validate(
                    extracted, criteria, variable
            );

            System.out.println(
                    "Structure validation passed. Rules: " + rules.length
            );

            for (Rule rule : rules) {
                System.out.println(rule.id + " outcome: " + rule.outcome);
            }

            BoundaryChecker.Result result =
                    BoundaryChecker.check(rules, domainMin, domainMax);

            System.out.println();
            System.out.println("Uncovered values: " + result.gaps);
            System.out.println("Overlapping values: " + result.overlaps);

            if (!result.overlaps.isEmpty()) {
                System.out.println(
                        "Manual review required: original rules overlap."
                );
                return;
            }

            if (result.gaps.isEmpty()) {
                System.out.println(
                        "No gaps within the specified integer domain."
                );
                return;
            }

            List<RepairGenerator.Alternative> alternatives =
                    RepairGenerator.generate(
                            rules, domainMin, domainMax
                    );

            System.out.println();
            System.out.println("Repair alternatives:");

            if (alternatives.isEmpty()) {
                System.out.println(
                        "No alternatives within the allowed operations."
                );
                return;
            }

            for (int i = 0; i < alternatives.size(); i++) {

                RepairGenerator.Alternative alternative =
                        alternatives.get(i);

                RepairVerifier.Verification verification =
                        RepairVerifier.verify(
                                rules, alternative, domainMin, domainMax
                        );

                System.out.println();
                System.out.println(
                        (i + 1) + ". " + alternative.description
                );

                System.out.println(
                        "Status: "
                                + (verification.valid
                                ? "VERIFIED" : "REJECTED")
                );

                System.out.println("Reason: " + verification.reason);
            }

            System.out.println();
            System.out.println(
                    "The user chooses the intended business outcome."
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("Request interrupted.");

        } catch (Exception exception) {
            System.out.println(
                    "Analysis stopped: " + exception.getMessage()
            );
        }
    }
}