package br.com.royal.pontos.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Hash de senhas com PBKDF2 (nunca guardamos a senha em texto). */
public final class Senha {

    private static final int ITERACOES = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Senha() {}

    public static String hash(String senha) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] h = pbkdf2(senha, salt, ITERACOES);
        return "pbkdf2$" + ITERACOES + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean confere(String senha, String armazenado) {
        if (senha == null || armazenado == null) return false;
        String[] p = armazenado.split("\\$");
        if (p.length != 4) return false;
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] esperado = Base64.getDecoder().decode(p[3]);
        byte[] h = pbkdf2(senha, salt, Integer.parseInt(p[1]));
        return MessageDigest.isEqual(esperado, h);
    }

    private static byte[] pbkdf2(String senha, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Código curto e legível para cupons e comprovantes (sem 0/O/1/I). */
    public static String codigo(String prefixo) {
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(prefixo).append('-');
        for (int i = 0; i < 6; i++) sb.append(alfabeto.charAt(RANDOM.nextInt(alfabeto.length())));
        return sb.toString();
    }
}
