package io.github.pixelsuite;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchCalc {
    private SearchCalc() {}

    static String digits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c >= '०' && c <= '९') c = (char) ('0' + (c - '०'));
            else if (c >= '০' && c <= '৯') c = (char) ('0' + (c - '০'));
            else if (c == '٫') c = '.';
            else if (c == '×' || c == '✕') c = '*';
            else if (c == '÷') c = '/';
            else if (c == '−') c = '-';
            b.append(c);
        }
        return b.toString();
    }

    static String calculate(String raw) {
        String s = digits(raw).trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || !s.matches("[0-9.,+\\-*/^%()a-z√π]+")) return null;
        boolean op = false;
        for (int i = 1; i < s.length(); i++) { char c = s.charAt(i); if ("+-*/^%".indexOf(c) >= 0) op = true; }
        if (s.matches(".*(sqrt|sin|cos|tan|log|ln|√|π).*")) op = true;
        if (!op) return null;
        if (s.matches("[0-9]+(-[0-9]+)+") ) return null;
        try {
            Parser p = new Parser(s.replace(",", ""));
            double v = p.expr();
            if (p.pos != p.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return format(v);
        } catch (Throwable t) { return null; }
    }

    static String format(double v) {
        if (Math.abs(v) < 1e-12) v = 0;
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.format(Locale.ROOT, "%,d", (long) v);
        BigDecimal d = new BigDecimal(v).round(new MathContext(10)).stripTrailingZeros();
        if (Math.abs(v) >= 1e15 || Math.abs(v) < 1e-6) return d.toString();
        String plain = d.toPlainString();
        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        String frac = dot < 0 ? "" : plain.substring(dot);
        boolean neg = whole.startsWith("-");
        if (neg) whole = whole.substring(1);
        try { whole = String.format(Locale.ROOT, "%,d", Long.parseLong(whole)); } catch (Throwable ignored) { }
        return (neg ? "-" : "") + whole + frac;
    }

    private static final class Parser {
        final String s; int pos;
        Parser(String s) { this.s = s; }
        boolean eat(char c) { if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; } return false; }
        boolean pct; double pctRaw;
        double expr() {
            double v = term();
            while (true) {
                if (eat('+')) { pct = false; double t = term(); if (pct) t = v * pctRaw / 100; v += t; }
                else if (eat('-')) { pct = false; double t = term(); if (pct) t = v * pctRaw / 100; v -= t; }
                else return v;
            }
        }
        double term() {
            double v = power();
            while (true) {
                if (eat('*')) v *= power();
                else if (eat('/')) v /= power();
                else if (pos < s.length() && (s.charAt(pos) == '(' || Character.isLetter(s.charAt(pos)) || s.charAt(pos) == '√' || s.charAt(pos) == 'π')) v *= power();
                else return v;
            }
        }
        double power() {
            double b = unary();
            if (eat('^')) return Math.pow(b, power());
            return b;
        }
        double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            double v = atom();
            if (eat('%')) { pct = true; pctRaw = v; v /= 100; }
            return v;
        }
        double atom() {
            if (eat('(')) { double v = expr(); eat(')'); return v; }
            if (eat('√')) return Math.sqrt(unary());
            if (eat('π')) return Math.PI;
            int start = pos;
            if (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
                String f = s.substring(start, pos);
                if (f.equals("pi")) return Math.PI;
                if (f.equals("e")) return Math.E;
                double a = unary();
                if (f.equals("sqrt")) return Math.sqrt(a);
                if (f.equals("sin")) return Math.sin(Math.toRadians(a));
                if (f.equals("cos")) return Math.cos(Math.toRadians(a));
                if (f.equals("tan")) return Math.tan(Math.toRadians(a));
                if (f.equals("log")) return Math.log10(a);
                if (f.equals("ln")) return Math.log(a);
                throw new IllegalArgumentException(f);
            }
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("number");
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    private static final HashMap<String, double[]> UNITS = new HashMap<String, double[]>();
    private static final HashMap<String, String> NAMES = new HashMap<String, String>();

    private static void unit(int kind, double factor, String name, String... aliases) {
        UNITS.put(name, new double[]{kind, factor});
        NAMES.put(name, name);
        for (String a : aliases) { UNITS.put(a, new double[]{kind, factor}); NAMES.put(a, name); }
    }

    static {
        unit(1, 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres");
        unit(1, 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres");
        unit(1, 1, "m", "meter", "meters", "metre", "metres");
        unit(1, 1000, "km", "kilometer", "kilometers", "kilometre", "kilometres", "kms");
        unit(1, 0.0254, "in", "inch", "inches", "\"");
        unit(1, 0.3048, "ft", "foot", "feet", "'");
        unit(1, 0.9144, "yd", "yard", "yards");
        unit(1, 1609.344, "mi", "mile", "miles");
        unit(1, 1852, "nmi", "nautical mile", "nautical miles");
        unit(2, 0.000001, "mg", "milligram", "milligrams");
        unit(2, 0.001, "g", "gram", "grams", "gr");
        unit(2, 1, "kg", "kilo", "kilos", "kilogram", "kilograms", "kgs");
        unit(2, 1000, "t", "ton", "tons", "tonne", "tonnes");
        unit(2, 0.028349523125, "oz", "ounce", "ounces");
        unit(2, 0.45359237, "lb", "lbs", "pound", "pounds");
        unit(2, 6.35029318, "st", "stone", "stones");
        unit(3, 0.001, "ml", "milliliter", "milliliters", "millilitre", "millilitres");
        unit(3, 1, "l", "liter", "liters", "litre", "litres", "ltr");
        unit(3, 3.785411784, "gal", "gallon", "gallons");
        unit(3, 0.946352946, "qt", "quart", "quarts");
        unit(3, 0.473176473, "pt", "pint", "pints");
        unit(3, 0.2365882365, "cup", "cups");
        unit(3, 0.0295735295625, "fl oz", "floz", "fluid ounce", "fluid ounces");
        unit(3, 0.01478676478125, "tbsp", "tablespoon", "tablespoons");
        unit(3, 0.00492892159375, "tsp", "teaspoon", "teaspoons");
        unit(5, 1 / 3.6, "km/h", "kmh", "kph", "kmph");
        unit(5, 0.44704, "mph", "mi/h");
        unit(5, 1, "m/s", "mps");
        unit(5, 0.514444, "kn", "knot", "knots");
        unit(6, 1, "m²", "m2", "sqm", "square meter", "square meters");
        unit(6, 1000000, "km²", "km2", "square kilometer", "square kilometers");
        unit(6, 0.09290304, "ft²", "ft2", "sqft", "square foot", "square feet");
        unit(6, 4046.8564224, "acre", "acres");
        unit(6, 10000, "ha", "hectare", "hectares");
        unit(7, 1, "B", "byte", "bytes");
        unit(7, 1000, "KB", "kb", "kilobyte", "kilobytes");
        unit(7, 1000000, "MB", "mb", "megabyte", "megabytes");
        unit(7, 1e9, "GB", "gb", "gigabyte", "gigabytes");
        unit(7, 1e12, "TB", "tb", "terabyte", "terabytes");
        unit(7, 1024, "KiB", "kib");
        unit(7, 1048576, "MiB", "mib");
        unit(7, 1073741824, "GiB", "gib");
        unit(8, 1, "s", "sec", "secs", "second", "seconds");
        unit(8, 60, "min", "mins", "minute", "minutes");
        unit(8, 3600, "h", "hr", "hrs", "hour", "hours");
        unit(8, 86400, "day", "days", "d");
        unit(8, 604800, "week", "weeks", "wk");
        unit(8, 31557600, "year", "years", "yr");
        unit(4, 1, "°C", "c", "celsius", "°c", "degc");
        unit(4, 1, "°F", "f", "fahrenheit", "°f", "degf");
        unit(4, 1, "K", "k", "kelvin");
    }

    private static final Pattern CONVERT = Pattern.compile("^\\s*(-?[0-9][0-9,]*(?:\\.[0-9]+)?|-?\\.[0-9]+)\\s*([^0-9]+?)\\s+(?:to|in|into|as|=|->|→|إلى|الى|بـ|به|zu|en|a|para|в|na|ke|に|转换为|换成)\\s+(.+?)\\s*$");

    static String convert(String raw) {
        Matcher m = CONVERT.matcher(digits(raw).toLowerCase(Locale.ROOT));
        if (!m.find()) return null;
        String from = unitName(m.group(2)), to = unitName(m.group(3));
        if (from == null || to == null) return null;
        double[] a = UNITS.get(from), b = UNITS.get(to);
        if (a == null || b == null || a[0] != b[0] || from.equals(to)) return null;
        double v;
        try { v = Double.parseDouble(m.group(1).replace(",", "")); } catch (Throwable t) { return null; }
        double r;
        if (a[0] == 4) {
            double c = from.equals("°C") ? v : from.equals("°F") ? (v - 32) * 5 / 9 : v - 273.15;
            r = to.equals("°C") ? c : to.equals("°F") ? c * 9 / 5 + 32 : c + 273.15;
        } else r = v * a[1] / b[1];
        return format(v) + " " + NAMES.get(from) + " = " + format(r) + " " + NAMES.get(to);
    }

    private static String unitName(String s) {
        s = s.trim().replace("degrees ", "").replace("degree ", "");
        if (UNITS.containsKey(s)) return NAMES.get(s);
        String u = s.replace(" ", "");
        if (UNITS.containsKey(u)) return NAMES.get(u);
        if (s.endsWith(".")) { s = s.substring(0, s.length() - 1); if (UNITS.containsKey(s)) return NAMES.get(s); }
        return null;
    }
}
