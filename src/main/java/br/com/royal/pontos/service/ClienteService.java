package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.db.DbException;
import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.util.Formatos;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ClienteService {

    private ClienteService() {}

    static final String SALDO_SQL = "(SELECT COALESCE(SUM(m.pontos_restantes),0) FROM movimentos m "
            + "WHERE m.cliente_id = c.id AND m.pontos_restantes > 0 AND m.expira_em >= ?)";

    static Cliente map(ResultSet rs) throws SQLException {
        return new Cliente(rs.getLong("id"), rs.getString("nome"), rs.getString("cpf"), rs.getString("telefone"),
                rs.getString("email"), rs.getObject("nascimento", LocalDate.class), rs.getBoolean("aceite_lgpd"),
                rs.getBoolean("ativo"), rs.getObject("criado_em", LocalDateTime.class), rs.getInt("saldo"));
    }

    public static List<Cliente> buscar(String texto) {
        String t = texto == null ? "" : texto.trim();
        String dig = Formatos.digitos(t);
        String sql = "SELECT c.*, " + SALDO_SQL + " AS saldo FROM clientes c WHERE c.ativo = TRUE ";
        if (t.isEmpty()) {
            return Db.lista(sql + "ORDER BY c.nome LIMIT 500", ClienteService::map, LocalDate.now());
        }
        sql += "AND (LOWER(c.nome) LIKE ? " + (dig.length() >= 3 ? "OR c.cpf LIKE ? OR c.telefone LIKE ?" : "") + ") ORDER BY c.nome LIMIT 500";
        String like = "%" + t.toLowerCase() + "%";
        return dig.length() >= 3
                ? Db.lista(sql, ClienteService::map, LocalDate.now(), like, "%" + dig + "%", "%" + dig + "%")
                : Db.lista(sql, ClienteService::map, LocalDate.now(), like);
    }

    public static Cliente porCpf(String cpf) {
        return Db.um("SELECT c.*, " + SALDO_SQL + " AS saldo FROM clientes c WHERE c.cpf = ?",
                ClienteService::map, LocalDate.now(), Formatos.digitos(cpf));
    }

    public static Cliente porId(long id) {
        return Db.um("SELECT c.*, " + SALDO_SQL + " AS saldo FROM clientes c WHERE c.id = ?",
                ClienteService::map, LocalDate.now(), id);
    }

    public static Cliente salvar(Long id, String nome, String cpf, String telefone, String email,
                                 LocalDate nascimento, boolean aceite) {
        String c = Formatos.digitos(cpf);
        String tel = Formatos.digitos(telefone);
        if (nome == null || nome.trim().length() < 3) throw new DbException("Informe o nome completo do cliente.");
        if (!Formatos.cpfValido(c)) throw new DbException("CPF inválido. Confira os números.");
        if (tel.length() < 10) throw new DbException("Informe o telefone com DDD.");
        if (!aceite) throw new DbException("É preciso o aceite do cliente para participar do programa (LGPD).");
        return Db.tx(con -> {
            Long outro = Db.um(con, "SELECT id FROM clientes WHERE cpf = ?", rs -> rs.getLong(1), c);
            if (outro != null && !outro.equals(id)) throw new DbException("Já existe um cliente com esse CPF.");
            long novoId = id == null ? inserir(con, nome, c, tel, email, nascimento, aceite) : id;
            if (id != null) {
                Db.executar(con, "UPDATE clientes SET nome=?, cpf=?, telefone=?, email=?, nascimento=?, aceite_lgpd=? WHERE id=?",
                        nome.trim(), c, tel, vazioNull(email), nascimento, aceite, id);
            }
            return Db.um(con, "SELECT c.*, " + SALDO_SQL + " AS saldo FROM clientes c WHERE c.id = ?",
                    ClienteService::map, LocalDate.now(), novoId);
        });
    }

    static long inserir(Connection con, String nome, String cpf, String tel, String email,
                        LocalDate nascimento, boolean aceite) throws SQLException {
        return Db.inserir(con, "INSERT INTO clientes(nome, cpf, telefone, email, nascimento, aceite_lgpd, ativo, criado_em) "
                        + "VALUES (?,?,?,?,?,?,TRUE,?)",
                nome.trim(), cpf, tel, vazioNull(email), nascimento, aceite, LocalDateTime.now());
    }

    public static void desativar(long id) {
        Db.executar("UPDATE clientes SET ativo = FALSE WHERE id = ?", id);
    }

    private static String vazioNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
