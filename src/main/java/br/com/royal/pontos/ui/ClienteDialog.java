package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.service.ClienteService;
import br.com.royal.pontos.util.Formatos;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Cadastro / edição de cliente. Retorna o cliente salvo ou null. */
public final class ClienteDialog {

    private ClienteDialog() {}

    public static Cliente abrir(Cliente existente, String cpfInicial) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(existente == null ? "Novo cliente" : "Editar cliente");
        d.setHeaderText(existente == null ? "Cadastrar cliente no programa" : "Editar dados do cliente");
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());

        TextField nome = Ui.campoTexto("Nome completo");
        TextField cpf = Ui.campoCpf();
        TextField tel = Ui.campoTelefone();
        TextField email = Ui.campoTexto("email@exemplo.com (opcional)");
        DatePicker nasc = new DatePicker();
        nasc.setPromptText("dd/mm/aaaa (opcional)");
        CheckBox aceite = new CheckBox("O cliente autorizou participar do programa e receber mensagens (LGPD)");
        aceite.setWrapText(true);
        Label erro = Ui.label("", "erro-texto");
        erro.setWrapText(true);

        if (existente != null) {
            nome.setText(existente.nome());
            cpf.setText(Formatos.cpf(existente.cpf()));
            tel.setText(Formatos.telefone(existente.telefone()));
            email.setText(existente.email() == null ? "" : existente.email());
            nasc.setValue(existente.nascimento());
            aceite.setSelected(existente.aceiteLgpd());
        } else if (cpfInicial != null) {
            cpf.setText(cpfInicial);
        }

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);
        g.add(Ui.campo("Nome completo *", nome), 0, 0, 2, 1);
        g.add(Ui.campo("CPF *", cpf), 0, 1);
        g.add(Ui.campo("Celular / WhatsApp *", tel), 1, 1);
        g.add(Ui.campo("E-mail", email), 0, 2);
        g.add(Ui.campo("Data de nascimento", nasc), 1, 2);
        VBox box = new VBox(14, g, aceite, erro);
        box.setPrefWidth(540);
        d.getDialogPane().setContent(box);

        ButtonType salvar = new ButtonType("Salvar cliente", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(salvar, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        Cliente[] resultado = new Cliente[1];
        d.getDialogPane().lookupButton(salvar).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                resultado[0] = ClienteService.salvar(existente == null ? null : existente.id(), nome.getText(), cpf.getText(),
                        tel.getText(), email.getText(), nasc.getValue(), aceite.isSelected());
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
                e.consume();
            }
        });
        javafx.application.Platform.runLater(nome::requestFocus);
        d.showAndWait();
        if (resultado[0] != null) Ui.toast(existente == null ? "Cliente cadastrado!" : "Dados atualizados");
        return resultado[0];
    }
}
