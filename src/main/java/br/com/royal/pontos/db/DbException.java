package br.com.royal.pontos.db;

/** Erro de banco ou de regra de negócio, com mensagem pronta para mostrar ao usuário. */
public class DbException extends RuntimeException {
    public DbException(String msg) { super(msg); }
    public DbException(String msg, Throwable causa) { super(msg, causa); }
}
