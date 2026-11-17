package br.com.royal.pontos.ui;

import br.com.royal.pontos.App;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Componentes visuais reutilizáveis (cards, botões, ícones, avisos). */
public final class Ui {

    private static StackPane toastHost;

    private Ui() {}

    // ------------------------------------------------------------------ básicos

    public static Region icone(String svg, double tamanho, String... classes) {
        Region r = new Region();
        r.setStyle("-fx-shape: \"" + svg + "\";");
        r.getStyleClass().add("icone");
        r.getStyleClass().addAll(classes);
        r.setMinSize(tamanho, tamanho);
        r.setPrefSize(tamanho, tamanho);
        r.setMaxSize(tamanho, tamanho);
        return r;
    }

    /** Logo da Royal num cartão branco arredondado. */
    public static StackPane logo(double altura) {
        ImageView iv = new ImageView(new Image(Ui.class.getResource("/img/logo.png").toExternalForm(), 0, altura * 2, true, true));
        iv.setPreserveRatio(true);
        iv.setFitHeight(altura);
        iv.setSmooth(true);
        StackPane s = new StackPane(iv);
        s.getStyleClass().add("logo-cartao");
        s.setMaxWidth(Region.USE_PREF_SIZE);
        return s;
    }

    public static StackPane iconeCirculo(String svg, String corClasse, double tamanho) {
        StackPane s = new StackPane(icone(svg, tamanho * 0.48, "icone-" + corClasse));
        s.getStyleClass().addAll("icone-fundo", "fundo-" + corClasse);
        s.setMinSize(tamanho, tamanho);
        s.setMaxSize(tamanho, tamanho);
        return s;
    }

    public static Label label(String texto, String... classes) {
        Label l = new Label(texto);
        l.getStyleClass().addAll(classes);
        // não corta o texto com "..." — exceto quando quebra linha ou tem largura máxima definida
        l.setMinWidth(Region.USE_PREF_SIZE);
        l.wrapTextProperty().addListener((o, a, quebra) -> l.setMinWidth(quebra ? 0 : Region.USE_PREF_SIZE));
        return l;
    }

    /** Texto truncável (nomes longos em listas). */
    public static Label nome(String texto, String classe, double larguraMax) {
        Label l = new Label(texto);
        l.getStyleClass().add(classe);
        l.setMaxWidth(larguraMax);
        l.setMinWidth(0);
        return l;
    }

    public static Button botao(String texto, String classe, String icone) {
        Button b = new Button(texto);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().addAll("btn", classe);
        if (icone != null) b.setGraphic(icone(icone, 15, "icone-btn"));
        b.setGraphicTextGap(8);
        return b;
    }

    public static VBox card(Node... filhos) {
        VBox v = new VBox(14, filhos);
        v.getStyleClass().add("card");
        return v;
    }

    public static VBox cardTitulo(String titulo, String subtitulo, Node... filhos) {
        VBox cab = new VBox(2, label(titulo, "card-titulo"));
        if (subtitulo != null) cab.getChildren().add(label(subtitulo, "card-sub"));
        VBox v = card(cab);
        v.getChildren().addAll(filhos);
        return v;
    }

    public static VBox campo(String rotulo, Node controle) {
        VBox v = new VBox(6, label(rotulo, "campo-rotulo"), controle);
        if (controle instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    public static Label badge(String texto, String cor) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("badge", "badge-" + cor);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static Region espaco() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static StackPane avatar(String iniciais, double tamanho) {
        Label l = new Label(iniciais);
        l.getStyleClass().add("avatar-texto");
        l.setStyle("-fx-font-size: " + (tamanho * 0.38) + "px;");
        StackPane s = new StackPane(l);
        s.getStyleClass().add("avatar");
        s.setMinSize(tamanho, tamanho);
        s.setMaxSize(tamanho, tamanho);
        return s;
    }

    public static ScrollPane rolagem(Node conteudo) {
        ScrollPane sp = new ScrollPane(conteudo);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("rolagem");
        return sp;
    }

    /** Imagem do prêmio (arquivo ou URL) com cantos arredondados; ícone de presente se não houver. */
    public static StackPane imagemPremio(String caminho, double largura, double altura) {
        StackPane box = new StackPane();
        box.getStyleClass().add("premio-img");
        box.setMinSize(largura, altura);
        box.setPrefSize(largura, altura);
        box.setMaxSize(largura, altura);
        Node conteudo = icone(Icones.PRESENTE, altura * 0.38, "icone-premio-vazio");
        if (caminho != null && !caminho.isBlank()) {
            try {
                String url = caminho.startsWith("http") ? caminho : new File(caminho).toURI().toString();
                Image img = new Image(url, largura * 2, altura * 2, true, true, true);
                ImageView iv = new ImageView(img);
                iv.setPreserveRatio(true);
                iv.setFitWidth(largura - 16);
                iv.setFitHeight(altura - 16);
                Node fallback = conteudo;
                img.errorProperty().addListener((o, a, erro) -> { if (erro) box.getChildren().setAll(fallback); });
                conteudo = iv;
            } catch (Exception ignored) {
            }
        }
        Rectangle clip = new Rectangle(largura, altura);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        box.setClip(clip);
        box.getChildren().add(conteudo);
        return box;
    }

    // ------------------------------------------------------------------ campos

    public static TextField campoTexto(String dica) {
        TextField t = new TextField();
        t.setPromptText(dica);
        return t;
    }

    /** Campo que só aceita números e formata como CPF. */
    public static TextField campoCpf() {
        TextField t = campoTexto("000.000.000-00");
        mascara(t, 11, d -> {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < d.length(); i++) {
                if (i == 3 || i == 6) s.append('.');
                if (i == 9) s.append('-');
                s.append(d.charAt(i));
            }
            return s.toString();
        });
        return t;
    }

    public static TextField campoTelefone() {
        TextField t = campoTexto("(38) 99999-9999");
        mascara(t, 11, d -> {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < d.length(); i++) {
                if (i == 0) s.append('(');
                if (i == 2) s.append(") ");
                if ((d.length() == 11 && i == 7) || (d.length() <= 10 && i == 6)) s.append('-');
                s.append(d.charAt(i));
            }
            return s.toString();
        });
        return t;
    }

