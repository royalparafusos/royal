package br.com.royal.pontos;

import br.com.royal.pontos.service.Atualizador;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Ponto de entrada do Royal Pontos.exe.
 * Se já existe uma versão mais nova baixada, carrega ela aqui mesmo (sem abrir outro processo);
 * senão abre a versão instalada.
 */
public class Inicio {
    public static void main(String[] args) {
        if (System.getProperty("royal.versao.baixada") == null) {
            Path nova = null;
            try {
                nova = Atualizador.versaoBaixadaMaisNova();
            } catch (Throwable e) {
                Atualizador.log("Erro ao procurar versão baixada: " + e);
            }
            if (nova != null) {
                try {
                    Atualizador.log("Abrindo versão baixada: " + nova.getFileName());
                    executarVersao(nova, args);
                    return;
                } catch (Throwable e) {
                    Atualizador.log("Falha ao abrir versão baixada, usando a instalada: " + e);
                    System.clearProperty("royal.versao.baixada");
                }
            }
        }
        Launcher.main(args);
    }

    private static void executarVersao(Path jar, String[] args) throws Exception {
        System.setProperty("royal.versao.baixada", Atualizador.versaoDe(jar));
        // Carrega tudo do jar novo, sem misturar com as classes da versão instalada
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        Class<?> launcher = Class.forName("br.com.royal.pontos.Launcher", true, cl);
        launcher.getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
