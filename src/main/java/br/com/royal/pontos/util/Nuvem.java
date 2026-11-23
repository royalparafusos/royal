package br.com.royal.pontos.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Conexão com a nuvem da Royal (Supabase) sem precisar digitar host e usuário:
 *  - só com a senha do banco: o programa descobre sozinho o endereço (pooler) do projeto;
 *  - ou com um "código de conexão" gerado em outro computador já conectado.
 */
public final class Nuvem {

    /** Projeto Supabase da Royal (o mesmo usado pela página do cliente). */
    public static final String PROJETO = "yxgjjeupeasxncrywdre";
    private static final String PREFIXO = "ROYAL1-";
    private static final byte[] MISTURA = "RoyalPontos-Salinas-MG".getBytes(StandardCharsets.UTF_8);

    private static final String[] REGIOES = {
            "sa-east-1", "us-east-1", "us-east-2", "us-west-1", "us-west-2", "ca-central-1",
            "eu-west-1", "eu-west-2", "eu-west-3", "eu-central-1", "eu-central-2", "eu-north-1",
            "ap-south-1", "ap-southeast-1", "ap-southeast-2", "ap-northeast-1", "ap-northeast-2"};

    private Nuvem() {}

    public record Dados(String host, String porta, String banco, String usuario, String senha) {}

    /** Descobre o endereço do banco testando os servidores do Supabase com a senha informada. */
    public static Dados descobrir(String senha, java.util.function.Consumer<String> progresso) throws SQLException {
        try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException e) { throw new SQLException(e); }
        String usuario = "postgres." + PROJETO;
        List<String> hosts = new ArrayList<>();
        for (String r : REGIOES) { hosts.add("aws-0-" + r + ".pooler.supabase.com"); hosts.add("aws-1-" + r + ".pooler.supabase.com"); }
        SQLException ultimo = null;
        int i = 0;
        for (String h : hosts) {
            i++;
            if (progresso != null) progresso.accept("Procurando o banco da loja... (" + i + "/" + hosts.size() + ")");
            String url = "jdbc:postgresql://" + h + ":5432/postgres?sslmode=require&prepareThreshold=0&connectTimeout=8&loginTimeout=10";
            try (Connection c = DriverManager.getConnection(url, usuario, senha)) {
                return new Dados(h, "5432", "postgres", usuario, senha);
            } catch (SQLException e) {
                String m = String.valueOf(e.getMessage()).toLowerCase();
                if ("28P01".equals(e.getSQLState()) || m.contains("password authentication failed")) {
                    throw new SQLException("Senha do banco incorreta. Confira a senha do banco de dados do Supabase.");
                }
                ultimo = e;
            }
        }
        throw new SQLException("Não foi possível encontrar o banco da loja. Verifique a internet."
                + (ultimo != null ? "\n(" + ultimo.getMessage() + ")" : ""));
    }

    /** Código para colar em outro computador da loja (contém a conexão, embaralhada). */
    public static String gerarCodigo(AppConfig cfg) {
        JsonObject j = new JsonObject();
        j.addProperty("h", cfg.host());
        j.addProperty("p", cfg.porta());
        j.addProperty("b", cfg.banco());
        j.addProperty("u", cfg.usuario());
        j.addProperty("s", cfg.senha());
        byte[] b = misturar(new Gson().toJson(j).getBytes(StandardCharsets.UTF_8));
        return PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static Dados lerCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.replaceAll("\\s", "");
        if (!c.startsWith(PREFIXO)) throw new IllegalArgumentException("Código de conexão inválido. Ele começa com " + PREFIXO);
        try {
            byte[] b = misturar(Base64.getUrlDecoder().decode(c.substring(PREFIXO.length())));
            JsonObject j = new Gson().fromJson(new String(b, StandardCharsets.UTF_8), JsonObject.class);
            return new Dados(j.get("h").getAsString(), j.get("p").getAsString(), j.get("b").getAsString(),
                    j.get("u").getAsString(), j.get("s").getAsString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Código de conexão inválido ou incompleto. Copie o código inteiro novamente.");
        }
    }

    private static byte[] misturar(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) out[i] = (byte) (in[i] ^ MISTURA[i % MISTURA.length]);
        return out;
    }
}
