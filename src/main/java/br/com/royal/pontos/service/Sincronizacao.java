package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.util.AppConfig;

import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Conciliação: envia para a nuvem os clientes e lançamentos que foram feitos neste computador
 * enquanto ele estava no banco local (modo demonstração). Nada é duplicado: clientes são
 * reconhecidos pelo CPF e lançamentos pela combinação cliente + tipo + pontos + data/hora.
 */
public final class Sincronizacao {

    private static final String MARCA = "nuvem_sincronizado_em";

    private Sincronizacao() {}

    public record Pendente(long clientes, long movimentos) {
        public boolean vazio() { return clientes == 0 && movimentos == 0; }
    }

    public record Resultado(int clientesNovos, int movimentosNovos, int movimentosJaExistiam) {}

    private static Connection local() throws SQLException {
        try { Class.forName("org.h2.Driver"); } catch (ClassNotFoundException e) { throw new SQLException(e); }
        return DriverManager.getConnection(AppConfig.jdbcUrlLocal(), "sa", "");
    }

    /** O que existe no banco local e ainda não foi enviado (null se não há banco local). */
    public static Pendente pendente(boolean ignorarMarca) {
        if (!Files.exists(AppConfig.arquivoLocal())) return null;
        try (Connection l = local()) {
            if (!existeTabela(l, "movimentos")) return null;
            if (!ignorarMarca && !valor(l, MARCA).isBlank()) return null;
            long cli = contar(l, "SELECT COUNT(*) FROM clientes");
            long mov = contar(l, "SELECT COUNT(*) FROM movimentos");
            Pendente p = new Pendente(cli, mov);
            return p.vazio() ? null : p;
        } catch (SQLException e) {
            Atualizador.log("Sincronização: não foi possível ler o banco local: " + e.getMessage());
            return null;
        }
    }

    /** Marca o banco local como "não precisa enviar" (o usuário recusou). */
    public static void dispensar() {
        try (Connection l = local()) { gravarMarca(l, "dispensado " + LocalDateTime.now()); } catch (SQLException ignored) { }
    }

    /** Envia tudo do banco local para a nuvem (a conexão atual do programa precisa ser a nuvem). */
    public static Resultado enviar() {
        if (!Db.isNuvem()) throw new IllegalStateException("Conecte o programa à nuvem antes de enviar os dados.");
        try (Connection l = local()) {
            Resultado r = Db.tx(n -> copiar(l, n));
            gravarMarca(l, LocalDateTime.now().toString());
            Atualizador.log("Sincronização concluída: " + r);
            return r;
        } catch (SQLException e) {
            throw new br.com.royal.pontos.db.DbException("Não foi possível ler os dados deste computador: " + e.getMessage(), e);
        }
    }

