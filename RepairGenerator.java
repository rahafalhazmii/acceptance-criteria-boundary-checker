import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RepairGenerator {

    // بديل إصلاح مع وصفه والقواعد بعد التعديل
    public static class Alternative {
        public final int gap;
        public final String description;
        public final Rule[] rules;

        public Alternative(
                int gap, String description, Rule[] rules) {

            this.gap = gap;
            this.description = description;
            this.rules = rules;
        }
    }

    public static List<Alternative> generate(
            Rule[] original, int domainMin, int domainMax) {

        BoundaryChecker.Result result =
                BoundaryChecker.check(original, domainMin, domainMax);

        List<Alternative> alternatives = new ArrayList<>();

        // وجود تداخل يحتاج مراجعة قبل اقتراح إصلاح للفجوات
        if (!result.overlaps.isEmpty()) {
            return alternatives;
        }

        Set<Integer> gaps = new HashSet<>(result.gaps);

        for (int gap : result.gaps) {

            // نقترح إصلاحات للفجوات المكونة من قيمة واحدة فقط
            boolean previousIsGap =
                    gap > domainMin && gaps.contains(gap - 1);

            boolean nextIsGap =
                    gap < domainMax && gaps.contains(gap + 1);

            if (previousIsGap || nextIsGap) {
                continue;
            }

            for (int i = 0; i < original.length; i++) {

                Rule rule = original[i];

                // تغيير الحد العلوي من < إلى <=
                if (rule.upper != null
                        && rule.upper == gap
                        && !rule.includeUpper) {

                    Rule replacement = new Rule(
                            rule.id,
                            rule.lower, rule.includeLower,
                            rule.upper, true,
                            rule.outcome
                    );

                    addAlternative(
                            alternatives, original, i, replacement,
                            gap, rule.id + ": change x < " + gap
                                    + " to x <= " + gap
                                    + " | Outcome: " + rule.outcome
                    );
                }

                // تغيير الحد السفلي من > إلى >=
                if (rule.lower != null
                        && rule.lower == gap
                        && !rule.includeLower) {

                    Rule replacement = new Rule(
                            rule.id,
                            rule.lower, true,
                            rule.upper, rule.includeUpper,
                            rule.outcome
                    );

                    addAlternative(
                            alternatives, original, i, replacement,
                            gap, rule.id + ": change x > " + gap
                                    + " to x >= " + gap
                                    + " | Outcome: " + rule.outcome
                    );
                }
            }
        }

        return alternatives;
    }

    private static void addAlternative(
            List<Alternative> alternatives,
            Rule[] original,
            int index,
            Rule replacement,
            int gap,
            String description) {

        if (!replacement.matches(gap)) {
            return;
        }

        // نسخة مستقلة؛ القواعد الأصلية تبقى محفوظة
        Rule[] candidateRules = original.clone();
        candidateRules[index] = replacement;

        alternatives.add(
                new Alternative(gap, description, candidateRules)
        );
    }
}
