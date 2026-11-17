package br.com.royal.pontos;

/** Ponto de entrada (necessário para rodar o .jar com JavaFX embutido). */
public class Launcher {
    public static void main(String[] args) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"));
        App.launch(App.class, args);
    }
}
