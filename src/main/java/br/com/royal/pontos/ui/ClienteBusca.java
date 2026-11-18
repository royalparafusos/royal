package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.service.ClienteService;
import br.com.royal.pontos.util.Formatos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Busca do cliente pelo CPF, com atalho para cadastrar se não existir. */
public class ClienteBusca extends VBox {

    private final TextField cpf = Ui.campoCpf();
    private final VBox resultado = new VBox();
    private final Consumer<Cliente> aoSelecionar;
    private Cliente cliente;

    public ClienteBusca(Consumer<Cliente> aoSelecionar) {
        this.aoSelecionar = aoSelecionar;
        setSpacing(14);
        cpf.getStyleClass().add("campo-grande");
        cpf.setOnAction(e -> buscar());
        Button buscar = Ui.botao("Buscar", "btn-primario", Icones.BUSCA);
        buscar.setMinHeight(52);
        buscar.setOnAction(e -> buscar());
        HBox linha = new HBox(10, cpf, buscar);
        HBox.setHgrow(cpf, Priority.ALWAYS);
        linha.setAlignment(Pos.CENTER_LEFT);
        cpf.textProperty().addListener((o, a, n) -> {
            if (cliente != null && !Formatos.digitos(n).equals(cliente.cpf())) limpar();
            if (Formatos.digitos(n).length() == 11 && cliente == null) buscar();
        });
        getChildren().addAll(Ui.campo("CPF do cliente", linha), resultado);
    }

    public Cliente cliente() { return cliente; }

    public void focar() { javafx.application.Platform.runLater(cpf::requestFocus); }

    public void definirCpf(String valor) {
        cpf.setText(valor);
        javafx.application.Platform.runLater(this::buscar);
    }

    public void recarregar() {
        if (cliente == null) return;
        long id = cliente.id();
        Ui.async(() -> ClienteService.porId(id), c -> { if (c != null) selecionar(c); });
    }

    private void limpar() {
        cliente = null;
        resultado.getChildren().clear();
        aoSelecionar.accept(null);
    }

    private void buscar() {
        String d = Formatos.digitos(cpf.getText());
        if (d.length() != 11) { mostrarAviso("Digite os 11 números do CPF.", false); return; }
        if (!Formatos.cpfValido(d)) { mostrarAviso("CPF inválido. Confira os números digitados.", false); return; }
        Ui.async(() -> ClienteService.porCpf(d), c -> {
            if (c == null || !c.ativo()) mostrarAviso("Cliente ainda não participa do programa.", true);
            else selecionar(c);
        });
    }

    private void selecionar(Cliente c) {
        cliente = c;
        Label nome = Ui.nome(c.nome(), "texto-forte", 400);
        Label doc = Ui.nome(Formatos.cpf(c.cpf()) + "  ·  " + Formatos.telefone(c.telefone()), "texto-suave", 400);
        VBox info = new VBox(2, nome, doc);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);
        VBox saldo = new VBox(0, Ui.label("Saldo atual", "texto-suave"), Ui.label(Formatos.pontos(c.saldo()), "texto-roxo"));
        saldo.setAlignment(Pos.CENTER_RIGHT);
        HBox chip = new HBox(12, Ui.avatar(Formatos.iniciais(c.nome()), 42), info, Ui.espaco(), saldo);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("cliente-chip");
        resultado.getChildren().setAll(chip);
        aoSelecionar.accept(c);
    }

    private void mostrarAviso(String texto, boolean oferecerCadastro) {
        cliente = null;
        aoSelecionar.accept(null);
        HBox aviso = new HBox(12, Ui.label(texto, ""));
        aviso.setAlignment(Pos.CENTER_LEFT);
        aviso.getStyleClass().add("aviso");
        if (oferecerCadastro) {
            Button cad = Ui.botao("Cadastrar agora", "btn-ouro", Icones.ADD_PESSOA);
            cad.setOnAction(e -> {
                Cliente novo = ClienteDialog.abrir(null, cpf.getText());
                if (novo != null) { cpf.setText(Formatos.cpf(novo.cpf())); selecionar(novo); }
            });
            aviso.getChildren().addAll(Ui.espaco(), cad);
        }
        resultado.getChildren().setAll(aviso);
    }
}
