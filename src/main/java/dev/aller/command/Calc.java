package dev.aller.command;

/** The launcher's calculator: arithmetic with brackets, and counts of items turned into stacks. */
public final class Calc {
    private final String s;
    private int pos;

    private Calc(String s) {
        this.s = s;
    }

    /** The value of an arithmetic expression, or null if the text is not one (a bare number is not). */
    public static Double eval(String text) {
        String t = text.replace(" ", "").replace(',', '.').replace('x', '*').replace('×', '*').replace('÷', '/');
        if (t.isEmpty() || !Character.isDigit(t.charAt(t.length() - 1)) && t.charAt(t.length() - 1) != ')') return null;
        boolean operator = false;
        for (int i = 1; i < t.length(); i++) if ("+-*/^%".indexOf(t.charAt(i)) >= 0) operator = true;
        if (!operator) return null;
        try {
            Calc c = new Calc(t);
            double v = c.sum();
            return c.pos == t.length() && Double.isFinite(v) ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private double sum() {
        double v = product();
        while (pos < s.length()) {
            char op = s.charAt(pos);
            if (op == '+') {
                pos++;
                v += product();
            } else if (op == '-') {
                pos++;
                v -= product();
            } else break;
        }
        return v;
    }

    private double product() {
        double v = power();
        while (pos < s.length()) {
            char op = s.charAt(pos);
            if (op == '*') {
                pos++;
                v *= power();
            } else if (op == '/') {
                pos++;
                v /= power();
            } else if (op == '%') {
                pos++;
                v %= power();
            } else break;
        }
        return v;
    }

    private double power() {
        double v = unary();
        if (pos < s.length() && s.charAt(pos) == '^') {
            pos++;
            return Math.pow(v, power());
        }
        return v;
    }

    private double unary() {
        if (pos < s.length() && s.charAt(pos) == '-') {
            pos++;
            return -unary();
        }
        if (pos < s.length() && s.charAt(pos) == '(') {
            pos++;
            double v = sum();
            if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalArgumentException();
            pos++;
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        return Double.parseDouble(s.substring(start, pos));
    }

    /** "1733", or "0.3333" for a fraction. */
    public static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        String text = String.format(java.util.Locale.ROOT, "%.4f", v);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** "27 stacks + 5" for a whole number of items that fills at least one stack, else an empty string. */
    public static String stacks(double items) {
        if (items != Math.rint(items) || items < 64 || items > 1e9) return "";
        long n = (long) items, stacks = n / 64, rest = n % 64;
        String text = stacks + (stacks == 1 ? " stack" : " stacks") + (rest > 0 ? " + " + rest : "");
        if (stacks >= 27) {
            long boxes = stacks / 27, left = stacks % 27;
            text += "  ·  " + boxes + (boxes == 1 ? " shulker box" : " shulker boxes")
                    + (left > 0 || rest > 0 ? " + " + (left > 0 ? left + (left == 1 ? " stack" : " stacks") : "")
                    + (left > 0 && rest > 0 ? " + " : "") + (rest > 0 ? rest : "") : "");
        }
        return text;
    }
}
