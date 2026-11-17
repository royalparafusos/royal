package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.util.AppConfig;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;

/**
 * "Lembrar usuário e senha" neste computador.
 * A senha NÃO é guardada: fica salvo um código aleatório (token) que só vale para este computador.
 * O banco guarda apenas o resumo (hash) do código. Trocar a senha ou desmarcar a opção cancela o código.
 */
public final class LembrarLogin {

    private static final Path ARQUIVO = AppConfig.PASTA.resolve("lembrar.properties");

    private LembrarLogin() {}

    private static Properties ler() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(ARQUIVO, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception ignored) {
        }
        return p;
    }

    /** Login salvo neste computador, ou null. */
    public static String loginSalvo() {
        Properties p = ler();
        return p.getProperty("login") != null && p.getProperty("token") != null ? p.getProperty("login") : null;
    }

    public static Usuario autenticarSalvo() {
        Properties p = ler();
        String token = p.getProperty("token");
        if (token == null) return null;
        Long id = Db.um("SELECT usuario_id FROM lembrar_login WHERE token_hash = ?", rs -> rs.getLong(1), hash(token));
        if (id == null) return null;
        return Db.um("SELECT * FROM usuarios WHERE id = ? AND ativo = TRUE",
                rs -> new Usuario(rs.getLong("id"), rs.getString("nome"), rs.getString("login"), rs.getString("perfil"), rs.getBoolean("ativo")), id);
    }

    public static void lembrar(Usuario u) {
        esquecer();
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Db.executar("INSERT INTO lembrar_login(token_hash, usuario_id, criado_em) VALUES (?,?,?)", hash(token), u.id(), LocalDateTime.now());
        Properties p = new Properties();
        p.setProperty("login", u.login());
        p.setProperty("token", token);
        try {
            Files.createDirectories(AppConfig.PASTA);
            try (Writer w = Files.newBufferedWriter(ARQUIVO, StandardCharsets.UTF_8)) {
                p.store(w, "Royal Pontos - login lembrado neste computador");
            }
        } catch (Exception ignored) {
        }
    }

    public static void esquecer() {
        String token = ler().getProperty("token");
        if (token != null) {
            try { Db.executar("DELETE FROM lembrar_login WHERE token_hash = ?", hash(token)); } catch (Exception ignored) { }
        }
        try { Files.deleteIfExists(ARQUIVO); } catch (Exception ignored) { }
    }

    /** Cancela todos os logins lembrados do usuário (ex.: ao trocar a senha). */
    public static void cancelarDoUsuario(long usuarioId) {
        Db.executar("DELETE FROM lembrar_login WHERE usuario_id = ?", usuarioId);
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
