package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.db.Db;
import br.com.royal.pontos.util.AppConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Escolha entre banco local (demonstração) e Supabase (nuvem). */
public final class ConexaoDialog {

    private ConexaoDialog() {}

    /** Retorna true se o usuário salvou uma nova configuração. */
    public static boolean abrir() {
        AppConfig cfg = AppConfig.carregar();
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Conexão com o banco de dados");
        d.setHeaderText("Onde os dados do programa ficam guardados?");
        if (App.janela() != null && App.janela().isShowing()) d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());

        ToggleGroup g = new ToggleGroup();
        RadioButton local = new RadioButton("Neste computador (modo demonstração, sem internet)");
        RadioButton nuvem = new RadioButton("Na nuvem — Supabase (recomendado: permite a consulta do cliente pelo celular)");
        local.setToggleGroup(g);
        nuvem.setToggleGroup(g);
        (cfg.isNuvem() ? nuvem : local).setSelected(true);

        TextField host = Ui.campoTexto("aws-0-sa-east-1.pooler.supabase.com");
        host.setText(cfg.host());
        TextField porta = Ui.campoTexto("5432");
        porta.setText(cfg.porta());
        TextField banco = Ui.campoTexto("postgres");
        banco.setText(cfg.banco());
        TextField usuario = Ui.campoTexto("postgres.xxxxxxxxxxxx");
        usuario.setText(cfg.usuario());
        PasswordField senha = new PasswordField();
        senha.setText(cfg.senha());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(Ui.campo("Host", host), 0, 0, 2, 1);
        grid.add(Ui.campo("Porta", porta), 0, 1);
        grid.add(Ui.campo("Banco", banco), 1, 1);
        grid.add(Ui.campo("Usuário", usuario), 0, 2, 2, 1);
        grid.add(Ui.campo("Senha do banco", senha), 0, 3, 2, 1);
        grid.disableProperty().bind(local.selectedProperty());

        Label status = Ui.label("", "texto-suave");
        Button testar = Ui.botao("Testar conexão", "btn-secundario", Icones.SYNC);
        testar.disableProperty().bind(local.selectedProperty());
        testar.setOnAction(e -> {
            AppConfig t = AppConfig.carregar();
            t.setNuvem(true);
            t.setNuvem(host.getText(), porta.getText(), banco.getText(), usuario.getText(), senha.getText());
            status.setText("Testando...");
            Ui.async(() -> { Db.testar(t); return true; }, ok -> status.setText("✓ Conexão funcionando!"));
        });

        Label dica = Ui.label("No Supabase: Project Settings › Database › Connection string › \"Session pooler\". "
                + "Copie host, porta e usuário de lá. Antes, rode o arquivo supabase/schema.sql no SQL Editor.", "texto-suave");
        dica.setWrapText(true);
        dica.setMaxWidth(520);

        VBox box = new VBox(12, local, nuvem, grid, new HBox(12, testar, status), dica);
        box.setPadding(new Insets(4, 0, 0, 0));
        box.setPrefWidth(560);
        d.getDialogPane().setContent(box);
        ButtonType salvar = new ButtonType("Salvar e reconectar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(salvar, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));

        var r = d.showAndWait();
        if (r.isEmpty() || r.get() != salvar) return false;
        cfg.setNuvem(nuvem.isSelected());
        cfg.setDemoEscolhido(!nuvem.isSelected());
        cfg.setNuvem(host.getText(), porta.getText(), banco.getText(), usuario.getText(), senha.getText());
        return Ui.tentar(() -> {
            try { cfg.salvar(); } catch (Exception ex) { throw new RuntimeException("Não foi possível salvar: " + ex.getMessage()); }
        });
    }
}
