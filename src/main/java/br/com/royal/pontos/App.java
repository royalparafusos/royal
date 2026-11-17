package br.com.royal.pontos;

import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.PontosService;
import br.com.royal.pontos.service.Sessao;
import br.com.royal.pontos.ui.ConexaoDialog;
import br.com.royal.pontos.ui.LoginView;
import br.com.royal.pontos.ui.MainView;
import br.com.royal.pontos.ui.Ui;
import br.com.royal.pontos.util.AppConfig;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    private static Stage janela;
    private static HostServices host;
    private static Scene cena;

    public static Stage janela() { return janela; }

    public static void abrirLink(String url) { if (host != null) host.showDocument(url); }

    @Override
    public void start(Stage stage) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"));
        janela = stage;
        host = getHostServices();
        stage.setTitle("Royal Pontos — Programa de Fidelidade");
        stage.setMinWidth(1000);
        stage.setMinHeight(620);
        // Ajusta a janela ao tamanho da tela (em telas menores abre maximizada, com a barra de título visível)
        javafx.geometry.Rectangle2D tela = javafx.stage.Screen.getPrimary().getVisualBounds();
        double largura = Math.min(1320, tela.getWidth()), altura = Math.min(840, tela.getHeight() - 40);
        cena = new Scene(new StackPane(), largura, altura);
        cena.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(cena);
        stage.getIcons().add(new javafx.scene.image.Image(App.class.getResource("/img/icone.png").toExternalForm()));
        stage.show();
        if (tela.getWidth() < 1340 || tela.getHeight() < 900) {
            stage.setMaximized(true);
        } else {
            stage.centerOnScreen();
            if (stage.getY() < tela.getMinY()) stage.setY(tela.getMinY());
        }

        if (!conectarBanco()) return;
        mostrarLogin();
    }

    /** Conecta e prepara o banco. Se falhar, oferece configurar a conexão. */
    private static boolean conectarBanco() {
        while (true) {
            try {
                AppConfig cfg = AppConfig.carregar();
                Db.configurar(cfg);
                Db.inicializar();
                Config.garantirPadroes();
                PontosService.processarExpiracoes();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                boolean configurar = Ui.confirmar("Não foi possível abrir o banco de dados",
                        e.getMessage() + "\n\nDeseja revisar a configuração de conexão?");
                if (!configurar || !ConexaoDialog.abrir()) {
                    janela.close();
                    return false;
                }
            }
        }
    }

    public static void mostrarLogin() {
        Sessao.sair();
        trocar(new LoginView());
    }

    public static void mostrarPrincipal(Usuario u) {
        Sessao.entrar(u);
        trocar(new MainView());
    }

    public static void reiniciarConexao() {
        if (conectarBanco()) mostrarLogin();
    }

    private static void trocar(Parent p) {
        cena.setRoot(p);
    }
}
