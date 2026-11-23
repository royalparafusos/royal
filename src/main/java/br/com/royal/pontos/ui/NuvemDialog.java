package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.util.AppConfig;
import br.com.royal.pontos.util.Nuvem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Tela "Conectar à nuvem da Royal": aparece quando o computador ainda não está ligado à nuvem.
 * Basta a senha do banco (o endereço é descoberto sozinho) ou o código de conexão de outro computador.
 */
public final class NuvemDialog {

    private NuvemDialog() {}

    /** Retorna true se o computador ficou configurado (nuvem, ou demonstração escolhida de propósito). */
    public static boolean abrir() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Conectar à nuvem da Royal");
        d.setHeaderText("Conecte este computador à nuvem da Royal\npara usar os mesmos clientes e pontos de todos os computadores da loja.");
        if (App.janela() != null && App.janela().isShowing()) d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());

        ToggleGroup g = new ToggleGroup();
        RadioButton porSenha = new RadioButton("Tenho a senha do banco de dados (Supabase)");
        RadioButton porCodigo = new RadioButton("Tenho o código de conexão de outro computador da loja");
        porSenha.setToggleGroup(g);
        porCodigo.setToggleGroup(g);
        porSenha.setSelected(true);

        PasswordField senha = new PasswordField();
        senha.setPromptText("Senha do banco de dados");
        VBox caixaSenha = new VBox(6, senha, Ui.label("O programa encontra o endereço do banco sozinho.", "texto-suave"));
        caixaSenha.visibleProperty().bind(porSenha.selectedProperty());
        caixaSenha.managedProperty().bind(porSenha.selectedProperty());

        TextArea codigo = new TextArea();
        codigo.setPromptText("Cole aqui o código (começa com ROYAL1-)");
        codigo.setWrapText(true);
        codigo.setPrefRowCount(3);
        Label dicaCodigo = Ui.label("No computador que já está na nuvem: Configurações › Banco de dados › \"Copiar código de conexão\".", "texto-suave");
        dicaCodigo.setWrapText(true);
        VBox caixaCodigo = new VBox(6, codigo, dicaCodigo);
        caixaCodigo.visibleProperty().bind(porCodigo.selectedProperty());
        caixaCodigo.managedProperty().bind(porCodigo.selectedProperty());

        Label status = Ui.label("", "texto-suave");
        status.setWrapText(true);
        Hyperlink demo = new Hyperlink("Usar só neste computador (apenas para teste, os dados não vão para a nuvem)");

        VBox box = new VBox(12, porSenha, caixaSenha, porCodigo, caixaCodigo, status, demo);
        box.setPadding(new Insets(4, 0, 0, 0));
        box.setPrefWidth(560);
        d.getDialogPane().setContent(box);
        ButtonType conectar = new ButtonType("Conectar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(conectar, new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button btn = (Button) d.getDialogPane().lookupButton(conectar);

        final boolean[] pronto = {false};
        demo.setOnAction(e -> {
            if (!Ui.confirmar("Usar só neste computador?", "Os lançamentos feitos aqui NÃO aparecerão nos outros computadores "
                    + "nem no site do cliente.\n\nVocê pode conectar à nuvem depois em Configurações.")) return;
            AppConfig cfg = AppConfig.carregar();
            cfg.setNuvem(false);
            cfg.setDemoEscolhido(true);
            if (Ui.tentar(() -> salvar(cfg))) { pronto[0] = true; d.setResult(ButtonType.CLOSE); d.close(); }
        });

        btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume(); // só fecha quando conectar de verdade
            String s = senha.getText(), c = codigo.getText();
            boolean usarCodigo = porCodigo.isSelected();
            if (usarCodigo ? c.isBlank() : s.isBlank()) { status.setText(usarCodigo ? "Cole o código de conexão." : "Digite a senha do banco."); return; }
            btn.setDisable(true);
            status.setText("Conectando...");
            Ui.async(() -> { try {
                Nuvem.Dados dados = usarCodigo ? Nuvem.lerCodigo(c)
                        : Nuvem.descobrir(s, msg -> javafx.application.Platform.runLater(() -> status.setText(msg)));
                AppConfig cfg = AppConfig.carregar();
                cfg.setNuvem(true);
                cfg.setDemoEscolhido(false);
                cfg.setNuvem(dados.host(), dados.porta(), dados.banco(), dados.usuario(), dados.senha());
                Db.testar(cfg);
                salvar(cfg);
                return "";
            } catch (Exception ex) { return ex.getMessage() == null ? ex.toString() : ex.getMessage(); } }, erro -> {
                btn.setDisable(false);
                if (erro.isEmpty()) { pronto[0] = true; d.setResult(conectar); d.close(); }
                else status.setText("Não conectou: " + erro);
            });
        });

        d.showAndWait();
        return pronto[0];
    }

    private static void salvar(AppConfig cfg) {
        try { cfg.salvar(); } catch (Exception ex) { throw new RuntimeException("Não foi possível salvar: " + ex.getMessage()); }
    }
}
