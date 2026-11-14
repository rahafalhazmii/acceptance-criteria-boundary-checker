public class LocalChecks {

    public static void main(String[] args) {

        // 1. Complete coverage
        Rule[] rules = {
            new Rule(
                "AC1", null, false,
                50, true, "Shipping fee = 15"
            ),
            new Rule(
                "AC2", 50, false,
                null, false, "Free shipping"
            )
        };

        var result = BoundaryChecker.check(rules, 0, 1000);

        System.out.println("TEST: Complete coverage");
        System.out.println("Uncovered values: " + result.gaps);
        System.out.println("Overlapping values: " + result.overlaps);

        if (!result.gaps.isEmpty() || !result.overlaps.isEmpty()) {
            throw new AssertionError(
                "FAILED: Expected no gaps and no overlaps."
            );
        }

        System.out.println("PASSED");

        // 2. Overlap at 50
        Rule[] overlappingRules = {
            new Rule(
                "AC1", null, false,
                50, true, "Shipping fee = 15"
            ),
            new Rule(
                "AC2", 50, true,
                null, false, "Free shipping"
            )
        };

        var overlapResult =
            BoundaryChecker.check(overlappingRules, 0, 1000);

        System.out.println("\nTEST: Overlap at 50");
        System.out.println("Uncovered values: " + overlapResult.gaps);
        System.out.println("Overlapping values: " + overlapResult.overlaps);

        if (!overlapResult.gaps.isEmpty()
                || !overlapResult.overlaps.equals(
                    java.util.List.of(50))) {
            throw new AssertionError(
                "FAILED: Expected only an overlap at 50."
            );
        }

        System.out.println("PASSED");

        // 3. Wide gap: detected but not repaired
        Rule[] wideGapRules = {
            new Rule(
                "AC1", null, false,
                50, false, "Shipping fee = 15"
            ),
            new Rule(
                "AC2", 52, false,
                null, false, "Free shipping"
            )
        };

        var wideGapResult =
            BoundaryChecker.check(wideGapRules, 0, 1000);

        var wideGapRepairs =
            RepairGenerator.generate(wideGapRules, 0, 1000);

        System.out.println("\nTEST: Wide gap");
        System.out.println("Uncovered values: " + wideGapResult.gaps);
        System.out.println("Overlapping values: " + wideGapResult.overlaps);
        System.out.println("Repair alternatives: " + wideGapRepairs.size());

        if (!wideGapResult.gaps.equals(
                    java.util.List.of(50, 51, 52))
                || !wideGapResult.overlaps.isEmpty()
                || !wideGapRepairs.isEmpty()) {
            throw new AssertionError(
                "FAILED: Expected gap [50, 51, 52] and no repairs."
            );
        }

        System.out.println("PASSED");

        // 4. Single gap with two independent repair alternatives
        Rule[] gapRules = {
            new Rule(
                "AC1", null, false,
                50, false, "Shipping fee = 15"
            ),
            new Rule(
                "AC2", 50, false,
                null, false, "Free shipping"
            )
        };

        var gapResult = BoundaryChecker.check(gapRules, 0, 1000);
        var alternatives = RepairGenerator.generate(gapRules, 0, 1000);

        System.out.println("\nTEST: Single gap with two repairs");
        System.out.println("Uncovered values: " + gapResult.gaps);
        System.out.println("Repair alternatives: " + alternatives.size());

        if (!gapResult.gaps.equals(java.util.List.of(50))
                || !gapResult.overlaps.isEmpty()
                || alternatives.size() != 2) {
            throw new AssertionError(
                "FAILED: Expected gap [50] and two alternatives."
            );
        }

        boolean feeAt100 = false;
        boolean freeAt100 = false;

        for (var alternative : alternatives) {

            var verification =
                RepairVerifier.verify(gapRules, alternative, 0, 1000);

            if (!verification.valid) {
                throw new AssertionError(
                    "FAILED: A valid repair was rejected."
                );
            }

            for (int value = 0; value <= 1000; value++) {

                int matches = 0;
                String actualOutcome = null;

                for (Rule rule : alternative.rules) {
                    if (rule.matches(value)) {
                        matches++;
                        actualOutcome = rule.outcome;
                    }
                }

                if (matches != 1) {
                    throw new AssertionError(
                        "FAILED: Expected one matching rule at " + value
                    );
                }

                if (value < 50
                        && !"Shipping fee = 15".equals(actualOutcome)) {
                    throw new AssertionError(
                        "FAILED: Behavior below 50 changed."
                    );
                }

                if (value > 50
                        && !"Free shipping".equals(actualOutcome)) {
                    throw new AssertionError(
                        "FAILED: Behavior above 50 changed."
                    );
                }

                if (value == 50) {
                    if ("Shipping fee = 15".equals(actualOutcome)) {
                        feeAt100 = true;
                    } else if ("Free shipping".equals(actualOutcome)) {
                        freeAt100 = true;
                    } else {
                        throw new AssertionError(
                            "FAILED: Unexpected outcome at 50."
                        );
                    }
                }
            }
        }

        if (!feeAt100 || !freeAt100) {
            throw new AssertionError(
                "FAILED: Expected both distinct business alternatives."
            );
        }

        System.out.println("Both repairs checked across all 1001 values.");
        System.out.println("PASSED");

        // 5. Reject a repair that changes the outcome
        Rule[] changedOutcomeRules = {
            new Rule(
                "AC1", null, false,
                50, true, "Shipping fee = 20"
            ),
            new Rule(
                "AC2", 50, false,
                null, false, "Free shipping"
            )
        };

        var changedOutcomeAlternative =
            new RepairGenerator.Alternative(
                50,
                "Invalid repair: changes the shipping fee",
                changedOutcomeRules
            );

        var changedOutcomeVerification =
            RepairVerifier.verify(
                gapRules, changedOutcomeAlternative, 0, 1000
            );

        System.out.println("\nTEST: Reject outcome change");
        System.out.println(
            "Repair accepted: " + changedOutcomeVerification.valid
        );
        System.out.println(
            "Reason: " + changedOutcomeVerification.reason
        );

        if (changedOutcomeVerification.valid) {
            throw new AssertionError(
                "FAILED: A repair that changes the outcome was accepted."
            );
        }

        System.out.println("PASSED");

        // 6. Reject a repair that makes both rules include 50
        Rule[] overlappingRepairRules = {
            new Rule(
                "AC1", null, false,
                50, true, "Shipping fee = 15"
            ),
            new Rule(
                "AC2", 50, true,
                null, false, "Free shipping"
            )
        };

        var overlappingAlternative =
            new RepairGenerator.Alternative(
                50,
                "Invalid repair: both rules include 50",
                overlappingRepairRules
            );

        var overlappingVerification =
            RepairVerifier.verify(
                gapRules, overlappingAlternative, 0, 1000
            );

        System.out.println("\nTEST: Reject overlapping repair");
        System.out.println(
            "Repair accepted: " + overlappingVerification.valid
        );
        System.out.println(
            "Reason: " + overlappingVerification.reason
        );

        if (overlappingVerification.valid) {
            throw new AssertionError(
                "FAILED: An overlapping repair was accepted."
            );
        }

        System.out.println("PASSED");
        System.out.println("\nALL 6 LOCAL TESTS PASSED");
    }
}