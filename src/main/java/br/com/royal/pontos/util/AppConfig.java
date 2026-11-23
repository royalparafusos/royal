package br.com.royal.pontos.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuração local do computador (arquivo config.properties em
 * Documentos do usuário/RoyalPontos). Guarda só a conexão com o banco;
 * as regras do programa ficam no próprio banco, compartilhadas.
 */
public final class AppConfig {

    public static final Path PASTA = Path.of(System.getProperty("user.home"), "RoyalPontos");
    private static final Path ARQUIVO = PASTA.resolve("config.properties");

    private final Properties p = new Properties();

    public static AppConfig carregar() {
        AppConfig c = new AppConfig();
        try {
            Files.createDirectories(PASTA);
            if (Files.exists(ARQUIVO)) {
                try (Reader r = Files.newBufferedReader(ARQUIVO, StandardCharsets.UTF_8)) {
                    c.p.load(r);
                }
            }
        } catch (IOException ignored) {
        }
        return c;
    }

    public void salvar() throws IOException {
        Files.createDirectories(PASTA);
        try (Writer w = Files.newBufferedWriter(ARQUIVO, StandardCharsets.UTF_8)) {
            p.store(w, "Royal Pontos - conexao com o banco de dados");
        }
    }

    public boolean isNuvem() { return "nuvem".equals(p.getProperty("db.tipo", "local")); }
    public void setNuvem(boolean nuvem) { p.setProperty("db.tipo", nuvem ? "nuvem" : "local"); }

    public String host() { return p.getProperty("db.host", ""); }
    public String porta() { return p.getProperty("db.porta", "5432"); }
    public String banco() { return p.getProperty("db.banco", "postgres"); }
    public String usuario() { return p.getProperty("db.usuario", ""); }
    public String senha() { return p.getProperty("db.senha", ""); }

    public void setNuvem(String host, String porta, String banco, String usuario, String senha) {
        p.setProperty("db.host", host.trim());
        p.setProperty("db.porta", porta.trim());
        p.setProperty("db.banco", banco.trim());
        p.setProperty("db.usuario", usuario.trim());
        p.setProperty("db.senha", senha);
    }

    /** O usuário escolheu de propósito usar só este computador (modo demonstração). */
    public boolean demoEscolhido() { return "sim".equals(p.getProperty("db.demo", "")); }
    public void setDemoEscolhido(boolean v) { p.setProperty("db.demo", v ? "sim" : ""); }

    /** Banco local (H2) deste computador, usado no modo demonstração. */
    public static Path arquivoLocal() { return PASTA.resolve("dados").resolve("royalpontos.mv.db"); }

    public static String jdbcUrlLocal() {
        Path arquivo = PASTA.resolve("dados").resolve("royalpontos");
        return "jdbc:h2:file:" + arquivo.toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE";
    }

    public String jdbcUrl() {
        if (isNuvem()) {
            return "jdbc:postgresql://" + host() + ":" + porta() + "/" + banco()
                    + "?sslmode=require&prepareThreshold=0&connectTimeout=15";
        }
        Path arquivo = PASTA.resolve("dados").resolve("royalpontos");
        return "jdbc:h2:file:" + arquivo.toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE";
    }
}
