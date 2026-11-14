import java.util.Objects;

public class RepairVerifier {

    public static class Verification {
        public final boolean valid;
        public final String reason;

        public Verification(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    public static Verification verify(
            Rule[] original,
            RepairGenerator.Alternative alternative,
            int domainMin,
            int domainMax) {

        Rule[] candidate = alternative.rules;
        int target = alternative.gap;

        BoundaryChecker.Result before =
                BoundaryChecker.check(original, domainMin, domainMax);

        if (!before.overlaps.isEmpty()) {
            return fail("Original rules contain overlaps.");
        }

        if (!before.gaps.contains(target)) {
            return fail("Target value is not an original gap.");
        }

        // التأكد أن الفجوة المستهدفة قيمة واحدة وليست فجوة ممتدة
        if ((target > domainMin && before.gaps.contains(target - 1))
                || (target < domainMax
                && before.gaps.contains(target + 1))) {

            return fail("Target belongs to a wider gap.");
        }

        if (candidate.length != original.length) {
            return fail("The number of rules changed.");
        }

        int edits = 0;

        for (int i = 0; i < original.length; i++) {

            Rule oldRule = original[i];
            Rule newRule = candidate[i];

            // الأرقام والنتائج ومعرفات القواعد يجب ألا تتغير
            if (!Objects.equals(oldRule.id, newRule.id)
                    || !Objects.equals(oldRule.lower, newRule.lower)
                    || !Objects.equals(oldRule.upper, newRule.upper)
                    || !Objects.equals(oldRule.outcome, newRule.outcome)) {

                return fail("A threshold, outcome or rule ID changed.");
            }

            if (oldRule.includeLower != newRule.includeLower) {

                if (oldRule.lower == null
                        || oldRule.includeLower
                        || !newRule.includeLower) {

                    return fail("Unsupported lower-bound edit.");
                }

                edits++;
            }

            if (oldRule.includeUpper != newRule.includeUpper) {

                if (oldRule.upper == null
                        || oldRule.includeUpper
                        || !newRule.includeUpper) {

                    return fail("Unsupported upper-bound edit.");
                }

                edits++;
            }
        }

        if (edits != 1) {
            return fail("Exactly one comparator edit is required.");
        }

        BoundaryChecker.Result after =
                BoundaryChecker.check(candidate, domainMin, domainMax);

        if (after.gaps.contains(target)) {
            return fail("The target gap is still uncovered.");
        }

        if (!after.overlaps.isEmpty()) {
            return fail("The repair introduced an overlap.");
        }

        if (!before.gaps.containsAll(after.gaps)) {
            return fail("The repair introduced a new gap.");
        }

        // مقارنة القواعد المنطبقة على كل قيمة مغطاة سابقا
        for (long current = domainMin; current <= domainMax; current++) {

            int value = (int) current;
            boolean previouslyCovered = false;

            for (Rule rule : original) {
                if (rule.matches(value)) {
                    previouslyCovered = true;
                    break;
                }
            }

            if (previouslyCovered) {
                for (int i = 0; i < original.length; i++) {

                    if (original[i].matches(value)
                            != candidate[i].matches(value)) {

                        return fail(
                                "Previously covered behavior changed at "
                                        + value
                        );
                    }
                }
            }
        }

        return new Verification(
                true,
                "Target gap closed; no new gaps or overlaps; "
                        + "previously covered behavior preserved."
        );
    }

    private static Verification fail(String reason) {
        return new Verification(false, reason);
    }
}
