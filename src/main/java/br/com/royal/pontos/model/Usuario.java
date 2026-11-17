package br.com.royal.pontos.model;

public record Usuario(long id, String nome, String login, String perfil, boolean ativo) {
    public boolean isAdmin() { return "ADMIN".equals(perfil); }
    public String perfilTexto() { return isAdmin() ? "Administrador" : "Atendente"; }
}
