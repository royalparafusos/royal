package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Configurações do programa guardadas no banco (valem para todos os computadores). */
public final class Config {

    public static final String LOJA_NOME = "loja_nome";
    public static final String LINK_CONSULTA = "link_consulta";
    public static final String REGRA_REAIS = "regra_reais";
    public static final String REGRA_PONTOS = "regra_pontos";
    public static final String VALOR_MINIMO = "valor_minimo";
    public static final String VALIDADE_DIAS = "validade_dias";
    public static final String MODO_RESGATE = "modo_resgate";       // PRODUTOS | DESCONTO | AMBOS
    public static final String DESCONTO_PONTOS = "desconto_pontos";
    public static final String DESCONTO_REAIS = "desconto_reais";
    public static final String BLING_CLIENT_ID = "bling_client_id";
    public static final String BLING_CLIENT_SECRET = "bling_client_secret";
    public static final String BLING_ACCESS_TOKEN = "bling_access_token";
    public static final String BLING_REFRESH_TOKEN = "bling_refresh_token";
    public static final String BLING_TOKEN_EXPIRA = "bling_token_expira";
    public static final String BLING_PONTOS_POR_REAL = "bling_pontos_por_real";
    public static final String BLING_IGNORAR_SITUACOES = "bling_ignorar_situacoes";
    public static final String BLING_AUTO_IMPORTAR = "bling_auto_importar";
    public static final String BLING_AUTO_CADASTRO = "bling_auto_cadastro";
    public static final String BLING_ULTIMA_IMPORTACAO = "bling_ultima_importacao";

    private static final Map<String, String> PADROES = Map.ofEntries(
            Map.entry(LOJA_NOME, "Royal Parafusos e EPIs"),
            Map.entry(LINK_CONSULTA, "https://royalparafusos.github.io/royal/"),
            Map.entry(REGRA_REAIS, "1.00"),
            Map.entry(REGRA_PONTOS, "1"),
            Map.entry(VALOR_MINIMO, "0.00"),
            Map.entry(VALIDADE_DIAS, "60"),
            Map.entry(MODO_RESGATE, "AMBOS"),
            Map.entry(DESCONTO_PONTOS, "100"),
            Map.entry(DESCONTO_REAIS, "5.00"),
            Map.entry(BLING_PONTOS_POR_REAL, "20"),
            Map.entry(BLING_IGNORAR_SITUACOES, "12"),
            Map.entry(BLING_AUTO_IMPORTAR, "false"),
            Map.entry(BLING_AUTO_CADASTRO, "false")
    );

    private static final Map<String, String> cache = new HashMap<>();

    private Config() {}

    public static synchronized void recarregar() {
        cache.clear();
        List<String[]> linhas = Db.lista("SELECT chave, valor FROM configuracoes",
                rs -> new String[]{rs.getString(1), rs.getString(2)});
        for (String[] l : linhas) cache.put(l[0], l[1]);
    }

    public static synchronized String get(String chave) {
        String v = cache.get(chave);
        if (v == null) v = PADROES.getOrDefault(chave, "");
        return v;
    }

    public static synchronized void set(String chave, String valor) {
        Db.tx(c -> {
            int n = Db.executar(c, "UPDATE configuracoes SET valor = ? WHERE chave = ?", valor, chave);
            if (n == 0) Db.executar(c, "INSERT INTO configuracoes(chave, valor) VALUES (?, ?)", chave, valor);
            return null;
        });
        cache.put(chave, valor);
    }

    public static int inteiro(String chave) {
        try { return Integer.parseInt(get(chave).trim()); } catch (Exception e) { return Integer.parseInt(PADROES.getOrDefault(chave, "0")); }
    }

    public static BigDecimal decimal(String chave) {
        try { return new BigDecimal(get(chave).trim()); } catch (Exception e) { return new BigDecimal(PADROES.getOrDefault(chave, "0")); }
    }

    public static boolean bool(String chave) { return Boolean.parseBoolean(get(chave)); }

    public static boolean aceitaProdutos() { return !"DESCONTO".equals(get(MODO_RESGATE)); }

    public static boolean aceitaDesconto() { return !"PRODUTOS".equals(get(MODO_RESGATE)); }

    /** Grava os valores padrão que ainda não existem (a página do cliente lê do banco). */
    public static void garantirPadroes() {
        recarregar();
        for (var e : PADROES.entrySet()) {
            if (!cache.containsKey(e.getKey())) set(e.getKey(), e.getValue());
        }
        // link da consulta ficou vazio nas versões antigas: passa a usar o app do cliente publicado
        if (get(LINK_CONSULTA).isBlank()) set(LINK_CONSULTA, PADROES.get(LINK_CONSULTA));
    }
}
