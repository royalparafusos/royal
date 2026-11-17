package br.com.royal.pontos.service;

import br.com.royal.pontos.model.Usuario;

/** Usuário logado no momento. */
public final class Sessao {
    private static Usuario usuario;

    private Sessao() {}

    public static Usuario usuario() { return usuario; }
    public static void entrar(Usuario u) { usuario = u; }
    public static void sair() { usuario = null; }
    public static boolean isAdmin() { return usuario != null && usuario.isAdmin(); }
    public static Long id() { return usuario == null ? null : usuario.id(); }
}
