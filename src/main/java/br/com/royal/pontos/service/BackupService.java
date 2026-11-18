package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.util.AppConfig;
import br.com.royal.pontos.util.Formatos;
import br.com.royal.pontos.util.Xlsx;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Backup automático em Excel, guardado no computador (independente do banco na nuvem).
 *
 * Documentos\Royal Pontos\Backups\
 *   Saldos Royal Pontos.xlsx      → saldo atual de cada cliente + os 2 saldos anteriores + todos os lançamentos
 *   copias\Saldos AAAA-MM-DD.xlsx → uma cópia por dia (guarda os últimos 30 dias)
 *   historico\saldos-AAAA-MM-DD.csv → foto diária dos saldos (base dos "saldos anteriores")
 */
public final class BackupService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter BR_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private BackupService() {}

    public static Path pasta() {
        Path docs;
        try {
            docs = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
        } catch (Throwable e) {
            docs = Path.of(System.getProperty("user.home"), "Documents");
        }
        if (!Files.isDirectory(docs)) docs = AppConfig.PASTA;
        return docs.resolve("Royal Pontos").resolve("Backups");
    }

    public static Path arquivoPrincipal() { return pasta().resolve("Saldos Royal Pontos.xlsx"); }

    private record Linha(long id, String nome, String cpf, String telefone, LocalDateTime desde, long saldo, long aVencer) {}

    /** Gera o backup agora. Retorna o arquivo principal. */
    public static synchronized Path executar() throws Exception {
        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        // ---- dados atuais (consultas agrupadas: rápido mesmo com muitos clientes)
        Map<Long, Long> saldos = new HashMap<>(), vencer = new HashMap<>();
        List<Linha> clientes = new ArrayList<>();
        List<Movimento> movimentos = Db.tx(c -> {
            Db.lista(c, "SELECT cliente_id, SUM(pontos_restantes) FROM movimentos WHERE pontos_restantes > 0 AND expira_em >= ? GROUP BY cliente_id",
                    rs -> saldos.put(rs.getLong(1), rs.getLong(2)), hoje);
            Db.lista(c, "SELECT cliente_id, SUM(pontos_restantes) FROM movimentos WHERE pontos_restantes > 0 AND expira_em BETWEEN ? AND ? GROUP BY cliente_id",
                    rs -> vencer.put(rs.getLong(1), rs.getLong(2)), hoje, hoje.plusDays(30));
            Db.lista(c, "SELECT id, nome, cpf, telefone, criado_em FROM clientes WHERE ativo = TRUE ORDER BY nome", rs -> {
                long id = rs.getLong(1);
                clientes.add(new Linha(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, LocalDateTime.class),
                        saldos.getOrDefault(id, 0L), vencer.getOrDefault(id, 0L)));
                return null;
            });
            return Db.lista(c, PontosService.SELECT_MOV + "ORDER BY m.criado_em, m.id", PontosService::map);
        });

        // ---- foto do dia (CSV) e as 2 fotos anteriores
        Path historico = pasta().resolve("historico");
        Files.createDirectories(historico);
        try (BufferedWriter w = Files.newBufferedWriter(historico.resolve("saldos-" + DIA.format(hoje) + ".csv"), StandardCharsets.UTF_8)) {
            w.write("cpf;nome;saldo;atualizado_em\n");
            for (Linha l : clientes) w.write(l.cpf() + ";" + l.nome().replace(';', ',') + ";" + l.saldo() + ";" + agora.withNano(0) + "\n");
        }
        List<LocalDate> anteriores = new ArrayList<>();
        try (Stream<Path> s = Files.list(historico)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.matches("saldos-\\d{4}-\\d{2}-\\d{2}\\.csv"))
                    .map(n -> LocalDate.parse(n.substring(7, 17))).filter(d -> d.isBefore(hoje))
                    .sorted(Comparator.reverseOrder()).limit(2).forEach(anteriores::add);
        }
        List<Map<String, Long>> saldosAnteriores = new ArrayList<>();
        for (LocalDate d : anteriores) saldosAnteriores.add(lerFoto(historico.resolve("saldos-" + DIA.format(d) + ".csv")));
        // clientes que existiam antes mas sumiram do banco também aparecem (com saldo atual vazio)
        Map<String, String> nomesAntigos = new LinkedHashMap<>();
        for (LocalDate d : anteriores) nomesAntigos.putAll(lerNomes(historico.resolve("saldos-" + DIA.format(d) + ".csv")));
        Set<String> atuais = new HashSet<>();
        clientes.forEach(l -> atuais.add(l.cpf()));

        // ---- planilha
        Xlsx x = Xlsx.nova();
        List<Object> cab = new ArrayList<>(List.of("Cliente", "CPF", "Celular", "Saldo atual (" + BR_HORA.format(agora) + ")"));
        for (LocalDate d : anteriores) cab.add("Saldo em " + BR.format(d));
        cab.addAll(List.of("A vencer em 30 dias", "Cliente desde"));
        double[] larg = anteriores.size() == 2 ? new double[]{38, 16, 17, 26, 18, 18, 18, 14}
                : anteriores.size() == 1 ? new double[]{38, 16, 17, 26, 18, 18, 14} : new double[]{38, 16, 17, 26, 18, 14};
        Xlsx.Aba saldosAba = x.aba("Saldos").larguras(larg).linha(cab.toArray());
        long total = 0;
        for (Linha l : clientes) {
            List<Object> v = new ArrayList<>(List.of(l.nome(), Formatos.cpf(l.cpf()), Formatos.telefone(l.telefone()), l.saldo()));
            for (Map<String, Long> m : saldosAnteriores) v.add(m.get(l.cpf()));
            v.add(l.aVencer());
            v.add(l.desde() == null ? "" : BR.format(l.desde()));
            saldosAba.linha(v.toArray());
            total += l.saldo();
        }
        for (var e : nomesAntigos.entrySet()) {
            if (atuais.contains(e.getKey())) continue;
            List<Object> v = new ArrayList<>(List.of(e.getValue() + " (não encontrado no sistema)", Formatos.cpf(e.getKey()), "", "—"));
            for (Map<String, Long> m : saldosAnteriores) v.add(m.get(e.getKey()));
            v.add(null);
            v.add("");
            saldosAba.linha(v.toArray());
        }

        Xlsx.Aba mov = x.aba("Lançamentos").larguras(17, 32, 16, 17, 40, 10, 12, 12, 11, 11, 16, 16)
                .linha("Data", "Cliente", "CPF", "Tipo", "Descrição", "Pontos", "Valor (R$)", "Validade", "Restantes", "Estornado", "Código", "Usuário");
        Map<Long, String> cpfPorId = new HashMap<>();
        clientes.forEach(l -> cpfPorId.put(l.id(), l.cpf()));
        for (Movimento m : movimentos) {
            mov.linha(m.criadoEm() == null ? "" : BR_HORA.format(m.criadoEm()), m.clienteNome(), Formatos.cpf(cpfPorId.getOrDefault(m.clienteId(), "")),
                    m.tipoTexto(), m.descricao(), m.pontos(), m.valor() == null ? null : m.valor().doubleValue(),
                    m.expiraEm() == null ? "" : BR.format(m.expiraEm()), m.isLote() ? m.restantes() : null,
                    m.estornado() ? "Sim" : "", m.cupom(), m.usuarioNome());
        }

        x.aba("Informações").larguras(34, 60)
                .linha("Royal Pontos — backup de saldos", "")
                .linha("Gerado em", BR_HORA.format(agora))
                .linha("Clientes", String.valueOf(clientes.size()))
                .linha("Total de pontos com clientes", Formatos.numero(total))
                .linha("Lançamentos", String.valueOf(movimentos.size()))
                .linha("Como usar", "A aba Saldos mostra o saldo atual e os 2 saldos anteriores de cada cliente.")
                .linha("", "A aba Lançamentos tem todo o histórico (compras, resgates, vencimentos e estornos).")
                .linha("", "Cópias de dias anteriores ficam na pasta 'copias'.");

        Path principal = arquivoPrincipal();
        x.salvar(principal);
        Path copias = pasta().resolve("copias");
        Files.createDirectories(copias);
        Files.copy(principal, copias.resolve("Saldos " + DIA.format(hoje) + ".xlsx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        limpar(copias, "Saldos \\d{4}-\\d{2}-\\d{2}\\.xlsx", 30);
        limpar(historico, "saldos-\\d{4}-\\d{2}-\\d{2}\\.csv", 90);
        Atualizador.log("Backup em Excel gerado: " + principal);
        return principal;
    }

    private static Map<String, Long> lerFoto(Path p) {
        Map<String, Long> m = new HashMap<>();
        try {
            for (String l : Files.readAllLines(p, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] c = l.split(";");
                if (c.length >= 3) m.put(c[0], Long.parseLong(c[2].trim()));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static Map<String, String> lerNomes(Path p) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            for (String l : Files.readAllLines(p, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] c = l.split(";");
                if (c.length >= 2) m.put(c[0], c[1]);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static void limpar(Path pasta, String padrao, int manter) {
        try (Stream<Path> s = Files.list(pasta)) {
            List<Path> arquivos = s.filter(p -> p.getFileName().toString().matches(padrao))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
            for (int i = manter; i < arquivos.size(); i++) Files.deleteIfExists(arquivos.get(i));
        } catch (Exception ignored) {
        }
    }
}
