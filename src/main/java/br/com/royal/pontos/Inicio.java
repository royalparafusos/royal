package br.com.royal.pontos;

import br.com.royal.pontos.service.Atualizador;

import java.nio.file.Path;

/**
 * Ponto de entrada do Royal Pontos.exe.
 * Se já existe uma versão mais nova baixada, abre ela; senão abre a versão instalada.
 */
public class Inicio {
    public static void main(String[] args) {
        if (System.getProperty("royal.versao.baixada") == null) {
            try {
                Path nova = Atualizador.versaoBaixadaMaisNova();
                if (nova != null && Atualizador.abrirVersao(nova)) return;
            } catch (Exception e) {
                e.printStackTrace(); // em caso de falha, segue com a versão instalada
            }
        }
        Launcher.main(args);
    }
}
