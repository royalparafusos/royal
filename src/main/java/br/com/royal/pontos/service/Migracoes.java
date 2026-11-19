package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;

import java.time.LocalDateTime;

/**
 * Ajustes que rodam uma única vez no banco, na primeira abertura de uma versão nova.
 * Cada ajuste grava uma marca em "configuracoes" para não rodar de novo.
 */
public final class Migracoes {

    private static final String CATALOGO_V1 = "migracao_catalogo_100pts_v1";

    private Migracoes() {}

    public static void aplicar() {
        if (Config.get(CATALOGO_V1).isBlank()) catalogoInicial();
    }

    /**
     * Catálogo de teste definido em 19/11/2026: brindes de 100 pontos (até R$ 10,00 de valor na loja,
     * produtos de markup alto), resgate somente em produtos.
     */
    private static void catalogoInicial() {
        Object[][] itens = {
                {"Espátula Inox Lisa 80 mm Sparta", "6.00"},
                {"Espátula Inox Lisa 100 mm Sparta", "7.00"},
                {"Espátula Inox Lisa 60 mm Sparta", "5.00"},
                {"Espátula Inox Lisa 50 mm Sparta", "5.00"},
                {"Espátula Plástica Dentada Condor", "5.00"},
                {"Espátula Plástica Lisa Condor", "5.00"},
                {"Luva PU Preta XG Kalipso", "5.00"},
                {"Fita Isolante 20 m x 19 mm Krona", "8.00"},
                {"Lâmpada LED 20 W Luz Amarela (3000K) Cristallux", "7.50"},
                {"Lâmpada LED 20 W Luz Branca (6500K) Cristallux", "7.50"},
                {"Cloro Tablete Genco 3 em 1 - 200 g", "10.00"},
        };
        Db.tx(c -> {
            for (Object[] i : itens) {
                String nome = (String) i[0];
                Long existe = Db.um(c, "SELECT id FROM premios WHERE LOWER(nome) = LOWER(?)", rs -> rs.getLong(1), nome);
                if (existe != null) continue;
                String valor = ((String) i[1]).replace('.', ',');
                Db.executar(c, "INSERT INTO premios(nome, descricao, pontos, estoque, imagem, origem, ativo, criado_em) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        nome, "Valor na loja: R$ " + valor, 100, -1, null, "MANUAL", true, LocalDateTime.now());
            }
            return null;
        });
        java.util.Map<String, String> v = new java.util.LinkedHashMap<>();
        v.put(Config.MODO_RESGATE, "PRODUTOS");
        v.put(CATALOGO_V1, LocalDateTime.now().toString());
        Config.setVarios(v);
    }
}
