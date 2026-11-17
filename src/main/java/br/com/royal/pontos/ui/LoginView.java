package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.model.Usuario;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.UsuarioService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

public class LoginView extends HBox {

    public LoginView() {
        // ---- lado da marca
        javafx.scene.image.ImageView logoImg = new javafx.scene.image.ImageView(new javafx.scene.image.Image(
                LoginView.class.getResource("/img/logo-branco.png").toExternalForm(), 0, 260, true, true));
        logoImg.setPreserveRatio(true);
        logoImg.setFitHeight(130);
        logoImg.setSmooth(true);
        StackPane logo = new StackPane(logoImg);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setMaxWidth(Region.USE_PREF_SIZE);

        Label titulo = Ui.label("Royal Pontos", "login-titulo");
        Label slogan = Ui.label("O programa de fidelidade da " + Config.get(Config.LOJA_NOME) + ".\nCada compra vira pontos. Cada ponto vira prêmio.", "login-slogan");
        slogan.setWrapText(true);

        VBox itens = new VBox(14,
                item(Icones.CARRINHO, "Lance compras e pontue clientes em segundos"),
                item(Icones.PRESENTE, "Troque pontos por produtos ou descontos"),
                item(Icones.SYNC, "Integração com o Bling"),
                item(Icones.CHAT, "Cliente consulta o saldo pelo celular"));

        VBox marca = new VBox(26, logo, new VBox(10, titulo, slogan), itens);
        marca.setAlignment(Pos.CENTER_LEFT);
        marca.setMaxWidth(480);

        Circle b1 = new Circle(180); b1.getStyleClass().add("bolha");
        Circle b2 = new Circle(120); b2.getStyleClass().add("bolha");
        StackPane.setAlignment(b1, Pos.TOP_RIGHT);
        StackPane.setAlignment(b2, Pos.BOTTOM_LEFT);
        b1.setTranslateX(90); b1.setTranslateY(-90);
        b2.setTranslateX(-50); b2.setTranslateY(60);
        StackPane lado = new StackPane(b1, b2, marca);
        lado.getStyleClass().add("login-lado");
        lado.setMinWidth(0);
        HBox.setHgrow(lado, Priority.ALWAYS);
        lado.setClip(new javafx.scene.shape.Rectangle());
        lado.layoutBoundsProperty().addListener((o, a, n) -> {
            var r = (javafx.scene.shape.Rectangle) lado.getClip();
            r.setWidth(n.getWidth()); r.setHeight(n.getHeight());
        });

        // ---- formulário
        TextField login = Ui.campoTexto("Seu usuário");
        PasswordField senha = new PasswordField();
        senha.setPromptText("Sua senha");
        Label erro = Ui.label("", "erro-texto");
        Button entrar = Ui.botao("Entrar", "btn-primario", null);
        entrar.getStyleClass().add("btn-grande");
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);

        entrar.setOnAction(e -> {
            erro.setText("");
            if (login.getText().isBlank() || senha.getText().isEmpty()) { erro.setText("Informe usuário e senha."); return; }
            try {
                Usuario u = UsuarioService.autenticar(login.getText(), senha.getText());
                if (u == null) { erro.setText("Usuário ou senha incorretos."); senha.clear(); return; }
                boolean senhaPadrao = "admin".equals(senha.getText());
                App.mostrarPrincipal(u);
                if (senhaPadrao) MinhaSenhaDialog.abrir(true);
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
            }
        });

        Button conexao = Ui.botao(Db.isNuvem() ? "Banco: nuvem (Supabase)" : "Banco: este computador (demonstração)", "btn-link",
                Db.isNuvem() ? Icones.NUVEM : Icones.COMPUTADOR);
        conexao.setOnAction(e -> { if (ConexaoDialog.abrir()) App.reiniciarConexao(); });

        Label primeira = Ui.label("Primeiro acesso: usuário admin, senha admin (você vai trocar ao entrar).", "texto-suave");
        primeira.setWrapText(true);

        VBox form = new VBox(18,
                new VBox(6, Ui.label("Bem-vindo de volta 👋", "login-bemvindo"), Ui.label("Entre para acessar o painel da loja.", "texto-suave")),
                Ui.campo("Usuário", login), Ui.campo("Senha", senha), erro, entrar, primeira, Ui.espaco(), conexao);
        form.setMaxWidth(360);
        form.setAlignment(Pos.CENTER_LEFT);
        VBox lateral = new VBox(form);
        lateral.setAlignment(Pos.CENTER);
        lateral.getStyleClass().add("login-form");
        lateral.setMinWidth(480);
        lateral.setPrefWidth(500);
        VBox.setVgrow(form, Priority.ALWAYS);
        form.setPadding(new Insets(40, 0, 0, 0));

        getChildren().addAll(lado, lateral);
        javafx.application.Platform.runLater(login::requestFocus);
    }

    private HBox item(String icone, String texto) {
        HBox h = new HBox(14, Ui.iconeCirculo(icone, "branco", 38), Ui.label(texto, "login-item"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