    private static void mascara(TextField t, int maxDigitos, UnaryOperator<String> formatar) {
        final boolean[] ocupado = {false};
        t.textProperty().addListener((obs, antigo, novo) -> {
            if (ocupado[0]) return;
            String d = novo.replaceAll("\\D", "");
            if (d.length() > maxDigitos) d = d.substring(0, maxDigitos);
            String f = formatar.apply(d);
            if (!f.equals(novo)) {
                ocupado[0] = true;
                Platform.runLater(() -> { t.setText(f); t.positionCaret(f.length()); ocupado[0] = false; });
            }
        });
    }

    public static TextField campoNumero(String dica, boolean decimal) {
        TextField t = campoTexto(dica);
        t.setTextFormatter(new TextFormatter<>(ch -> {
            String n = ch.getControlNewText();
            return n.matches(decimal ? "[0-9.]*,?[0-9]{0,2}" : "-?[0-9]*") ? ch : null;
        }));
        return t;
    }

    // ------------------------------------------------------------------ diálogos e avisos

    public static void definirToastHost(StackPane host) { toastHost = host; }

    public static void toast(String msg) { mostrarToast(msg, false); }

    public static void toastErro(String msg) { mostrarToast(msg, true); }

    private static void mostrarToast(String msg, boolean erro) {
        if (toastHost == null) { if (erro) erro(msg); return; }
        HBox t = new HBox(10, icone(erro ? Icones.TAG : Icones.CHECK, 16, "icone-toast"), new Label(msg));
        t.getStyleClass().addAll("toast", erro ? "toast-erro" : "toast-ok");
        t.setAlignment(Pos.CENTER_LEFT);
        t.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        t.setMouseTransparent(true);
        StackPane.setAlignment(t, Pos.TOP_CENTER);
        StackPane.setMargin(t, new Insets(24, 0, 0, 0));
        toastHost.getChildren().add(t);
        FadeTransition in = new FadeTransition(Duration.millis(200), t);
        in.setFromValue(0); in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(400), t);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(in, new PauseTransition(Duration.seconds(erro ? 5 : 3)), out);
        seq.setOnFinished(e -> toastHost.getChildren().remove(t));
        seq.play();
    }

    private static Alert alerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert a = new Alert(tipo, msg, tipo == Alert.AlertType.CONFIRMATION
                ? new ButtonType[]{new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE), new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE)}
                : new ButtonType[]{new ButtonType("OK", ButtonBar.ButtonData.OK_DONE)});
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        estilizar(a.getDialogPane());
        if (App.janela() != null) a.initOwner(App.janela());
        return a;
    }

    public static boolean confirmar(String titulo, String msg) {
        Optional<ButtonType> r = alerta(Alert.AlertType.CONFIRMATION, titulo, msg).showAndWait();
        return r.isPresent() && r.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    public static void erro(String msg) {
        alerta(Alert.AlertType.ERROR, "Atenção", msg).showAndWait();
    }

    public static void info(String titulo, String msg) {
        alerta(Alert.AlertType.INFORMATION, titulo, msg).showAndWait();
    }

    public static void estilizar(DialogPane p) {
        p.getStylesheets().add(Ui.class.getResource("/css/app.css").toExternalForm());
        p.getStyleClass().add("dialogo");
    }

    /** Executa fora da tela (sem travar) e volta com o resultado. */
    public static <T> void async(Callable<T> trabalho, Consumer<T> sucesso) {
        async(trabalho, sucesso, null);
    }

    public static <T> void async(Callable<T> trabalho, Consumer<T> sucesso, Runnable sempre) {
        Task<T> t = new Task<>() { @Override protected T call() throws Exception { return trabalho.call(); } };
        t.setOnSucceeded(e -> { if (sempre != null) sempre.run(); sucesso.accept(t.getValue()); });
        t.setOnFailed(e -> {
            if (sempre != null) sempre.run();
            Throwable ex = t.getException();
            ex.printStackTrace();
            toastErro(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        });
        Thread th = new Thread(t, "royal-async");
        th.setDaemon(true);
        th.start();
    }

    /** Executa uma ação e mostra o erro de forma amigável, se houver. Retorna true se deu certo. */
    public static boolean tentar(Runnable acao) {
        try {
            acao.run();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            erro(e.getMessage() == null ? e.toString() : e.getMessage());
            return false;
        }
    }

    public static void abrirWhatsApp(String telefone, String mensagem) {
        String tel = telefone.replaceAll("\\D", "");
        if (!tel.startsWith("55")) tel = "55" + tel;
        String txt = URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20");
        App.abrirLink("https://wa.me/" + tel + "?text=" + txt);
    }
}
