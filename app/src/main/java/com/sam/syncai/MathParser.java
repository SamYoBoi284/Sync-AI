package com.sam.syncai;

import java.util.Locale;

public final class MathParser {
    private MathParser() {}

    public static double evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("No arithmetic expression found.");
        }
        Parser p = new Parser(expression);
        double value = p.parseExpression();
        p.skip();
        if (p.pos != p.text.length()) {
            throw new IllegalArgumentException("I couldn't parse that calculation.");
        }
        return value;
    }

    public static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "undefined";
        if (Math.abs(value - Math.rint(value)) < 1e-10) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static final class Parser {
        final String text;
        int pos;

        Parser(String raw) {
            text = raw.replaceAll("\\s+", "");
        }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                skip();
                if (eat('+')) value += parseTerm();
                else if (eat('-')) value -= parseTerm();
                else return value;
            }
        }

        double parseTerm() {
            double value = parseFactor();
            while (true) {
                skip();
                if (eat('*')) value *= parseFactor();
                else if (eat('/')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-12) throw new IllegalArgumentException("Division by zero.");
                    value /= divisor;
                } else if (eat('%')) value %= parseFactor();
                else return value;
            }
        }

        double parseFactor() {
            skip();
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();
            if (eat('(')) {
                double value = parseExpression();
                if (!eat(')')) throw new IllegalArgumentException("Missing closing parenthesis.");
                return value;
            }
            int start = pos;
            while (pos < text.length() &&
                    (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("Expected a number.");
            return Double.parseDouble(text.substring(start, pos));
        }

        boolean eat(char c) {
            skip();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }
    }
}
