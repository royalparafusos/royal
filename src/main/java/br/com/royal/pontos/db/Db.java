package br.com.royal.pontos.db;

import br.com.royal.pontos.util.AppConfig;
import br.com.royal.pontos.util.Senha;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco de dados. Funciona com:
 *  - H2 local (modo demonstração, arquivo no próprio computador)
 *  - PostgreSQL do Supabase (nuvem, compartilhado com a página de consulta do cliente)
 */
public final class Db {

    @FunctionalInterface public interface Mapper<T> { T map(ResultSet rs) throws SQLException; }
    @FunctionalInterface public interface Tx<T> { T run(Connection c) throws SQLException; }

    private static String url, usuario, senha;
    private static boolean nuvem;

    private Db() {}

    public static void configurar(AppConfig cfg) {
        url = cfg.jdbcUrl();
        nuvem = cfg.isNuvem();
        usuario = nuvem ? cfg.usuario() : "sa";
        senha = nuvem ? cfg.senha() : "";
        try {
            Class.forName(nuvem ? "org.postgresql.Driver" : "org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver do banco não encontrado", e);
        }
    }

    public static boolean isNuvem() { return nuvem; }

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }

    public static void testar(AppConfig cfg) throws SQLException {
        try (Connection c = DriverManager.getConnection(cfg.jdbcUrl(), cfg.usuario(), cfg.senha());
             Statement st = c.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    public static <T> List<T> lista(Connection c, String sql, Mapper<T> m, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> r = new ArrayList<>();
                while (rs.next()) r.add(m.map(rs));
                return r;
            }
        }
    }

    public static <T> List<T> lista(String sql, Mapper<T> m, Object... params) {
        return tx(c -> lista(c, sql, m, params));
    }

    public static <T> T um(Connection c, String sql, Mapper<T> m, Object... params) throws SQLException {
        List<T> r = lista(c, sql, m, params);
        return r.isEmpty() ? null : r.get(0);
    }

    public static <T> T um(String sql, Mapper<T> m, Object... params) {
        return tx(c -> um(c, sql, m, params));
    }

    public static long numero(Connection c, String sql, Object... params) throws SQLException {
        Long v = um(c, sql, rs -> rs.getLong(1), params);
        return v == null ? 0 : v;
    }

    public static long numero(String sql, Object... params) {
        return tx(c -> numero(c, sql, params));
    }

    public static int executar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public static int executar(String sql, Object... params) {
        return tx(c -> executar(c, sql, params));
    }

    public static long inserir(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"id"})) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public static long inserir(String sql, Object... params) {
        return tx(c -> inserir(c, sql, params));
    }

    /** Executa em transação: tudo ou nada. */
    public static <T> T tx(Tx<T> trabalho) {
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                T r = trabalho.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DbException(traduzir(e), e);
        }
    }

    private static String traduzir(SQLException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase();
        if (m.contains("unique") || m.contains("duplicate") || m.contains("23505")) return "Registro duplicado (já existe um cadastro com esse dado).";
        if (m.contains("connect") || m.contains("timeout") || m.contains("unknownhost")) return "Sem conexão com o banco de dados. Verifique a internet.";
        return "Erro no banco de dados: " + e.getMessage();
    }

    // ------------------------------------------------------------------ estrutura

    private static final String[] DDL = {
        """
        CREATE TABLE IF NOT EXISTS configuracoes (
            chave VARCHAR(60) PRIMARY KEY,
            valor VARCHAR(2000)
        )""",
        """
        CREATE TABLE IF NOT EXISTS usuarios (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            nome VARCHAR(120) NOT NULL,
            login VARCHAR(60) NOT NULL UNIQUE,
            senha_hash VARCHAR(200) NOT NULL,
            perfil VARCHAR(20) NOT NULL,
            ativo BOOLEAN NOT NULL DEFAULT TRUE,
            criado_em TIMESTAMP
        )""",
        """
        CREATE TABLE IF NOT EXISTS clientes (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            nome VARCHAR(150) NOT NULL,
            cpf VARCHAR(11) NOT NULL UNIQUE,
            telefone VARCHAR(20) NOT NULL,
            email VARCHAR(150),
            nascimento DATE,
            aceite_lgpd BOOLEAN NOT NULL DEFAULT FALSE,
            ativo BOOLEAN NOT NULL DEFAULT TRUE,
            criado_em TIMESTAMP
        )""",
        """
        CREATE TABLE IF NOT EXISTS premios (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            nome VARCHAR(150) NOT NULL,
            descricao VARCHAR(500),
            pontos INTEGER NOT NULL,
            estoque INTEGER NOT NULL DEFAULT -1,
            imagem VARCHAR(600),
            origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
            bling_produto_id VARCHAR(40),
            ativo BOOLEAN NOT NULL DEFAULT TRUE,
            criado_em TIMESTAMP
        )""",
        """
        CREATE TABLE IF NOT EXISTS movimentos (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            cliente_id BIGINT NOT NULL REFERENCES clientes(id),
            tipo VARCHAR(20) NOT NULL,
            pontos INTEGER NOT NULL,
            valor NUMERIC(12,2),
            pontos_restantes INTEGER NOT NULL DEFAULT 0,
            expira_em DATE,
            descricao VARCHAR(300),
            origem VARCHAR(20) NOT NULL DEFAULT 'BALCAO',
            bling_pedido_id VARCHAR(40) UNIQUE,
            cupom VARCHAR(20),
            premio_id BIGINT REFERENCES premios(id),
            quantidade INTEGER,
            usuario_id BIGINT REFERENCES usuarios(id),
            estornado BOOLEAN NOT NULL DEFAULT FALSE,
            criado_em TIMESTAMP NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS ix_mov_cliente ON movimentos(cliente_id)",
        "CREATE INDEX IF NOT EXISTS ix_mov_data ON movimentos(criado_em)",
        "CREATE INDEX IF NOT EXISTS ix_mov_validade ON movimentos(expira_em)"
    };

    public static void inicializar() {
        tx(c -> {
            try (Statement st = c.createStatement()) {
                for (String s : DDL) st.execute(s);
            }
            if (numero(c, "SELECT COUNT(*) FROM usuarios") == 0) {
                executar(c, "INSERT INTO usuarios(nome, login, senha_hash, perfil, ativo, criado_em) VALUES (?,?,?,?,TRUE,?)",
                        "Administrador", "admin", Senha.hash("admin"), "ADMIN", LocalDateTime.now());
            }
            return null;
        });
    }
}
