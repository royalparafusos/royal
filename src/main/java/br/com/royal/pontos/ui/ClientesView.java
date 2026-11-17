package br.com.royal.pontos.ui;

import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.service.ClienteService;
import br.com.royal.pontos.util.Formatos;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

public class ClientesView extends VBox {

    private final TableView<Cliente> tabela = Tabelas.nova("Nenhum cliente encontrado.");
    private final TextField busca = Ui.campoTexto("Buscar por nome, CPF ou telefone");
    private final Label total = Ui.label("", "texto-suave");

    public ClientesView() {
        getStyleClass().add("pagina");

        HBox buscaBox = new HBox(8, Ui.icone(Icones.BUSCA, 16), busca);
        buscaBox.setAlignment(Pos.CENTER_LEFT);
        busca.setPrefWidth(360);
        Button novo = Ui.botao("Novo cliente", "btn-primario", Icones.ADD_PESSOA);
        novo.setOnAction(e -> { Cliente c = ClienteDialog.abrir(null, null); if (c != null) carregar(); });
        HBox barra = new HBox(14, buscaBox, total, Ui.espaco(), novo);
        barra.setAlignment(Pos.CENTER_LEFT);

        tabela.getColumns().add(Tabelas.colunaNo("CLIENTE", c -> {
            HBox h = new HBox(10, Ui.avatar(Formatos.iniciais(c.nome()), 30), Ui.nome(c.nome(), "texto-forte", 400));
            h.setAlignment(Pos.CENTER_LEFT);
            return h;
        }, 380));
        tabela.getColumns().add(Tabelas.coluna("CPF", c -> Formatos.cpf(c.cpf()), 120));
        tabela.getColumns().add(Tabelas.coluna("CELULAR", c -> Formatos.telefone(c.telefone()), 120));
        tabela.getColumns().add(Tabelas.colunaNo("SALDO", c -> Ui.badge(Formatos.pontos(c.saldo()), c.saldo() > 0 ? "ouro" : "cinza"), 110));
        tabela.getColumns().add(Tabelas.coluna("CLIENTE DESDE", c -> c.criadoEm() == null ? "" : Formatos.data(c.criadoEm().toLocalDate()), 110));
        tabela.getColumns().add(Tabelas.colunaNo("", c -> {
            Button ver = Ui.botao("Ver ficha", "btn-link", null);
            ver.setOnAction(e -> MainView.atual().abrirCliente(c.id()));
            return ver;
        }, 90));
        Tabelas.aoDuploClique(tabela, c -> MainView.atual().abrirCliente(c.id()));

        VBox card = Ui.card(barra, tabela);
        VBox.setVgrow(tabela, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().add(card);

        PauseTransition atraso = new PauseTransition(Duration.millis(300));
        atraso.setOnFinished(e -> carregar());
        busca.textProperty().addListener((o, a, n) -> atraso.playFromStart());
        carregar();
    }

    private void carregar() {
        String t = busca.getText();
        Ui.async(() -> ClienteService.buscar(t), lista -> {
            tabela.getItems().setAll(lista);
            total.setText(lista.size() + " cliente(s)");
        });
    }
}
