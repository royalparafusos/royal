package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.service.Atualizador;
import br.com.royal.pontos.service.BlingService;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.Sessao;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Janela principal: menu lateral + topo + área de conteúdo. */
public class MainView extends BorderPane {

    public enum Tela { PAINEL, COMPRA, RESGATE, CLIENTES, PREMIOS, BLING, CONFIG }

    private static MainView atual;
    private final Map<Tela, Button> botoes = new LinkedHashMap<>();
    private final StackPane conteudo = new StackPane();
    private final Label titulo = Ui.label("", "pagina-titulo");
    private final Label subtitulo = Ui.label("", "pagina-sub");

    public static MainView atual() { return atual; }

    public MainView() {
        atual = this;
        Usuario u = Sessao.usuario();

        // ---- menu
        javafx.scene.image.ImageView logoImg = new javafx.scene.image.ImageView(new javafx.scene.image.Image(
                MainView.class.getResource("/img/logo-branco.png").toExternalForm(), 0, 160, true, true));
        logoImg.setPreserveRatio(true);
        logoImg.setFitWidth(190);
        logoImg.setSmooth(true);
        StackPane logo = new StackPane(logoImg);
        logo.setStyle("-fx-padding: 4 0 0 0;");
        VBox marca = new VBox(8, logo, Ui.label("PROGRAMA DE FIDELIDADE", "marca-sub"));
        marca.setAlignment(Pos.CENTER);
        marca.setStyle("-fx-padding: 0 0 10 0;");

        VBox menu = new VBox(4);
        menu.getStyleClass().add("sidebar");
        menu.getChildren().addAll(marca, Ui.label("OPERAÇÃO", "menu-secao"),
                nav(Tela.PAINEL, "Painel", Icones.PAINEL),
                nav(Tela.COMPRA, "Lançar compra", Icones.CARRINHO),
                nav(Tela.RESGATE, "Resgatar pontos", Icones.PRESENTE),
                nav(Tela.CLIENTES, "Clientes", Icones.PESSOAS),
                nav(Tela.PREMIOS, "Prêmios", Icones.ESTRELA));
        if (u.isAdmin()) {
            menu.getChildren().addAll(Ui.label("GESTÃO", "menu-secao"),
                    nav(Tela.BLING, "Integração Bling", Icones.SYNC),
                    nav(Tela.CONFIG, "Configurações", Icones.CONFIG));
        }

        Button sair = new Button();
        sair.setGraphic(Ui.icone(Icones.SAIR, 18));
        sair.getStyleClass().add("btn-sair");
        sair.setTooltip(new Tooltip("Sair"));
        sair.setOnAction(e -> App.mostrarLogin());
        Label nome = Ui.nome(u.nome(), "usuario-nome", 120);
        VBox info = new VBox(1, nome, Ui.label(u.perfilTexto(), "usuario-perfil"));
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setMinWidth(0);
        HBox usuario = new HBox(10, Ui.avatar(Formatos.iniciais(u.nome()), 36), info, sair);
        usuario.setAlignment(Pos.CENTER_LEFT);
        usuario.getStyleClass().add("usuario-box");
        usuario.setOnMouseClicked(e -> { if (e.getClickCount() == 2) MinhaSenhaDialog.abrir(false); });
        Tooltip.install(usuario, new Tooltip("Clique duas vezes para alterar sua senha"));
        Label versao = Ui.label("Versão " + Atualizador.versaoEmUso(), "usuario-perfil");
        HBox linhaVersao = new HBox(versao);
        linhaVersao.setAlignment(Pos.CENTER);
        menu.getChildren().addAll(Ui.espaco(), usuario, linhaVersao);

        // ---- topo
        Label banco = Db.isNuvem() ? Ui.badge("● Nuvem conectada", "verde") : Ui.badge("● Banco local (demonstração)", "ouro");
        Button atalho = Ui.botao("Lançar compra", "btn-ouro", Icones.MAIS);
        atalho.setOnAction(e -> ir(Tela.COMPRA));
        atualizacao.setVisible(false);
        atualizacao.setManaged(false);
        atualizacao.setOnAction(e -> {
            if (!Atualizador.reiniciar()) Ui.info("Atualização pronta", "Feche o Royal Pontos e abra de novo para usar a versão nova.");
        });
        HBox topo = new HBox(16, new VBox(2, titulo, subtitulo), Ui.espaco(), atualizacao, banco, atalho);
        topo.setAlignment(Pos.CENTER_LEFT);
        topo.getStyleClass().add("topo");

        StackPane area = new StackPane(conteudo);
        Ui.definirToastHost(area);
        VBox centro = new VBox(topo, area);
        VBox.setVgrow(area, Priority.ALWAYS);

        setLeft(menu);
        setCenter(centro);
        ir(Tela.PAINEL);
        importarBlingAutomatico();
        verificarAtualizacao(false);
        fazerBackup(false);
        javafx.animation.Timeline backup = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(30), e -> fazerBackup(false)));
        backup.setCycleCount(javafx.animation.Animation.INDEFINITE);
        backup.play();
        sceneProperty().addListener((o, a, n) -> { if (n == null) backup.stop(); });
    }

    /** Atualiza o backup em Excel dos saldos (Documentos\Royal Pontos\Backups). */
    public void fazerBackup(boolean avisar) {
        Ui.async(br.com.royal.pontos.service.BackupService::executar,
                arquivo -> { if (avisar) Ui.toast("Backup salvo em " + arquivo.getParent()); });
    }

    private final Button atualizacao = Ui.botao("Atualização pronta — reiniciar", "btn-verde", Icones.SYNC);

    /** Procura versão nova em segundo plano; se baixar, mostra o botão para reiniciar. */
    public void verificarAtualizacao(boolean avisarSeAtualizado) {
        Ui.async(Atualizador::verificarEBaixar, nova -> {
            if (nova != null || Atualizador.versaoBaixadaMaisNova() != null && System.getProperty("royal.versao.baixada") == null) {
                atualizacao.setText("Versão " + (nova != null ? nova : "nova") + " pronta — reiniciar");
                atualizacao.setVisible(true);
                atualizacao.setManaged(true);
                Ui.toast("Nova versão do Royal Pontos baixada. Ela abre automaticamente na próxima vez, ou clique em Reiniciar.");
            } else if (avisarSeAtualizado) {
                Ui.toast("Você já está na versão mais recente (" + Atualizador.versaoEmUso() + ").");
            }
        });
    }

    private Button nav(Tela tela, String texto, String icone) {
        Button b = new Button(texto, Ui.icone(icone, 18));
        b.getStyleClass().add("nav");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> ir(tela));
        botoes.put(tela, b);
        return b;
    }

    public void ir(Tela tela) { ir(tela, null); }

    /** Navega para uma tela. "param" pode ser um CPF (compra/resgate). */
    public void ir(Tela tela, String param) {
        botoes.values().forEach(b -> b.getStyleClass().remove("nav-ativo"));
        Button b = botoes.get(tela);
        if (b != null) b.getStyleClass().add("nav-ativo");
        switch (tela) {
            case PAINEL -> mostrar("Olá, " + Formatos.primeiroNome(Sessao.usuario().nome()) + "!", "Resumo do programa de fidelidade da " + Config.get(Config.LOJA_NOME), new PainelView());
            case COMPRA -> mostrar("Lançar compra", "Identifique o cliente e informe o valor para creditar os pontos", new CompraView(param));
            case RESGATE -> mostrar("Resgatar pontos", "Troque os pontos do cliente por produtos ou descontos", new ResgateView(param));
            case CLIENTES -> mostrar("Clientes", "Participantes do programa de fidelidade", new ClientesView());
            case PREMIOS -> mostrar("Catálogo de prêmios", "Produtos que podem ser trocados por pontos", new PremiosView());
            case BLING -> mostrar("Integração Bling", "Importe vendas e produtos automaticamente", new BlingView());
            case CONFIG -> mostrar("Configurações", "Regras do programa, forma de resgate e usuários", new ConfiguracoesView());
        }
    }

    public void abrirCliente(long clienteId) {
        botoes.values().forEach(b -> b.getStyleClass().remove("nav-ativo"));
        botoes.get(Tela.CLIENTES).getStyleClass().add("nav-ativo");
        mostrar("Ficha do cliente", "Saldo, validade e extrato de pontos", new ClienteDetalheView(clienteId));
    }

    private void mostrar(String t, String s, Node view) {
        titulo.setText(t);
        subtitulo.setText(s);
        conteudo.getChildren().setAll(view);
    }

    private void importarBlingAutomatico() {
        if (!Config.bool(Config.BLING_AUTO_IMPORTAR) || !BlingService.conectado()) return;
        Ui.async(() -> BlingService.importarPendentes(msg -> {}), r -> {
            if (r.creditados() > 0) Ui.toast("Bling: " + r.creditados() + " venda(s) pontuada(s) automaticamente");
        });
    }
}
