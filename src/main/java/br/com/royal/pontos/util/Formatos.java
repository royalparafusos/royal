package br.com.royal.pontos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação e validação de dados brasileiros (CPF, telefone, moeda, datas). */
public final class Formatos {

    public static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Formatos() {}

    public static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    public static boolean cpfValido(String cpf) {
        String c = digitos(cpf);
        if (c.length() != 11 || c.chars().distinct().count() == 1) return false;
        for (int t = 9; t < 11; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) soma += (c.charAt(i) - '0') * (t + 1 - i);
            int dv = (soma * 10) % 11 % 10;
            if (dv != c.charAt(t) - '0') return false;
        }
        return true;
    }

    public static String cpf(String cpf) {
        String c = digitos(cpf);
        if (c.length() != 11) return cpf == null ? "" : cpf;
        return c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9);
    }

    public static String telefone(String tel) {
        String t = digitos(tel);
        if (t.length() == 11) return "(" + t.substring(0, 2) + ") " + t.substring(2, 7) + "-" + t.substring(7);
        if (t.length() == 10) return "(" + t.substring(0, 2) + ") " + t.substring(2, 6) + "-" + t.substring(6);
        return tel == null ? "" : tel;
    }

    public static String moeda(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(BR).format(v == null ? BigDecimal.ZERO : v).replace(' ', ' ');
    }

    public static String numero(long n) {
        return NumberFormat.getIntegerInstance(BR).format(n);
    }

    public static String pontos(long n) {
        return numero(n) + " pts";
    }

    public static String data(LocalDate d) {
        return d == null ? "" : DATA.format(d);
    }

    public static String dataHora(LocalDateTime d) {
        return d == null ? "" : DATA_HORA.format(d);
    }

    /** Converte "1.234,56" ou "1234.56" em BigDecimal. Retorna null se inválido. */
    public static BigDecimal valor(String s) {
        if (s == null) return null;
        String v = s.replace("R$", "").replace(" ", "").trim();
        if (v.isEmpty()) return null;
        if (v.contains(",")) v = v.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String valorTexto(BigDecimal v) {
        if (v == null) return "";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        String p = nome.trim().split("\\s+")[0];
        return p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase();
    }

    public static String iniciais(String nome) {
        String limpo = nome == null ? "" : nome.replaceAll("[^\\p{L} ]", " ").trim();
        if (limpo.isBlank()) return "?";
        String[] p = limpo.split("\\s+");
        String r = p[0].substring(0, 1);
        if (p.length > 1) r += p[p.length - 1].substring(0, 1);
        return r.toUpperCase();
    }
}
