package br.com.royal.pontos.model;

public record Premio(long id, String nome, String descricao, int pontos, int estoque,
                     String imagem, String origem, String blingProdutoId, boolean ativo) {

    public boolean semControleEstoque() { return estoque < 0; }

    public boolean disponivel() { return ativo && estoque != 0; }
}
