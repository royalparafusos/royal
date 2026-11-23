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
        AppConfig inicial = AppConfig.carregar();
        if (!inicial.isNuvem() && !inicial.demoEscolhido()) {
            if (!br.com.royal.pontos.ui.NuvemDialog.abrir()) { janela.close(); return false; }
        }
        while (true) {
            try {
                AppConfig cfg = AppConfig.carregar();
                Db.configurar(cfg);
                Db.inicializar();
                Config.garantirPadroes();
                br.com.royal.pontos.service.Migracoes.aplicar();
                PontosService.processarExpiracoes();
                if (Db.isNuvem()) oferecerSincronizacao();
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

    /** Se este computador tem lançamentos do banco local que ainda não foram para a nuvem, oferece enviar. */
    private static void oferecerSincronizacao() {
        var p = br.com.royal.pontos.service.Sincronizacao.pendente(false);
        if (p == null) return;
        boolean enviar = Ui.confirmar("Enviar dados deste computador para a nuvem?",
                "Este computador tem " + p.clientes() + " cliente(s) e " + p.movimentos() + " lançamento(s) feitos antes de conectar à nuvem.\n\n"
                        + "Deseja juntar esses dados com os da nuvem agora? Nada será duplicado: clientes são reconhecidos pelo CPF.");
        if (!enviar) { br.com.royal.pontos.service.Sincronizacao.dispensar(); return; }
        try {
            var r = br.com.royal.pontos.service.Sincronizacao.enviar();
            Ui.info("Dados enviados para a nuvem", r.clientesNovos() + " cliente(s) novo(s) e " + r.movimentosNovos()
                    + " lançamento(s) enviados." + (r.movimentosJaExistiam() > 0 ? "\n" + r.movimentosJaExistiam() + " lançamento(s) já estavam na nuvem e foram ignorados." : "")
                    + "\n\nAgora todos os computadores mostram os mesmos dados.");
        } catch (Exception e) {
            e.printStackTrace();
            Ui.erro("Não foi possível enviar os dados: " + e.getMessage() + "\n\nVocê pode tentar de novo em Configurações › Banco de dados.");
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
