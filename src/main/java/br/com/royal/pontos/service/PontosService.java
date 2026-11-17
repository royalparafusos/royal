package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.db.DbException;
import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.model.Premio;
import br.com.royal.pontos.util.Formatos;
import br.com.royal.pontos.util.Senha;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Coração do programa: crédito, resgate, validade e estorno de pontos.
 *
 * Cada crédito vira um "lote" com data de validade. Os resgates consomem
 * primeiro os lotes que vencem antes (FIFO), então o cliente nunca perde
 * pontos que poderia ter usado.
 */
public final class PontosService {

    private PontosService() {}

    public record Resultado(long movimentoId, int pontos, String codigo, BigDecimal valor, LocalDate validade, int saldoAtual) {}

    static final String SELECT_MOV = "SELECT m.*, c.nome AS cliente_nome, u.nome AS usuario_nome FROM movimentos m "
            + "JOIN clientes c ON c.id = m.cliente_id LEFT JOIN usuarios u ON u.id = m.usuario_id ";

    static Movimento map(ResultSet rs) throws SQLException {
        return new Movimento(rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("cliente_nome"),
                rs.getString("tipo"), rs.getInt("pontos"), rs.getBigDecimal("valor"), rs.getInt("pontos_restantes"),
                rs.getObject("expira_em", LocalDate.class), rs.getString("descricao"), rs.getString("origem"),
                rs.getString("cupom"), (Integer) rs.getObject("quantidade"), rs.getString("usuario_nome"),
                rs.getBoolean("estornado"), rs.getObject("criado_em", LocalDateTime.class));
    }

    // ------------------------------------------------------------------ regra

