package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;

import java.time.LocalDateTime;

/**
 * Ajustes que rodam uma única vez no banco, na primeira abertura de uma versão nova.
 * Cada ajuste grava uma marca em "configuracoes" para não rodar de novo.
 */
public final class Migracoes {

    private static final String CATALOGO_V1 = "migracao_catalogo_100pts_v1";
    private static final String CATALOGO_V2 = "migracao_catalogo_faixas_v2";

    private Migracoes() {}

    public static void aplicar() {
        if (Config.get(CATALOGO_V1).isBlank()) catalogoInicial();
        if (Config.get(CATALOGO_V2).isBlank()) catalogoFaixas();
    }

    /**
     * Faixas definidas em 19/11/2026. Regra: cada 100 pontos valem até R$ 10,00 em produto
     * (100 pts até R$ 10 · 200 até R$ 20 · 300 até R$ 30 · 500 até R$ 50).
     */
    private static void catalogoFaixas() {
        inserir(new Object[][]{
                {"Araldite Hobby 23 g Tekbond", "7.00", 100},
                {"Adesivo Epóxi Araldite Massa 100 g Tekbond", "10.00", 100},
                {"Antiferrugem Tek Lub 300 ml Tekbond", "13.33", 200},
                {"Veda Junta Orbiquímica", "12.00", 200},
                {"Bainha de Lona para Facão Lotus", "11.43", 200},
                {"Clorador Médio Brustec", "19.05", 200},
                {"Espeto Duplo 75 cm Mor", "18.00", 200},
                {"Espeto Simples 85 cm Mor", "18.00", 200},
                {"Abraçadeira Plástica 2,5 x 100 Branca (pacote com 100)", "15.00", 200},
                {"Facão Mato 20\" Cabo Plástico Vonder", "30.00", 300},
                {"Facão Mato 16\" Cabo Plástico Vonder", "22.00", 300},
                {"Clorador Margarida Brustec", "28.57", 300},
                {"Araldite Epóxi Hobby 10 min Tekbond", "22.00", 300},
                {"Conjunto Assador 3 Facas Mor", "42.00", 500},
                {"Conjunto Assador Faca 9\" e Chaira Mor", "42.00", 500},
                {"Tábua de Bambu com Cabo 38,5 x 20,5 cm Mor", "45.00", 500},
        });
        Config.set(CATALOGO_V2, LocalDateTime.now().toString());
    }

    private static void inserir(Object[][] itens) {
        Db.tx(c -> {
            for (Object[] i : itens) {
                String nome = (String) i[0];
                Long existe = Db.um(c, "SELECT id FROM premios WHERE LOWER(nome) = LOWER(?)", rs -> rs.getLong(1), nome);
                if (existe != null) continue;
                String valor = new java.math.BigDecimal((String) i[1]).setScale(2, java.math.RoundingMode.HALF_UP)
                        .toPlainString().replace('.', ',');
                Db.executar(c, "INSERT INTO premios(nome, descricao, pontos, estoque, imagem, origem, ativo, criado_em) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        nome, "Valor na loja: R$ " + valor, (Integer) i[2], -1, null, "MANUAL", true, LocalDateTime.now());
            }
            return null;
        });
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
