package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Cliente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Números do painel inicial. */
public final class PainelService {

    private PainelService() {}

    public record Resumo(long clientes, long novosMes, long emitidosMes, long resgatadosMes, long emCirculacao,
                         long aVencer7, BigDecimal vendasMes, Map<LocalDate, long[]> serie, List<Cliente> top) {}

    public static Resumo carregar() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate inicioSerie = hoje.minusDays(29);
        return Db.tx(c -> {
            long clientes = Db.numero(c, "SELECT COUNT(*) FROM clientes WHERE ativo = TRUE");
            long novos = Db.numero(c, "SELECT COUNT(*) FROM clientes WHERE ativo = TRUE AND criado_em >= ?", inicioMes.atStartOfDay());
            long emitidos = Db.numero(c, "SELECT COALESCE(SUM(pontos),0) FROM movimentos WHERE tipo = 'CREDITO' AND estornado = FALSE AND criado_em >= ?",
                    inicioMes.atStartOfDay());
            long resgatados = Db.numero(c, "SELECT COALESCE(SUM(-pontos),0) FROM movimentos WHERE tipo LIKE 'RESGATE%' AND estornado = FALSE AND criado_em >= ?",
                    inicioMes.atStartOfDay());
            long circulacao = Db.numero(c, "SELECT COALESCE(SUM(pontos_restantes),0) FROM movimentos WHERE pontos_restantes > 0 AND expira_em >= ?", hoje);
            long aVencer = Db.numero(c, "SELECT COALESCE(SUM(pontos_restantes),0) FROM movimentos WHERE pontos_restantes > 0 AND expira_em BETWEEN ? AND ?",
                    hoje, hoje.plusDays(7));
            BigDecimal vendas = Db.um(c, "SELECT COALESCE(SUM(valor),0) FROM movimentos WHERE tipo = 'CREDITO' AND estornado = FALSE AND criado_em >= ?",
                    rs -> rs.getBigDecimal(1), inicioMes.atStartOfDay());

            Map<LocalDate, long[]> serie = new LinkedHashMap<>();
            for (int i = 0; i < 30; i++) serie.put(inicioSerie.plusDays(i), new long[2]);
            Db.lista(c, "SELECT CAST(criado_em AS DATE) AS dia, "
                            + "SUM(CASE WHEN tipo = 'CREDITO' THEN pontos ELSE 0 END), "
                            + "SUM(CASE WHEN tipo LIKE 'RESGATE%' THEN -pontos ELSE 0 END) "
                            + "FROM movimentos WHERE estornado = FALSE AND criado_em >= ? GROUP BY CAST(criado_em AS DATE)",
                    rs -> {
                        long[] v = serie.get(rs.getObject(1, LocalDate.class));
                        if (v != null) { v[0] = rs.getLong(2); v[1] = rs.getLong(3); }
                        return null;
                    }, inicioSerie.atStartOfDay());

            List<Cliente> top = Db.lista(c, "SELECT * FROM (SELECT c.*, " + ClienteService.SALDO_SQL + " AS saldo FROM clientes c WHERE c.ativo = TRUE) t "
                    + "WHERE saldo > 0 ORDER BY saldo DESC LIMIT 6", ClienteService::map, hoje);
            return new Resumo(clientes, novos, emitidos, resgatados, circulacao, aVencer, vendas, serie, top);
        });
    }
}
