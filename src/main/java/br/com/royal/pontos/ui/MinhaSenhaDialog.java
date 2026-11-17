package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.service.Sessao;
import br.com.royal.pontos.service.UsuarioService;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class MinhaSenhaDialog {
    private MinhaSenhaDialog() {}

    public static void abrir(boolean obrigatorio) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Alterar senha");
        d.setHeaderText(obrigatorio ? "Por segurança, crie uma nova senha" : "Alterar minha senha");
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());
        PasswordField nova = new PasswordField();
        PasswordField conf = new PasswordField();
        Label erro = Ui.label("", "erro-texto");
        VBox box = new VBox(12, Ui.campo("Nova senha", nova), Ui.campo("Confirme a nova senha", conf), erro);
        box.setPrefWidth(380);
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType("Salvar senha", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().add(ok);
        if (!obrigatorio) d.getDialogPane().getButtonTypes().add(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button btn = (Button) d.getDialogPane().lookupButton(ok);
        btn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!nova.getText().equals(conf.getText())) { erro.setText("As senhas não conferem."); e.consume(); return; }
            if ("admin".equals(nova.getText())) { erro.setText("Escolha uma senha diferente de \"admin\"."); e.consume(); return; }
            try {
                UsuarioService.alterarSenha(Sessao.id(), nova.getText());
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
                e.consume();
            }
        });
        if (obrigatorio) d.setOnCloseRequest(e -> { if (d.getResult() != ok) e.consume(); });
        d.showAndWait().ifPresent(r -> { if (r == ok) Ui.toast("Senha alterada com sucesso"); });
    }
}
