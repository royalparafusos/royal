package br.com.royal.pontos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Movimento(long id, long clienteId, String clienteNome, String tipo, int pontos,
                        BigDecimal valor, int restantes, LocalDate expiraEm, String descricao,
                        String origem, String cupom, Integer quantidade, String usuarioNome,
                        boolean estornado, LocalDateTime criadoEm) {

    public static final String CREDITO = "CREDITO";
    public static final String RESGATE_PRODUTO = "RESGATE_PRODUTO";
    public static final String RESGATE_DESCONTO = "RESGATE_DESCONTO";
    public static final String EXPIRACAO = "EXPIRACAO";
    public static final String ESTORNO = "ESTORNO";
    public static final String AJUSTE = "AJUSTE";

    public String tipoTexto() {
        return switch (tipo) {
            case CREDITO -> "Compra";
            case RESGATE_PRODUTO -> "Resgate produto";
            case RESGATE_DESCONTO -> "Resgate desconto";
            case EXPIRACAO -> "Expirados";
            case ESTORNO -> "Estorno";
            case AJUSTE -> "Ajuste";
            default -> tipo;
        };
    }

    public boolean isResgate() { return tipo.startsWith("RESGATE"); }

    /** Lançamento que gerou pontos (lote com validade). */
    public boolean isLote() { return pontos > 0 && expiraEm != null; }

    public String situacao() {
        if (estornado) return "Estornado";
        if (!isLote()) return "";
        if (restantes == 0) return expiraEm.isBefore(LocalDate.now()) ? "Expirado" : "Utilizado";
        if (expiraEm.isBefore(LocalDate.now())) return "Expirado";
        return restantes < pontos ? "Parcial" : "Ativo";
    }
}