    private static Resultado copiar(Connection l, Connection n) throws SQLException {
        // usuários (pelo login)
        Map<Long, Long> usuarios = new HashMap<>();
        try (Statement st = l.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM usuarios")) {
            while (rs.next()) {
                String login = rs.getString("login");
                Long id = Db.um(n, "SELECT id FROM usuarios WHERE LOWER(login) = LOWER(?)", x -> x.getLong(1), login);
                if (id == null) {
                    id = Db.inserir(n, "INSERT INTO usuarios(nome, login, senha_hash, perfil, ativo, criado_em) VALUES (?,?,?,?,?,?)",
                            rs.getString("nome"), login, rs.getString("senha_hash"), rs.getString("perfil"),
                            rs.getBoolean("ativo"), ts(rs, "criado_em"));
                }
                usuarios.put(rs.getLong("id"), id);
            }
        }
        // prêmios (pelo nome)
        Map<Long, Long> premios = new HashMap<>();
        try (Statement st = l.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM premios")) {
            while (rs.next()) {
                String nome = rs.getString("nome");
                Long id = Db.um(n, "SELECT id FROM premios WHERE LOWER(nome) = LOWER(?) ORDER BY id LIMIT 1", x -> x.getLong(1), nome);
                if (id == null) {
                    id = Db.inserir(n, "INSERT INTO premios(nome, descricao, pontos, estoque, imagem, origem, bling_produto_id, ativo, criado_em) "
                                    + "VALUES (?,?,?,?,?,?,?,?,?)",
                            nome, rs.getString("descricao"), rs.getInt("pontos"), rs.getInt("estoque"), rs.getString("imagem"),
                            rs.getString("origem"), rs.getString("bling_produto_id"), rs.getBoolean("ativo"), ts(rs, "criado_em"));
                }
                premios.put(rs.getLong("id"), id);
            }
        }
        // clientes (pelo CPF)
        int clientesNovos = 0;
        Map<Long, Long> clientes = new HashMap<>();
        try (Statement st = l.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM clientes")) {
            while (rs.next()) {
                String cpf = rs.getString("cpf");
                Long id = Db.um(n, "SELECT id FROM clientes WHERE cpf = ?", x -> x.getLong(1), cpf);
                if (id == null) {
                    id = Db.inserir(n, "INSERT INTO clientes(nome, cpf, telefone, email, nascimento, aceite_lgpd, ativo, criado_em) "
                                    + "VALUES (?,?,?,?,?,?,?,?)",
                            rs.getString("nome"), cpf, rs.getString("telefone"), rs.getString("email"),
                            rs.getObject("nascimento", java.time.LocalDate.class), rs.getBoolean("aceite_lgpd"),
                            rs.getBoolean("ativo"), ts(rs, "criado_em"));
                    clientesNovos++;
                }
                clientes.put(rs.getLong("id"), id);
            }
        }
        // lançamentos
        int novos = 0, existiam = 0;
        try (Statement st = l.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM movimentos ORDER BY id")) {
            while (rs.next()) {
                Long cli = clientes.get(rs.getLong("cliente_id"));
                if (cli == null) continue;
                LocalDateTime quando = ts(rs, "criado_em");
                String bling = rs.getString("bling_pedido_id");
                long ja = Db.numero(n, "SELECT COUNT(*) FROM movimentos WHERE cliente_id=? AND tipo=? AND pontos=? AND criado_em=?",
                        cli, rs.getString("tipo"), rs.getInt("pontos"), quando);
                if (ja == 0 && bling != null && !bling.isBlank()) {
                    ja = Db.numero(n, "SELECT COUNT(*) FROM movimentos WHERE bling_pedido_id=?", bling);
                }
                if (ja > 0) { existiam++; continue; }
                long pid = rs.getLong("premio_id");
                Long premio = rs.wasNull() ? null : premios.get(pid);
                long uid = rs.getLong("usuario_id");
                Long usuario = rs.wasNull() ? null : usuarios.get(uid);
                Integer qtd = (Integer) rs.getObject("quantidade");
                Db.executar(n, "INSERT INTO movimentos(cliente_id, tipo, pontos, valor, pontos_restantes, expira_em, descricao, origem, "
                                + "bling_pedido_id, cupom, premio_id, quantidade, usuario_id, estornado, criado_em) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        cli, rs.getString("tipo"), rs.getInt("pontos"), rs.getBigDecimal("valor"), rs.getInt("pontos_restantes"),
                        rs.getObject("expira_em", java.time.LocalDate.class), rs.getString("descricao"), rs.getString("origem"),
                        bling == null || bling.isBlank() ? null : bling, rs.getString("cupom"), premio, qtd, usuario,
                        rs.getBoolean("estornado"), quando);
                novos++;
            }
        }
        return new Resultado(clientesNovos, novos, existiam);
    }

    private static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }

    private static boolean existeTabela(Connection c, String nome) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, nome, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = c.getMetaData().getTables(null, null, nome.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private static long contar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String valor(Connection c, String chave) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT valor FROM configuracoes WHERE chave = ?")) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getString(1) != null ? rs.getString(1) : ""; }
        }
    }

    private static void gravarMarca(Connection l, String v) throws SQLException {
        try (PreparedStatement d = l.prepareStatement("DELETE FROM configuracoes WHERE chave = ?")) { d.setString(1, MARCA); d.executeUpdate(); }
        try (PreparedStatement i = l.prepareStatement("INSERT INTO configuracoes(chave, valor) VALUES (?,?)")) {
            i.setString(1, MARCA); i.setString(2, v); i.executeUpdate();
        }
    }
}