    /** Quantos pontos uma compra desse valor gera, pela regra configurada. */
    public static int calcular(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) return 0;
        if (valor.compareTo(Config.decimal(Config.VALOR_MINIMO)) < 0) return 0;
        BigDecimal reais = Config.decimal(Config.REGRA_REAIS);
        if (reais.signum() <= 0) return 0;
        long blocos = valor.divide(reais, 0, RoundingMode.FLOOR).longValue();
        return (int) Math.min(Integer.MAX_VALUE, blocos * Config.inteiro(Config.REGRA_PONTOS));
    }

    public static LocalDate validadePara(LocalDate base) {
        return base.plusDays(Config.inteiro(Config.VALIDADE_DIAS));
    }

    public static String regraTexto() {
        return "A cada " + Formatos.moeda(Config.decimal(Config.REGRA_REAIS)) + " em compras = "
                + Config.inteiro(Config.REGRA_PONTOS) + " ponto(s) · validade de " + Config.inteiro(Config.VALIDADE_DIAS) + " dias";
    }

    // ------------------------------------------------------------------ saldo

    public static int saldo(Connection c, long clienteId) throws SQLException {
        return (int) Db.numero(c, "SELECT COALESCE(SUM(pontos_restantes),0) FROM movimentos WHERE cliente_id=? "
                + "AND pontos_restantes > 0 AND expira_em >= ?", clienteId, LocalDate.now());
    }

    public static int saldo(long clienteId) {
        return Db.tx(c -> saldo(c, clienteId));
    }

    public static int aVencer(long clienteId, int dias) {
        LocalDate hoje = LocalDate.now();
        return (int) Db.numero("SELECT COALESCE(SUM(pontos_restantes),0) FROM movimentos WHERE cliente_id=? "
                + "AND pontos_restantes > 0 AND expira_em BETWEEN ? AND ?", clienteId, hoje, hoje.plusDays(dias));
    }

    public static LocalDate proximaExpiracao(long clienteId) {
        return Db.um("SELECT MIN(expira_em) FROM movimentos WHERE cliente_id=? AND pontos_restantes > 0 AND expira_em >= ?",
                rs -> rs.getObject(1, LocalDate.class), clienteId, LocalDate.now());
    }

    public static List<Movimento> extrato(long clienteId) {
        return Db.lista(SELECT_MOV + "WHERE m.cliente_id = ? ORDER BY m.criado_em DESC, m.id DESC",
                PontosService::map, clienteId);
    }

    public static List<Movimento> ultimos(int limite) {
        return Db.lista(SELECT_MOV + "ORDER BY m.criado_em DESC, m.id DESC LIMIT " + limite, PontosService::map);
    }

    public static List<Movimento> creditosDeHoje() {
        return Db.lista(SELECT_MOV + "WHERE m.tipo = ? AND m.criado_em >= ? ORDER BY m.criado_em DESC LIMIT 20",
                PontosService::map, Movimento.CREDITO, LocalDate.now().atStartOfDay());
    }

    // ------------------------------------------------------------------ crédito

    public static Resultado creditar(long clienteId, BigDecimal valor, String descricao) {
        int pts = calcular(valor);
        if (pts <= 0) throw new DbException("Esse valor não gera pontos pela regra atual (" + regraTexto() + ").");
        return Db.tx(c -> creditar(c, clienteId, valor, pts, descricao, "BALCAO", null, LocalDateTime.now()));
    }

    static Resultado creditar(Connection c, long clienteId, BigDecimal valor, int pts, String descricao,
                              String origem, String blingPedidoId, LocalDateTime quando) throws SQLException {
        LocalDate validade = validadePara(quando.toLocalDate());
        long id = Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, valor, pontos_restantes, expira_em, descricao, "
                        + "origem, bling_pedido_id, usuario_id, criado_em) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                clienteId, Movimento.CREDITO, pts, valor, pts, validade,
                descricao == null || descricao.isBlank() ? "Compra na loja" : descricao.trim(),
                origem, blingPedidoId, Sessao.id(), quando);
        return new Resultado(id, pts, null, valor, validade, saldo(c, clienteId));
    }

    // ------------------------------------------------------------------ resgate

    /** Consome pontos dos lotes que vencem primeiro. */
    private static void consumir(Connection c, long clienteId, int pontos) throws SQLException {
        List<long[]> lotes = Db.lista(c, "SELECT id, pontos_restantes FROM movimentos WHERE cliente_id=? AND pontos_restantes > 0 "
                        + "AND expira_em >= ? ORDER BY expira_em, id FOR UPDATE",
                rs -> new long[]{rs.getLong(1), rs.getLong(2)}, clienteId, LocalDate.now());
        int disponivel = (int) lotes.stream().mapToLong(l -> l[1]).sum();
        if (disponivel < pontos) {
            throw new DbException("Saldo insuficiente: o cliente tem " + Formatos.pontos(disponivel)
                    + " e o resgate precisa de " + Formatos.pontos(pontos) + ".");
        }
        int falta = pontos;
        for (long[] l : lotes) {
            if (falta == 0) break;
            int usa = (int) Math.min(falta, l[1]);
            Db.executar(c, "UPDATE movimentos SET pontos_restantes = pontos_restantes - ? WHERE id = ?", usa, l[0]);
            falta -= usa;
        }
    }

    public static Resultado resgatarProduto(long clienteId, long premioId, int quantidade) {
        if (!Config.aceitaProdutos()) throw new DbException("Resgate por produtos está desativado nas configurações.");
        if (quantidade < 1) throw new DbException("Quantidade inválida.");
        return Db.tx(c -> {
            Premio p = Db.um(c, "SELECT * FROM premios WHERE id = ?" + (Db.isNuvem() ? " FOR UPDATE" : ""), PremioService::map, premioId);
            if (p == null || !p.ativo()) throw new DbException("Prêmio indisponível.");
            if (!p.semControleEstoque() && p.estoque() < quantidade) throw new DbException("Estoque insuficiente para esse prêmio.");
            int total = p.pontos() * quantidade;
            consumir(c, clienteId, total);
            if (!p.semControleEstoque()) Db.executar(c, "UPDATE premios SET estoque = estoque - ? WHERE id = ?", quantidade, premioId);
            String codigo = Senha.codigo("RP");
            long id = Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, descricao, cupom, premio_id, quantidade, "
                            + "usuario_id, criado_em) VALUES (?,?,?,?,?,?,?,?,?)",
                    clienteId, Movimento.RESGATE_PRODUTO, -total, quantidade + "x " + p.nome(), codigo, premioId,
                    quantidade, Sessao.id(), LocalDateTime.now());
            return new Resultado(id, total, codigo, null, null, saldo(c, clienteId));
        });
    }

    public static BigDecimal valorDesconto(int pontos) {
        int bloco = Math.max(1, Config.inteiro(Config.DESCONTO_PONTOS));
        return Config.decimal(Config.DESCONTO_REAIS).multiply(BigDecimal.valueOf(pontos / bloco)).setScale(2, RoundingMode.HALF_UP);
    }

    public static Resultado resgatarDesconto(long clienteId, int pontos) {
        if (!Config.aceitaDesconto()) throw new DbException("Resgate em desconto está desativado nas configurações.");
        int bloco = Math.max(1, Config.inteiro(Config.DESCONTO_PONTOS));
        if (pontos < bloco || pontos % bloco != 0) throw new DbException("Os pontos devem ser múltiplos de " + bloco + ".");
        BigDecimal valor = valorDesconto(pontos);
        return Db.tx(c -> {
            consumir(c, clienteId, pontos);
            String cupom = Senha.codigo("DESC");
            long id = Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, valor, descricao, cupom, usuario_id, criado_em) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    clienteId, Movimento.RESGATE_DESCONTO, -pontos, valor, "Desconto de " + Formatos.moeda(valor),
                    cupom, Sessao.id(), LocalDateTime.now());
            return new Resultado(id, pontos, cupom, valor, null, saldo(c, clienteId));
        });
    }

    // ------------------------------------------------------------------ ajuste / estorno / validade

    public static void ajustar(long clienteId, int pontos, String motivo) {
        if (pontos == 0) throw new DbException("Informe uma quantidade diferente de zero.");
        if (motivo == null || motivo.isBlank()) throw new DbException("Informe o motivo do ajuste.");
        Db.tx(c -> {
            if (pontos > 0) {
                Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, pontos_restantes, expira_em, descricao, usuario_id, criado_em) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        clienteId, Movimento.AJUSTE, pontos, pontos, validadePara(LocalDate.now()), motivo.trim(), Sessao.id(), LocalDateTime.now());
            } else {
                consumir(c, clienteId, -pontos);
                Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, descricao, usuario_id, criado_em) VALUES (?,?,?,?,?,?)",
                        clienteId, Movimento.AJUSTE, pontos, motivo.trim(), Sessao.id(), LocalDateTime.now());
            }
            return null;
        });
    }

    public static void estornar(long movimentoId) {
        Db.tx(c -> {
            Movimento m = Db.um(c, SELECT_MOV + "WHERE m.id = ?", PontosService::map, movimentoId);
            if (m == null) throw new DbException("Lançamento não encontrado.");
            if (m.estornado()) throw new DbException("Esse lançamento já foi estornado.");
            LocalDateTime agora = LocalDateTime.now();
            if (m.isLote()) {
                if (m.restantes() == 0) throw new DbException("Os pontos desse lançamento já foram usados ou expiraram.");
                Db.executar(c, "UPDATE movimentos SET pontos_restantes = 0, estornado = TRUE WHERE id = ?", m.id());
                Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, descricao, usuario_id, criado_em) VALUES (?,?,?,?,?,?)",
                        m.clienteId(), Movimento.ESTORNO, -m.restantes(), "Estorno do lançamento #" + m.id(), Sessao.id(), agora);
            } else if (m.isResgate()) {
                Db.executar(c, "UPDATE movimentos SET estornado = TRUE WHERE id = ?", m.id());
                int pts = -m.pontos();
                Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, pontos_restantes, expira_em, descricao, usuario_id, criado_em) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        m.clienteId(), Movimento.ESTORNO, pts, pts, validadePara(LocalDate.now()),
                        "Cancelamento do resgate " + (m.cupom() == null ? "#" + m.id() : m.cupom()), Sessao.id(), agora);
                if (Movimento.RESGATE_PRODUTO.equals(m.tipo()) && m.quantidade() != null) {
                    Db.executar(c, "UPDATE premios SET estoque = estoque + ? WHERE id = (SELECT premio_id FROM movimentos WHERE id = ?) AND estoque >= 0",
                            m.quantidade(), m.id());
                }
            } else {
                throw new DbException("Esse tipo de lançamento não pode ser estornado.");
            }
            return null;
        });
    }

    /** Zera lotes vencidos, registrando a expiração no extrato. Retorna quantos lotes expiraram. */
    public static int processarExpiracoes() {
        return Db.tx(c -> {
            List<long[]> vencidos = Db.lista(c, "SELECT id, cliente_id, pontos_restantes FROM movimentos "
                            + "WHERE pontos_restantes > 0 AND expira_em < ?",
                    rs -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, LocalDate.now());
            LocalDateTime agora = LocalDateTime.now();
            for (long[] v : vencidos) {
                Db.executar(c, "UPDATE movimentos SET pontos_restantes = 0 WHERE id = ?", v[0]);
                Db.inserir(c, "INSERT INTO movimentos(cliente_id, tipo, pontos, descricao, criado_em) VALUES (?,?,?,?,?)",
                        v[1], Movimento.EXPIRACAO, (int) -v[2], "Pontos vencidos do lançamento #" + v[0], agora);
            }
            return vencidos.size();
        });
    }
}
