import java.util.ArrayList;
import java.util.List;

public class BoundaryChecker {

    // حفظ نتائج الفحص بدل الاكتفاء بطباعتها
    public static class Result {
        public final List<Integer> gaps = new ArrayList<>();
        public final List<Integer> overlaps = new ArrayList<>();
    }

    public static Result check(
            Rule[] rules, int domainMin, int domainMax) {

        // التأكد من أن النطاق صحيح وحجمه مناسب
        long domainSize = (long) domainMax - domainMin + 1;

        if (domainSize <= 0 || domainSize > 10000) {
            throw new IllegalArgumentException(
                    "Domain must contain 1 to 10000 integer values."
            );
        }

        Result result = new Result();

        for (long current = domainMin; current <= domainMax; current++) {

            int value = (int) current;
            int matchingRules = 0;

            for (Rule rule : rules) {
                if (rule.matches(value)) {
                    matchingRules++;
                }
            }

            if (matchingRules == 0) {
                result.gaps.add(value);
            } else if (matchingRules > 1) {
                result.overlaps.add(value);
            }
        }

        return result;
    }
}
