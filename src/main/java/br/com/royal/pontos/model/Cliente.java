package br.com.royal.pontos.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Cliente(long id, String nome, String cpf, String telefone, String email,
                      LocalDate nascimento, boolean aceiteLgpd, boolean ativo,
                      LocalDateTime criadoEm, int saldo) {
}
