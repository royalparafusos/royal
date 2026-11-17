package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.db.DbException;
import br.com.royal.pontos.model.Premio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public final class PremioService {

    private PremioService() {}

    static Premio map(ResultSet rs) throws SQLException {
        return new Premio(rs.getLong("id"), rs.getString("nome"), rs.getString("descricao"), rs.getInt("pontos"),
                rs.getInt("estoque"), rs.getString("imagem"), rs.getString("origem"), rs.getString("bling_produto_id"),
                rs.getBoolean("ativo"));
    }

    public static List<Premio> listar(boolean incluirInativos, String busca) {
        String b = busca == null ? "" : busca.trim().toLowerCase();
        String sql = "SELECT * FROM premios WHERE (? = TRUE OR ativo = TRUE) AND (? = '' OR LOWER(nome) LIKE ?) ORDER BY ativo DESC, pontos, nome";
        return Db.lista(sql, PremioService::map, incluirInativos, b, "%" + b + "%");
    }

    public static void salvar(Long id, String nome, String descricao, int pontos, int estoque, String imagem, boolean ativo) {
        if (nome == null || nome.isBlank()) throw new DbException("Informe o nome do prêmio.");
        if (pontos <= 0) throw new DbException("Informe quantos pontos o prêmio custa.");
        if (id == null) {
            Db.executar("INSERT INTO premios(nome, descricao, pontos, estoque, imagem, origem, ativo, criado_em) VALUES (?,?,?,?,?,?,?,?)",
                    nome.trim(), descricao, pontos, estoque, imagem, "MANUAL", ativo, LocalDateTime.now());
        } else {
            Db.executar("UPDATE premios SET nome=?, descricao=?, pontos=?, estoque=?, imagem=?, ativo=? WHERE id=?",
                    nome.trim(), descricao, pontos, estoque, imagem, ativo, id);
        }
    }

    /** Importa (ou atualiza) um produto do Bling como prêmio. */
    public static boolean importarBling(String blingId, String nome, String descricao, int pontos, String imagem) {
        return Db.tx(c -> {
            Long existe = Db.um(c, "SELECT id FROM premios WHERE bling_produto_id = ?", rs -> rs.getLong(1), blingId);
            if (existe != null) {
                Db.executar(c, "UPDATE premios SET nome=?, pontos=?, imagem=COALESCE(?, imagem) WHERE id=?", nome, pontos, imagem, existe);
                return false;
            }
            Db.executar(c, "INSERT INTO premios(nome, descricao, pontos, estoque, imagem, origem, bling_produto_id, ativo, criado_em) "
                    + "VALUES (?,?,?,-1,?,'BLING',?,TRUE,?)", nome, descricao, pontos, imagem, blingId, LocalDateTime.now());
            return true;
        });
    }
}
