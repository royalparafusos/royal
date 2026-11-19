package br.com.royal.pontos;

/** Versão do programa. A cada atualização publicada, este número sobe junto com a tag no GitHub (ex.: v1.2.0). */
public final class Versao {
    public static final String ATUAL = "1.5.3";

    /** Repositório no GitHub onde as versões novas são publicadas (usuario/repositorio). */
    public static final String REPOSITORIO = "royalparafusos/royal";

    private Versao() {}

    public static boolean repositorioConfigurado() {
        return !REPOSITORIO.startsWith("SEU-USUARIO");
    }

    /** Compara "1.2.10" com "1.10.0" numericamente. */
    public static int comparar(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("\\."), y = b.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
