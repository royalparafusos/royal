package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.model.Premio;
import br.com.royal.pontos.service.BlingService;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.PremioService;
import br.com.royal.pontos.service.Sessao;
import br.com.royal.pontos.util.AppConfig;
import br.com.royal.pontos.util.Formatos;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PremiosView extends StackPane {

    private final FlowPane grade = new FlowPane(18, 18);
    private final TextField busca = Ui.campoTexto("Buscar prêmio");
    private final CheckBox inativos = new CheckBox("Mostrar inativos");
    private final Label total = Ui.label("", "texto-suave");

    public PremiosView() {
        HBox buscaBox = new HBox(8, Ui.icone(Icones.BUSCA, 16), busca);
        buscaBox.setAlignment(Pos.CENTER_LEFT);
        busca.setPrefWidth(300);
        busca.textProperty().addListener((o, a, n) -> carregar());
        inativos.selectedProperty().addListener((o, a, n) -> carregar());
        HBox barra = new HBox(14, buscaBox, inativos, total, Ui.espaco());
        barra.setAlignment(Pos.CENTER_LEFT);
        if (Sessao.isAdmin()) {
            Button bling = Ui.botao("Importar do Bling", "btn-secundario", Icones.SYNC);
            bling.setOnAction(e -> importarBling());
            Button novo = Ui.botao("Novo prêmio", "btn-primario", Icones.MAIS);
            novo.setOnAction(e -> { if (editar(null)) carregar(); });
            barra.getChildren().addAll(bling, novo);
        }

        Label modo = Ui.label(switch (Config.get(Config.MODO_RESGATE)) {
            case "DESCONTO" -> "⚠ A forma de resgate está configurada só para DESCONTO — o catálogo não aparece para o cliente.";
            case "PRODUTOS" -> "Forma de resgate: somente produtos do catálogo.";
            default -> "Forma de resgate: produtos do catálogo e descontos.";
        }, "texto-suave");

        VBox pagina = new VBox(18, Ui.card(barra, modo), grade);
        pagina.getStyleClass().add("pagina");
        getChildren().add(Ui.rolagem(pagina));
        carregar();
    }

    private void carregar() {
        String b = busca.getText();
        boolean in = inativos.isSelected();
        Ui.async(() -> PremioService.listar(in, b), lista -> {
            grade.getChildren().clear();
            for (Premio p : lista) grade.getChildren().add(card(p));
            total.setText(lista.size() + " prêmio(s)");
            if (lista.isEmpty()) grade.getChildren().add(Ui.card(Ui.iconeCirculo(Icones.PRESENTE, "roxo", 56),
                    Ui.label("Seu catálogo está vazio", "card-titulo"),
                    Ui.label("Cadastre prêmios manualmente ou importe produtos do Bling.", "texto-suave")));
        });
    }

    private VBox card(Premio p) {
        Label nome = Ui.label(p.nome(), "premio-nome");
        nome.setWrapText(true);
        nome.setMinHeight(38);
        nome.setMaxWidth(196);
        HBox pts = new HBox(4, Ui.label(Formatos.numero(p.pontos()), "premio-pontos"), Ui.label("pts", "premio-pts"));
        pts.setAlignment(Pos.BASELINE_LEFT);
        HBox badges = new HBox(6);
        if (!p.ativo()) badges.getChildren().add(Ui.badge("Inativo", "cinza"));
        else if (p.estoque() == 0) badges.getChildren().add(Ui.badge("Esgotado", "vermelho"));
        else badges.getChildren().add(Ui.badge(p.semControleEstoque() ? "Sem limite" : p.estoque() + " un.", "verde"));
        if ("BLING".equals(p.origem())) badges.getChildren().add(Ui.badge("Bling", "azul"));

        VBox card = new VBox(10, Ui.imagemPremio(p.imagem(), 196, 140), nome, pts, badges);
        if (p.descricao() != null && !p.descricao().isBlank()) {
            Label d = Ui.label(p.descricao(), "texto-suave");
            d.setWrapText(true);
            d.setMaxWidth(196);
            d.setMaxHeight(34);
            card.getChildren().add(3, d);
        }
        if (Sessao.isAdmin()) {
            Button ed = Ui.botao("Editar", "btn-secundario", Icones.EDITAR);
            ed.setMaxWidth(Double.MAX_VALUE);
            ed.setOnAction(e -> { if (editar(p)) carregar(); });
            card.getChildren().add(ed);
        }
        card.getStyleClass().add("premio-card");
        card.setPrefWidth(220);
        if (!p.ativo()) card.setOpacity(0.6);
        return card;
    }

    private boolean editar(Premio p) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(p == null ? "Novo prêmio" : "Editar prêmio");
        d.setHeaderText(p == null ? "Cadastrar prêmio no catálogo" : "Editar prêmio");
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());

        TextField nome = Ui.campoTexto("Ex.: Luva nitrílica (par)");
        TextArea desc = new TextArea();
        desc.setPromptText("Descrição curta (opcional)");
        desc.setPrefRowCount(2);
        desc.setWrapText(true);
        TextField pontos = Ui.campoNumero("Ex.: 500", false);
        TextField estoque = Ui.campoNumero("Quantidade", false);
        CheckBox semLimite = new CheckBox("Sem controle de estoque");
        TextField imagem = Ui.campoTexto("Caminho do arquivo ou link https://...");
        Button escolher = Ui.botao("Escolher foto", "btn-secundario", null);
        CheckBox ativo = new CheckBox("Prêmio ativo (aparece para resgate)");
        Label erro = Ui.label("", "erro-texto");
        StackPane previa = new StackPane(Ui.imagemPremio(null, 150, 110));

        if (p != null) {
            nome.setText(p.nome());
            desc.setText(p.descricao());
            pontos.setText(String.valueOf(p.pontos()));
            semLimite.setSelected(p.semControleEstoque());
            estoque.setText(p.semControleEstoque() ? "" : String.valueOf(p.estoque()));
            imagem.setText(p.imagem() == null ? "" : p.imagem());
            ativo.setSelected(p.ativo());
        } else {
            semLimite.setSelected(true);
            ativo.setSelected(true);
        }
        estoque.disableProperty().bind(semLimite.selectedProperty());
        imagem.textProperty().addListener((o, a, n) -> previa.getChildren().setAll(Ui.imagemPremio(n, 150, 110)));
        previa.getChildren().setAll(Ui.imagemPremio(imagem.getText(), 150, 110));
        escolher.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
            File f = fc.showOpenDialog(App.janela());
            if (f != null) {
                try {
                    Path destino = AppConfig.PASTA.resolve("imagens");
                    Files.createDirectories(destino);
                    Path copia = destino.resolve(System.currentTimeMillis() + "_" + f.getName());
                    Files.copy(f.toPath(), copia, StandardCopyOption.REPLACE_EXISTING);
                    imagem.setText(copia.toString());
                } catch (Exception ex) {
                    imagem.setText(f.getAbsolutePath());
                }
            }
        });

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(12);
        g.add(Ui.campo("Nome do prêmio *", nome), 0, 0, 2, 1);
        g.add(Ui.campo("Descrição", desc), 0, 1, 2, 1);
        g.add(Ui.campo("Custa (pontos) *", pontos), 0, 2);
        g.add(Ui.campo("Estoque", new VBox(6, estoque, semLimite)), 1, 2);
        HBox img = new HBox(8, imagem, escolher);
        HBox.setHgrow(imagem, Priority.ALWAYS);
        g.add(Ui.campo("Foto", img), 0, 3, 2, 1);
        Label dica = Ui.label("Dica: para a foto aparecer também na página do cliente, use um link https:// (ex.: imagem do produto no Bling).", "texto-suave");
        dica.setWrapText(true);
        dica.setMaxWidth(420);
        g.add(dica, 0, 4, 2, 1);
        HBox conteudo = new HBox(20, g, new VBox(10, previa, ativo));
        conteudo.setPrefWidth(680);
        d.getDialogPane().setContent(new VBox(10, conteudo, erro));

        ButtonType ok = new ButtonType("Salvar prêmio", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                int pts = pontos.getText().isBlank() ? 0 : Integer.parseInt(pontos.getText().trim());
                int est = semLimite.isSelected() ? -1 : (estoque.getText().isBlank() ? 0 : Integer.parseInt(estoque.getText().trim()));
                PremioService.salvar(p == null ? null : p.id(), nome.getText(), desc.getText(), pts, Math.max(-1, est),
                        imagem.getText().isBlank() ? null : imagem.getText().trim(), ativo.isSelected());
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
                e.consume();
            }
        });
        boolean salvo = d.showAndWait().filter(b -> b == ok).isPresent();
        if (salvo) Ui.toast("Prêmio salvo");
        return salvo;
    }

    // ------------------------------------------------------------------ Bling

    private void importarBling() {
        if (!BlingService.conectado()) {
            Ui.info("Bling não conectado", "Conecte o Bling primeiro em \"Integração Bling\".");
            return;
        }
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Importar produtos do Bling");
        d.setHeaderText("Escolha os produtos que podem ser trocados por pontos");
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());

        Map<BlingService.Produto, SimpleBooleanProperty> marcados = new HashMap<>();
        TableView<BlingService.Produto> t = Tabelas.nova("Busque produtos do Bling.");
        TableColumn<BlingService.Produto, Boolean> sel = new TableColumn<>("");
        sel.setCellValueFactory(c -> marcados.computeIfAbsent(c.getValue(), k -> new SimpleBooleanProperty(false)));
        sel.setCellFactory(CheckBoxTableCell.forTableColumn(sel));
        sel.setEditable(true);
        sel.setPrefWidth(40);
        sel.setMaxWidth(44);
        t.setEditable(true);
        t.getColumns().add(sel);
        t.getColumns().add(Tabelas.coluna("PRODUTO", BlingService.Produto::nome, 300));
        t.getColumns().add(Tabelas.coluna("CÓDIGO", BlingService.Produto::codigo, 100));
        t.getColumns().add(Tabelas.coluna("PREÇO", pr -> Formatos.moeda(pr.preco()), 90));
        t.getColumns().add(Tabelas.colunaNo("PONTOS", pr -> Ui.label(Formatos.numero(pr.pontos()), "texto-roxo"), 90));
        t.setPrefSize(720, 380);

        TextField filtro = Ui.campoTexto("Nome do produto (deixe vazio para listar todos)");
        HBox.setHgrow(filtro, Priority.ALWAYS);
        Button buscar = Ui.botao("Buscar no Bling", "btn-primario", Icones.BUSCA);
        Label status = Ui.label("Conversão atual: R$ 1,00 = " + Config.get(Config.BLING_PONTOS_POR_REAL)
                + " pontos (altere em Integração Bling).", "texto-suave");
        Runnable acao = () -> {
            status.setText("Buscando produtos no Bling...");
            String f = filtro.getText();
            Ui.async(() -> BlingService.produtos(f), lista -> {
                marcados.clear();
                t.getItems().setAll(lista);
                status.setText(lista.size() + " produto(s) encontrado(s). Marque os que deseja importar.");
            });
        };
        buscar.setOnAction(e -> acao.run());
        filtro.setOnAction(e -> acao.run());
        d.getDialogPane().setContent(new VBox(12, new HBox(10, filtro, buscar), t, status));

        ButtonType ok = new ButtonType("Importar selecionados", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        if (d.showAndWait().filter(b -> b == ok).isEmpty()) return;
        List<BlingService.Produto> escolhidos = marcados.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        if (escolhidos.isEmpty()) return;
        int novos = 0;
        for (BlingService.Produto pr : escolhidos) {
            if (PremioService.importarBling(pr.id(), pr.nome(), pr.codigo().isBlank() ? null : "Cód. " + pr.codigo(), pr.pontos(), pr.imagem())) novos++;
        }
        Ui.toast(novos + " prêmio(s) novo(s), " + (escolhidos.size() - novos) + " atualizado(s)");
        carregar();
    }
}
