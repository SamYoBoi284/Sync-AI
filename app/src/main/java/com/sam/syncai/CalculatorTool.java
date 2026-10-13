package com.sam.syncai;

import java.util.Map;

public final class CalculatorTool implements SyncTool {
    @Override public String getName() { return "calculator"; }

    @Override public String getDescription() {
        return "Evaluate a basic arithmetic expression using +, -, *, /, %, ^ and parentheses.";
    }

    @Override public String getInputSchema() {
        return "{\"expression\":\"(12+8)*3/2\"}";
    }

    @Override public String execute(Map<String, String> arguments) throws Exception {
        String expression = arguments.get("expression");
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing expression.");
        }
        double result = new Parser(expression).parse();
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException("Expression produced a non-finite result.");
        }
        if (result == Math.rint(result)) return Long.toString((long) result);
        return Double.toString(result);
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        double parse() {
            double value = parseExpression();
            skip();
            if (pos != s.length()) throw new IllegalArgumentException("Unexpected character at position " + pos + ".");
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skip();
                if (match('+')) value += parseTerm();
                else if (match('-')) value -= parseTerm();
                else return value;
            }
        }

        private double parseTerm() {
            double value = parsePower();
            while (true) {
                skip();
                if (match('*')) value *= parsePower();
                else if (match('/')) {
                    double divisor = parsePower();
                    if (divisor == 0) throw new IllegalArgumentException("Division by zero.");
                    value /= divisor;
                } else if (match('%')) {
                    double divisor = parsePower();
                    if (divisor == 0) throw new IllegalArgumentException("Modulo by zero.");
                    value %= divisor;
                } else return value;
            }
        }

        private double parsePower() {
            double value = parseUnary();
            skip();
            if (match('^')) value = Math.pow(value, parsePower());
            return value;
        }

        private double parseUnary() {
            skip();
            if (match('+')) return parseUnary();
            if (match('-')) return -parseUnary();
            return parsePrimary();
        }

        private double parsePrimary() {
            skip();
            if (match('(')) {
                double value = parseExpression();
                skip();
                if (!match(')')) throw new IllegalArgumentException("Missing closing parenthesis.");
                return value;
            }

            int start = pos;
            boolean dot = false;
            while (pos < s.length()) {
                char ch = s.charAt(pos);
                if (Character.isDigit(ch)) {
                    pos++;
                } else if (ch == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) throw new IllegalArgumentException("Expected a number at position " + pos + ".");
            return Double.parseDouble(s.substring(start, pos));
        }

        private boolean match(char expected) {
            skip();
            if (pos < s.length() && s.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
