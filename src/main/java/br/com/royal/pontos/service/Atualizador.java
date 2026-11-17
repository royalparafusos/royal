package br.com.royal.pontos.service;

import br.com.royal.pontos.Versao;
import br.com.royal.pontos.util.AppConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Atualização automática: consulta a última versão publicada no GitHub,
 * baixa em segundo plano para Documentos do usuário/RoyalPontos/versoes
 * e, na próxima abertura (ou ao clicar em "Reiniciar"), o programa já abre atualizado.
 */
public final class Atualizador {

    private static final Path PASTA = AppConfig.PASTA.resolve("versoes");
    private static final Pattern NOME = Pattern.compile("royal-pontos-(\\d+(?:\\.\\d+)*)\\.jar");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.ALWAYS).build();

    private Atualizador() {}

    /** Versão que está rodando agora (a instalada ou uma baixada). */
    public static String versaoEmUso() {
        String v = System.getProperty("royal.versao.baixada");
        return v != null ? v : Versao.ATUAL;
    }

    /** Verifica e baixa uma versão nova. Retorna a versão baixada, ou null se já está atualizado. */
    public static String verificarEBaixar() throws Exception {
        if (!Versao.repositorioConfigurado()) return null;
        log("Verificando atualizações (versão em uso " + versaoEmUso() + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + Versao.REPOSITORIO + "/releases/latest"))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "RoyalPontos")
                .timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return null;
        JsonObject rel = JsonParser.parseString(r.body()).getAsJsonObject();
        String versao = rel.get("tag_name").getAsString().replaceFirst("^[vV]", "");
        if (Versao.comparar(versao, versaoEmUso()) <= 0) return null;
        log("Versão nova disponível: " + versao);

        Path destino = PASTA.resolve("royal-pontos-" + versao + ".jar");
        if (Files.exists(destino)) return versao;
        String url = null;
        for (JsonElement a : rel.getAsJsonArray("assets")) {
            JsonObject o = a.getAsJsonObject();
            if (o.get("name").getAsString().endsWith(".jar")) { url = o.get("browser_download_url").getAsString(); break; }
        }
        if (url == null) return null;

        Files.createDirectories(PASTA);
        Path parcial = PASTA.resolve("royal-pontos-" + versao + ".jar.baixando");
        HttpResponse<Path> d = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "RoyalPontos")
                .timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofFile(parcial));
        if (d.statusCode() != 200 || !jarValido(parcial)) { Files.deleteIfExists(parcial); return null; }
        Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log("Versão " + versao + " baixada em " + destino);
        limparAntigas();
        return versao;
    }

    private static boolean jarValido(Path p) {
        try (JarFile j = new JarFile(p.toFile())) {
            return j.getEntry("br/com/royal/pontos/Launcher.class") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** A versão baixada mais nova que a instalada, se houver. */
    public static Path versaoBaixadaMaisNova() {
        if (!Files.isDirectory(PASTA)) return null;
        try (Stream<Path> s = Files.list(PASTA)) {
            return s.filter(p -> versaoDe(p) != null)
                    .filter(p -> Versao.comparar(versaoDe(p), Versao.ATUAL) > 0)
                    .max(Comparator.comparing(Atualizador::versaoDe, Versao::comparar))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static String versaoDe(Path p) {
        Matcher m = NOME.matcher(p.getFileName().toString());
        return m.matches() ? m.group(1) : null;
    }

    /** Abre outra versão do programa usando o mesmo Java embutido. */
    public static boolean abrirVersao(Path jar) {
        try {
            String javaHome = System.getProperty("java.home");
            File javaw = new File(javaHome, "bin/javaw.exe");
            if (!javaw.exists()) javaw = new File(javaHome, "bin/java");
            new ProcessBuilder(javaw.getAbsolutePath(), "-Droyal.versao.baixada=" + versaoDe(jar), "-Dfile.encoding=UTF-8",
                    "-cp", jar.toAbsolutePath().toString(), "br.com.royal.pontos.Inicio")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Fecha e abre o programa de novo (o Inicio carrega a versão mais nova baixada). */
    public static boolean reiniciar() {
        try {
            String exe = System.getProperty("jpackage.app-path");
            if (exe != null && new File(exe).exists()) {
                log("Reiniciando pelo executável: " + exe);
                new ProcessBuilder(exe).directory(new File(exe).getParentFile())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } else {
                Path nova = versaoBaixadaMaisNova();
                if (nova == null || !abrirVersao(nova)) { log("Não foi possível reiniciar automaticamente."); return false; }
            }
            javafx.application.Platform.exit();
            System.exit(0);
            return true;
        } catch (Exception e) {
            log("Falha ao reiniciar: " + e);
            return false;
        }
    }

    /** Registro simples em Documentos do usuário/RoyalPontos/atualizacao.log (para diagnóstico). */
    public static synchronized void log(String msg) {
        try {
            Files.createDirectories(AppConfig.PASTA);
            Files.writeString(AppConfig.PASTA.resolve("atualizacao.log"),
                    java.time.LocalDateTime.now().withNano(0) + "  " + msg + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /** Mantém só as 2 versões baixadas mais recentes. */
    private static void limparAntigas() {
        try (Stream<Path> s = Files.list(PASTA)) {
            List<Path> jars = s.filter(p -> versaoDe(p) != null)
                    .sorted(Comparator.comparing(Atualizador::versaoDe, Versao::comparar).reversed()).toList();
            for (int i = 2; i < jars.size(); i++) Files.deleteIfExists(jars.get(i));
        } catch (Exception ignored) {
        }
    }
}
