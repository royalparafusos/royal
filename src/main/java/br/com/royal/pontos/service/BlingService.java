package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.db.DbException;
import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.util.Formatos;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Integração com o Bling (API v3, OAuth 2.0).
 *  - Importa pedidos de venda e credita pontos pelo CPF do cliente.
 *  - Lista produtos para virarem prêmios do catálogo.
 */
public final class BlingService {

    private static final String API = "https://api.bling.com.br/Api/v3/";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private BlingService() {}

    public record Produto(String id, String nome, String codigo, BigDecimal preco, String imagem) {
        public int pontos() {
            return preco.multiply(Config.decimal(Config.BLING_PONTOS_POR_REAL)).setScale(0, RoundingMode.CEILING).intValue();
        }
    }

    public record ResumoImportacao(int pedidos, int creditados, long pontos, int semCliente, int jaImportados, int ignorados) {}

    public static boolean conectado() {
        return !Config.get(Config.BLING_REFRESH_TOKEN).isBlank();
    }

    public static String urlAutorizacao() {
        String id = Config.get(Config.BLING_CLIENT_ID);
        if (id.isBlank()) throw new DbException("Informe o Client ID do aplicativo Bling.");
        return "https://www.bling.com.br/Api/v3/oauth/authorize?response_type=code&client_id="
                + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&state=royalpontos";
    }

    // ------------------------------------------------------------------ OAuth

    /** Troca o código de autorização (copiado do navegador) pelos tokens. */
    public static void conectarComCodigo(String codigo) {
        String c = codigo.trim();
        int i = c.indexOf("code=");
        if (i >= 0) { c = c.substring(i + 5); int amp = c.indexOf('&'); if (amp >= 0) c = c.substring(0, amp); }
        pedirToken("grant_type=authorization_code&code=" + URLEncoder.encode(c, StandardCharsets.UTF_8));
    }

