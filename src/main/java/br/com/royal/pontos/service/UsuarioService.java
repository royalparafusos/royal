package br.com.royal.pontos.service;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.db.DbException;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.util.Senha;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public final class UsuarioService {

    private UsuarioService() {}

    private static Usuario map(ResultSet rs) throws SQLException {
        return new Usuario(rs.getLong("id"), rs.getString("nome"), rs.getString("login"),
                rs.getString("perfil"), rs.getBoolean("ativo"));
    }

    public static Usuario autenticar(String login, String senha) {
        String[] r = Db.um("SELECT id, senha_hash FROM usuarios WHERE LOWER(login) = LOWER(?) AND ativo = TRUE",
                rs -> new String[]{rs.getString(1), rs.getString(2)}, login.trim());
        if (r == null || !Senha.confere(senha, r[1])) return null;
        return Db.um("SELECT * FROM usuarios WHERE id = ?", UsuarioService::map, Long.parseLong(r[0]));
    }

    public static List<Usuario> listar() {
        return Db.lista("SELECT * FROM usuarios ORDER BY nome", UsuarioService::map);
    }

    public static void salvar(Long id, String nome, String login, String senha, String perfil, boolean ativo) {
        if (nome.isBlank() || login.isBlank()) throw new DbException("Informe nome e login.");
        if (id == null) {
            if (senha == null || senha.length() < 4) throw new DbException("A senha precisa ter pelo menos 4 caracteres.");
            Db.executar("INSERT INTO usuarios(nome, login, senha_hash, perfil, ativo, criado_em) VALUES (?,?,?,?,?,?)",
                    nome.trim(), login.trim().toLowerCase(), Senha.hash(senha), perfil, ativo, LocalDateTime.now());
        } else {
            if (!ativo && id.equals(Sessao.id())) throw new DbException("Você não pode desativar o próprio usuário.");
            Db.executar("UPDATE usuarios SET nome=?, login=?, perfil=?, ativo=? WHERE id=?",
                    nome.trim(), login.trim().toLowerCase(), perfil, ativo, id);
            if (senha != null && !senha.isBlank()) alterarSenha(id, senha);
        }
    }

    public static void alterarSenha(long id, String nova) {
        if (nova == null || nova.length() < 4) throw new DbException("A senha precisa ter pelo menos 4 caracteres.");
        Db.executar("UPDATE usuarios SET senha_hash=? WHERE id=?", Senha.hash(nova), id);
        LembrarLogin.cancelarDoUsuario(id);
    }
}
