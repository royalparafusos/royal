package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import br.com.royal.pontos.model.Cliente;
import br.com.royal.pontos.model.Movimento;
import br.com.royal.pontos.service.ClienteService;
import br.com.royal.pontos.service.Config;
import br.com.royal.pontos.service.PontosService;
import br.com.royal.pontos.service.Sessao;
import br.com.royal.pontos.util.Formatos;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

public class ClienteDetalheView extends VBox {

    private final long clienteId;

    public ClienteDetalheView(long clienteId) {
        this.clienteId = clienteId;
        getStyleClass().add("pagina");
        carregar();
    }

    private void carregar() {
        Ui.async(() -> new Object[]{ClienteService.porId(clienteId), PontosService.aVencer(clienteId, 30),
                PontosService.proximaExpiracao(clienteId), PontosService.extrato(clienteId)}, r -> {
            Cliente c = (Cliente) r[0];
            @SuppressWarnings("unchecked") List<Movimento> extrato = (List<Movimento>) r[3];
            montar(c, (Integer) r[1], (LocalDate) r[2], extrato);
        });
    }

    private void montar(Cliente c, int aVencer, LocalDate proxima, List<Movimento> extrato) {
        Button voltar = Ui.botao("Voltar para clientes", "btn-link", Icones.VOLTAR);
        voltar.setOnAction(e -> MainView.atual().ir(MainView.Tela.CLIENTES));

        Button compra = Ui.botao("Lançar compra", "btn-ouro", Icones.CARRINHO);
        compra.setOnAction(e -> MainView.atual().ir(MainView.Tela.COMPRA, c.cpf()));
        Button resgatar = Ui.botao("Resgatar", "btn-secundario", Icones.PRESENTE);
        resgatar.setOnAction(e -> MainView.atual().ir(MainView.Tela.RESGATE, c.cpf()));
        compra.setMinWidth(170);
        resgatar.setMinWidth(170);
        ClienteHero hero = new ClienteHero(c, aVencer, proxima, compra, resgatar);

        Button editar = Ui.botao("Editar dados", "btn-secundario", Icones.EDITAR);
        editar.setOnAction(e -> { if (ClienteDialog.abrir(c, null) != null) carregar(); });
        Button whats = Ui.botao("Enviar saldo no WhatsApp", "btn-verde", Icones.CHAT);
        whats.setOnAction(e -> Ui.abrirWhatsApp(c.telefone(), mensagemSaldo(c, aVencer, proxima)));
        HBox acoes = new HBox(10, editar, whats);
        if (Sessao.isAdmin()) {
            Button ajuste = Ui.botao("Ajustar pontos", "btn-secundario", Icones.TAG);
            ajuste.setOnAction(e -> ajustar(c));
            Button desativar = Ui.botao("Remover do programa", "btn-perigo", null);
            desativar.setOnAction(e -> {
                if (Ui.confirmar("Remover cliente", "Remover " + c.nome() + " do programa? O histórico é mantido, mas ele não aparecerá mais nas buscas.")
                        && Ui.tentar(() -> ClienteService.desativar(c.id()))) {
                    Ui.toast("Cliente removido do programa");
                    MainView.atual().ir(MainView.Tela.CLIENTES);
                }
            });
            acoes.getChildren().addAll(ajuste, Ui.espaco(), desativar);
        }

        TableView<Movimento> tabela = Tabelas.movimentos(false);
        tabela.getItems().setAll(extrato);
        tabela.setPrefHeight(420);
        if (Sessao.isAdmin()) {
            MenuItem estornar = new MenuItem("Estornar / cancelar este lançamento");
            estornar.setOnAction(e -> {
                Movimento m = tabela.getSelectionModel().getSelectedItem();
                if (m == null) return;
                if (Ui.confirmar("Estornar lançamento", "Confirma o estorno de \"" + m.descricao() + "\" (" + Formatos.pontos(m.pontos()) + ")?")
                        && Ui.tentar(() -> PontosService.estornar(m.id()))) {
                    Ui.toast("Lançamento estornado");
                    carregar();
                }
            });
            tabela.setContextMenu(new ContextMenu(estornar));
        }
        Label dica = Ui.label(Sessao.isAdmin() ? "Clique com o botão direito em um lançamento para estornar." : "", "texto-suave");
        VBox cardExtrato = Ui.cardTitulo("Extrato de pontos", "Todas as compras, resgates e vencimentos", tabela, dica);

        getChildren().setAll(voltar, hero, acoes, cardExtrato);
    }

    static String mensagemSaldo(Cliente c, int aVencer, LocalDate proxima) {
        StringBuilder sb = new StringBuilder("Olá, ").append(Formatos.primeiroNome(c.nome())).append("! 👑\n")
                .append("Seu saldo no programa de fidelidade da *").append(Config.get(Config.LOJA_NOME)).append("* é de *")
                .append(Formatos.pontos(c.saldo())).append("*.");
        if (aVencer > 0 && proxima != null) sb.append("\n⏰ Atenção: ").append(Formatos.pontos(aVencer))
                .append(" vencem em breve (primeiro vencimento em ").append(Formatos.data(proxima)).append("). Venha trocar!");
        String link = Config.get(Config.LINK_CONSULTA);
        if (!link.isBlank()) sb.append("\n\nConsulte seus pontos e prêmios: ").append(link);
        return sb.toString();
    }

    private void ajustar(Cliente c) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Ajustar pontos");
        d.setHeaderText("Ajuste manual de pontos — " + c.nome());
        d.initOwner(App.janela());
        Ui.estilizar(d.getDialogPane());
        TextField pts = Ui.campoNumero("Ex.: 50 para adicionar, -50 para remover", false);
        TextField motivo = Ui.campoTexto("Ex.: bonificação de aniversário");
        Label erro = Ui.label("", "erro-texto");
        VBox box = new VBox(12, Ui.campo("Pontos (+ adiciona / − remove)", pts), Ui.campo("Motivo", motivo), erro);
        box.setPrefWidth(400);
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType("Aplicar ajuste", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                PontosService.ajustar(c.id(), Integer.parseInt(pts.getText().trim()), motivo.getText());
            } catch (NumberFormatException ex) {
                erro.setText("Informe um número de pontos.");
                e.consume();
            } catch (Exception ex) {
                erro.setText(ex.getMessage());
                e.consume();
            }
        });
        d.showAndWait().filter(b -> b == ok).ifPresent(b -> { Ui.toast("Ajuste aplicado"); carregar(); });
    }
}
