package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Premio;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

/**
 * Coloca automaticamente a foto nos prêmios que ainda não têm imagem.
 * As fotos ficam publicadas em royalparafusos.github.io/royal/premios (lista em imagens.json),
 * identificadas pelo código de barras; o prêmio é reconhecido pelo produto do Bling ou pelo nome.
 */
public final class FotosPremios {

    public static final String BASE = "https://royalparafusos.github.io/royal/";

    private FotosPremios() {}

    private record Item(String imagem, Set<String> bling, List<List<String>> palavras) {}

    /** Retorna quantos prêmios receberam foto. */
    public static int aplicar() throws Exception {
        List<Premio> semFoto = PremioService.listar(true, "").stream()
                .filter(p -> p.imagem() == null || p.imagem().isBlank()).toList();
        if (semFoto.isEmpty()) return 0;
        List<Item> itens = baixarLista();
        int n = 0;
        for (Premio p : semFoto) {
            Set<String> tokens = tokens(p.nome() + " " + (p.descricao() == null ? "" : p.descricao()));
            for (Item it : itens) {
                boolean porBling = p.blingProdutoId() != null && it.bling().contains(p.blingProdutoId());
                boolean porNome = !it.palavras().isEmpty() && it.palavras().stream().allMatch(g -> g.stream().anyMatch(tokens::contains));
                if (porBling || porNome) {
                    n += Db.executar("UPDATE premios SET imagem = ? WHERE id = ? AND (imagem IS NULL OR imagem = '')", BASE + it.imagem(), p.id());
                    break;
                }
            }
        }
        if (n > 0) Atualizador.log("Fotos adicionadas automaticamente em " + n + " prêmio(s)");
        return n;
    }

    private static List<Item> baixarLista() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(BASE + "premios/imagens.json?t=" + System.currentTimeMillis()))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return List.of();
        List<Item> lista = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("itens")) {
            JsonObject o = e.getAsJsonObject();
            Set<String> bling = new HashSet<>();
            if (o.has("bling")) for (JsonElement b : o.getAsJsonArray("bling")) bling.add(b.getAsString());
            List<List<String>> palavras = new ArrayList<>();
            if (o.has("palavras")) for (JsonElement g : o.getAsJsonArray("palavras")) {
                List<String> grupo = new ArrayList<>();
                for (JsonElement w : (JsonArray) g) grupo.add(normalizar(w.getAsString()).trim());
                palavras.add(grupo);
            }
            lista.add(new Item(o.get("imagem").getAsString(), bling, palavras));
        }
        return lista;
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Palavras do nome; "80 mm" vira "80mm" e "80", "(3000K)" vira "3000k" e "3000". */
    static Set<String> tokens(String texto) {
        String t = normalizar(texto).replaceAll("[^a-z0-9]+", " ").replaceAll("(\\d+) (mm|cm|m|k|w|g|kg|ml)\\b", "$1$2");
        Set<String> out = new HashSet<>();
        for (String w : t.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            out.add(w);
            String num = w.replaceAll("^(\\d+)[a-z]+$", "$1");
            if (!num.equals(w)) out.add(num);
        }
        return out;
    }
}