    private static synchronized String token() {
        String atual = Config.get(Config.BLING_ACCESS_TOKEN);
        String expira = Config.get(Config.BLING_TOKEN_EXPIRA);
        if (!atual.isBlank() && !expira.isBlank() && LocalDateTime.parse(expira).isAfter(LocalDateTime.now().plusMinutes(5))) {
            return atual;
        }
        String refresh = Config.get(Config.BLING_REFRESH_TOKEN);
        if (refresh.isBlank()) throw new DbException("O Bling ainda não está conectado. Vá em Bling > Conexão.");
        pedirToken("grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8));
        return Config.get(Config.BLING_ACCESS_TOKEN);
    }

    private static void pedirToken(String corpo) {
        String id = Config.get(Config.BLING_CLIENT_ID), segredo = Config.get(Config.BLING_CLIENT_SECRET);
        if (id.isBlank() || segredo.isBlank()) throw new DbException("Informe o Client ID e o Client Secret do aplicativo Bling.");
        String basic = Base64.getEncoder().encodeToString((id + ":" + segredo).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "oauth/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "1.0")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        JsonObject j = enviar(req);
        if (!j.has("access_token")) throw new DbException("O Bling recusou a autorização: " + j);
        Config.set(Config.BLING_ACCESS_TOKEN, j.get("access_token").getAsString());
        if (j.has("refresh_token")) Config.set(Config.BLING_REFRESH_TOKEN, j.get("refresh_token").getAsString());
        long seg = j.has("expires_in") ? j.get("expires_in").getAsLong() : 3600;
        Config.set(Config.BLING_TOKEN_EXPIRA, LocalDateTime.now().plusSeconds(seg).toString());
    }

    private static JsonObject get(String caminho) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + caminho))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json").GET().build();
        try { Thread.sleep(350); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } // limite: 3 req/s
        return enviar(req);
    }

    private static JsonObject enviar(HttpRequest req) {
        try {
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() == 429) {
                Thread.sleep(1500);
                r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
            if (r.statusCode() == 401) {
                Config.set(Config.BLING_TOKEN_EXPIRA, "");
                throw new DbException("Acesso ao Bling expirou. Reconecte em Bling > Conexão.");
            }
            if (r.statusCode() >= 400) throw new DbException("Bling respondeu erro " + r.statusCode() + ": " + r.body());
            String body = r.body() == null || r.body().isBlank() ? "{}" : r.body();
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (DbException e) {
            throw e;
        } catch (Exception e) {
            throw new DbException("Não foi possível falar com o Bling: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ vendas

    public static ResumoImportacao importarVendas(LocalDate inicio, LocalDate fim, Consumer<String> log) {
        Set<String> ignorar = new HashSet<>();
        for (String s : Config.get(Config.BLING_IGNORAR_SITUACOES).split("[,; ]+")) if (!s.isBlank()) ignorar.add(s.trim());
        boolean autoCadastro = Config.bool(Config.BLING_AUTO_CADASTRO);
        Map<String, JsonObject> cacheContatos = new HashMap<>();
        int pedidos = 0, creditados = 0, semCliente = 0, ja = 0, ignorados = 0;
        long pontos = 0;

        log.accept("Buscando pedidos de " + Formatos.data(inicio) + " a " + Formatos.data(fim) + "...");
        for (int pagina = 1; pagina < 200; pagina++) {
            JsonArray dados = arr(get("pedidos/vendas?pagina=" + pagina + "&limite=100&dataInicial=" + inicio + "&dataFinal=" + fim), "data");
            if (dados.isEmpty()) break;
            for (JsonElement el : dados) {
                JsonObject p = el.getAsJsonObject();
                pedidos++;
                String idPedido = str(p, "id");
                String numero = str(p, "numero");
                String situacao = p.has("situacao") && p.get("situacao").isJsonObject() ? str(p.getAsJsonObject("situacao"), "id") : "";
                if (ignorar.contains(situacao)) { ignorados++; continue; }
                if (Db.numero("SELECT COUNT(*) FROM movimentos WHERE bling_pedido_id = ?", idPedido) > 0) { ja++; continue; }

                BigDecimal total = dec(p, "total");
                JsonObject contato = p.has("contato") && p.get("contato").isJsonObject() ? p.getAsJsonObject("contato") : new JsonObject();
                String doc = Formatos.digitos(str(contato, "numeroDocumento"));
                if (doc.isEmpty() && !str(contato, "id").isEmpty()) {
                    JsonObject full = contato(str(contato, "id"), cacheContatos);
                    doc = Formatos.digitos(str(full, "numeroDocumento"));
                }
                Cliente cli = doc.length() == 11 ? ClienteService.porCpf(doc) : null;
                if (cli == null && autoCadastro && Formatos.cpfValido(doc)) {
                    cli = cadastrarDoBling(str(contato, "id"), doc, cacheContatos);
                    if (cli != null) log.accept("  + Cliente cadastrado do Bling: " + cli.nome());
                }
                if (cli == null) { semCliente++; continue; }

                LocalDate dataPedido = parseData(str(p, "data"), LocalDate.now());
                if (PontosService.validadePara(dataPedido).isBefore(LocalDate.now())) { ignorados++; continue; }
                int pts = PontosService.calcular(total);
                if (pts <= 0) { ignorados++; continue; }
                final Cliente alvo = cli;
                try {
                    Db.tx(c -> PontosService.creditar(c, alvo.id(), total, pts, "Pedido Bling nº " + numero, "BLING", idPedido,
                            dataPedido.atTime(12, 0)));
                } catch (br.com.royal.pontos.db.DbException dup) {
                    // outro computador da loja importou este pedido ao mesmo tempo
                    if (Db.numero("SELECT COUNT(*) FROM movimentos WHERE bling_pedido_id = ?", idPedido) > 0) { ja++; continue; }
                    throw dup;
                }
                creditados++;
                pontos += pts;
                log.accept("  ✓ Pedido " + numero + " · " + alvo.nome() + " · " + Formatos.moeda(total) + " → " + Formatos.pontos(pts));
            }
            if (dados.size() < 100) break;
        }
        Config.set(Config.BLING_ULTIMA_IMPORTACAO, fim.toString());
        log.accept("Concluído: " + creditados + " pedido(s) pontuado(s), " + Formatos.pontos(pontos) + " creditados. "
                + semCliente + " sem cliente no programa, " + ja + " já importados, " + ignorados + " ignorados.");
        return new ResumoImportacao(pedidos, creditados, pontos, semCliente, ja, ignorados);
    }

    /** Importação automática desde a última vez (usada ao abrir o programa). */
    public static ResumoImportacao importarPendentes(Consumer<String> log) {
        String ultima = Config.get(Config.BLING_ULTIMA_IMPORTACAO);
        LocalDate inicio = ultima.isBlank() ? LocalDate.now().minusDays(1) : LocalDate.parse(ultima);
        return importarVendas(inicio, LocalDate.now(), log);
    }

    private static JsonObject contato(String id, Map<String, JsonObject> cache) {
        return cache.computeIfAbsent(id, k -> {
            JsonObject r = get("contatos/" + k);
            return r.has("data") && r.get("data").isJsonObject() ? r.getAsJsonObject("data") : new JsonObject();
        });
    }

    private static Cliente cadastrarDoBling(String contatoId, String cpf, Map<String, JsonObject> cache) {
        if (contatoId.isEmpty()) return null;
        JsonObject c = contato(contatoId, cache);
        String tel = Formatos.digitos(str(c, "celular"));
        if (tel.length() < 10) tel = Formatos.digitos(str(c, "telefone"));
        if (tel.length() < 10) return null;
        String nome = str(c, "nome");
        String email = str(c, "email");
        String telefone = tel;
        long id = Db.tx(con -> ClienteService.inserir(con, nome, cpf, telefone, email, null, true));
        return ClienteService.porId(id);
    }

    // ------------------------------------------------------------------ produtos

    public static List<Produto> produtos(String busca) {
        List<Produto> r = new ArrayList<>();
        String filtro = busca == null || busca.isBlank() ? "" : "&nome=" + URLEncoder.encode(busca.trim(), StandardCharsets.UTF_8);
        for (int pagina = 1; pagina <= 5; pagina++) {
            JsonArray dados = arr(get("produtos?pagina=" + pagina + "&limite=100&criterio=2" + filtro), "data");
            for (JsonElement el : dados) {
                JsonObject p = el.getAsJsonObject();
                String img = str(p, "imagemURL");
                r.add(new Produto(str(p, "id"), str(p, "nome"), str(p, "codigo"), dec(p, "preco"), img.isBlank() ? null : img));
            }
            if (dados.size() < 100) break;
        }
        return r;
    }

    // ------------------------------------------------------------------ JSON helpers

    private static JsonArray arr(JsonObject o, String campo) {
        return o.has(campo) && o.get(campo).isJsonArray() ? o.getAsJsonArray(campo) : new JsonArray();
    }

    private static String str(JsonObject o, String campo) {
        if (o == null || !o.has(campo) || o.get(campo).isJsonNull()) return "";
        JsonElement e = o.get(campo);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static BigDecimal dec(JsonObject o, String campo) {
        try { return new BigDecimal(str(o, campo)).setScale(2, RoundingMode.HALF_UP); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static LocalDate parseData(String s, LocalDate padrao) {
        try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); } catch (Exception e) { return padrao; }
    }
}
