public class Rule {

    // رقم معيار القبول، مثل AC1
    final String id;

    // الحدود؛ null تعني عدم وجود حد في هذا الاتجاه
    final Integer lower;
    final Integer upper;

    // هل القيمة عند الحد مشمولة؟
    final boolean includeLower;
    final boolean includeUpper;

    // النتيجة المذكورة في معيار القبول
    final String outcome;

    public Rule(String id,
                Integer lower, boolean includeLower,
                Integer upper, boolean includeUpper,
                String outcome) {

        this.id = id;
        this.lower = lower;
        this.includeLower = includeLower;
        this.upper = upper;
        this.includeUpper = includeUpper;
        this.outcome = outcome;
    }

    // هل تنطبق القاعدة على هذه القيمة؟
    public boolean matches(int value) {

        if (lower != null) {
            if (value < lower ||
                (value == lower && !includeLower)) {
                return false;
            }
        }

        if (upper != null) {
            if (value > upper ||
                (value == upper && !includeUpper)) {
                return false;
            }
        }

        return true;
    } 
}
