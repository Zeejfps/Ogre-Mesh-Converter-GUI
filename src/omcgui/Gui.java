package omcgui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.*;

import java.io.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Created by zeejfps on 1/30/17.
 */
public class Gui extends BorderPane {

    private static final Font FONT_AWESOME;
    static {
        FONT_AWESOME = Font.loadFont(Gui.class.getClassLoader().getResourceAsStream("fontawesome-webfont.ttf"), 16);
    }

    private static final String PATH_PREF = "ogre.pref.path";

    private final Preferences prefs;
    private final Stage stage;
    private final PathDialog pathDialog;
    private final DirectoryChooser dirChooser;
    private final FileChooser fileChooser;
    private final Text statusTxt;
    private final Cmd ogreXMLConvertCmd;
    private final Cmd ogreUpdateCmd;

    private String pathStr = "";
    private File dstDir;

    public Gui(Stage stage) {
        this.stage = stage;

        prefs = Preferences.userNodeForPackage(App.class);
        pathDialog = new PathDialog();
        dirChooser = new DirectoryChooser();
        fileChooser = new FileChooser();

        statusTxt = new Text();
        statusTxt.setFocusTraversable(false);

        pathStr = prefs.get(PATH_PREF, "");
        System.out.println(pathStr);

        String os = System.getProperty("os.name");

        if (os.startsWith("Windows")) {
            System.out.println("Windows");
            ogreXMLConvertCmd = new WinCmd("OgreXMLConverter.exe");
            ogreUpdateCmd = new WinCmd("OgreMeshUpdater.exe");
        }
        else {
            System.out.println("Unix");
            ogreXMLConvertCmd = new UnixCmd("OgreXMLConverter");
            ogreUpdateCmd = new UnixCmd("OgreMeshUpdater");
        }

        checkPath();

        Button binBtn = new Button("\uF013");
        binBtn.setFocusTraversable(false);
        binBtn.setFont(FONT_AWESOME);
        HBox h = new HBox(binBtn);
        h.setAlignment(Pos.CENTER_RIGHT);

        StackPane topPane = new StackPane(statusTxt, h);
        setTop(topPane);

        TableColumn<File, String> fileCol = new TableColumn<>("XML File(s)");
        fileCol.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().getAbsolutePath()));

        TableView<File> tableView = new TableView<>();
        tableView.setFocusTraversable(false);
        tableView.setPlaceholder(new Label("No files added"));
        tableView.setPrefHeight(250);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getColumns().add(fileCol);
        tableView.setEditable(false);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setCenter(tableView);

        fileChooser.setTitle("Choose XML files");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ogre Mesh XML Files (*.mesh.xml)", "*.mesh.xml"));

        Button addBtn = new Button("Add File(s)");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        Button clsBtn = new Button("Remove File(s)");
        clsBtn.setMaxWidth(Double.MAX_VALUE);

        GridPane btnBox = new GridPane();
        btnBox.addRow(0, addBtn, clsBtn);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50);
        btnBox.getColumnConstraints().addAll(cc, cc);

        TextField dstFld = new TextField();
        dstFld.setFocusTraversable(false);
        dstFld.setEditable(false);
        Button dstBtn = new Button("...");
        dstBtn.setFocusTraversable(false);
        BorderPane dstBox = new BorderPane();
        dstBox.setRight(dstBtn);
        dstBox.setCenter(dstFld);
        BorderedTitledPane dstPane = new BorderedTitledPane("Mesh Export Path", dstBox);

        Button convertBtn = new Button("Convert");
        convertBtn.setDisable(true);
        convertBtn.setPrefHeight(40);
        convertBtn.setMaxWidth(Double.MAX_VALUE);

        VBox vbox = new VBox(btnBox, dstPane, convertBtn);
        vbox.setFillWidth(true);
        vbox.setAlignment(Pos.CENTER_RIGHT);
        setBottom(vbox);

        addBtn.setOnAction(e -> {
            List<File> files = fileChooser.showOpenMultipleDialog(stage);
            if (files != null && !files.isEmpty()) {
                tableView.getItems().addAll(files);
                convertBtn.setDisable(false);
            }
        });

        clsBtn.setOnAction(e -> {
            ObservableList<File> selectedItems = tableView.getSelectionModel().getSelectedItems();
            tableView.getItems().removeAll(selectedItems);
            if (tableView.getItems().isEmpty()) {
                convertBtn.setDisable(true);
            }
        });

        dstBtn.setOnAction(e -> {
            dirChooser.setTitle("Choose output directory");
            dstDir = dirChooser.showDialog(stage);
            if (dstDir != null && dstDir.exists()) {
                dstFld.setText(dstDir.getAbsolutePath());
            }
        });

        convertBtn.setOnAction(e -> {

            convertBtn.setDisable(true);

            ProgressBar pb = new ProgressBar();
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setPrefHeight(topPane.getHeight());
            setTop(pb);
            double maxWork = tableView.getItems().size();

            Task<Void> task = new Task<Void>() {
                double workDone = 0;
                @Override
                protected Void call() throws Exception {
                    for (File f : tableView.getItems()) {
                        try {
                            Process p;
                            p = ogreXMLConvertCmd.execute(f, dstDir);
                            p.waitFor();
                            p = ogreUpdateCmd.execute(f, dstDir);
                            p.waitFor();
                            updateProgress(++workDone, maxWork);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    return null;
                }
            };

            task.setOnSucceeded(workerStateEvent -> {
                statusTxt.setText("Successful!");
                setTop(topPane);
                convertBtn.setDisable(false);
            });
            pb.progressProperty().bind(task.progressProperty());
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        });

        binBtn.setOnAction(e -> {
            pathDialog.showAndWait();
        });
    }

    private class BorderedTitledPane extends StackPane {

        public BorderedTitledPane(String titleString, Node content) {
            Label title = new Label(" " + titleString + " ");
            title.getStyleClass().add("bordered-titled-title");
            StackPane.setAlignment(title, Pos.CENTER_LEFT);

            StackPane contentPane = new StackPane();
            content.getStyleClass().add("bordered-titled-content");
            contentPane.getChildren().add(content);

            getStyleClass().add("bordered-titled-border");
            getChildren().addAll(title, contentPane);
        }

    }

    private class PathDialog extends Stage {

        private TextField pathFld;

        public PathDialog() {
            super(StageStyle.UTILITY);

            this.pathFld = new TextField();
            pathFld.setPrefWidth(200);

            setTitle("Configure PATH variable");
            initModality(Modality.WINDOW_MODAL);
            initOwner(stage);
            setResizable(false);

            Label pathLbl = new Label("PATH = ");

            Button setBtn = new Button("Set");
            setBtn.setMinWidth(35);
            setBtn.setOnAction(e -> {
                pathStr = pathFld.getText();
                PathDialog.this.close();
                System.out.println("PATH=" + pathStr);
                if (!pathStr.isEmpty()) {
                    prefs.put(PATH_PREF, pathStr);
                }
                checkPath();
            });

            HBox hBox = new HBox(pathLbl, pathFld, setBtn);
            hBox.setAlignment(Pos.CENTER);
            hBox.setStyle("-fx-padding: 10 10 10 10;");

            Scene scene = new Scene(hBox);
            scene.getStylesheets().add("styles.css");
            setScene(scene);
            sizeToScene();
        }

    }

    private boolean checkPath() {
        if (pathStr == null || pathStr.isEmpty()) {
            statusTxt.setText("No PATH variable set !");
            statusTxt.setFill(Color.RED);
            return false;
        }
        else {
            statusTxt.setText("Ready to convert files");
            statusTxt.setFill(Color.GREEN);
            pathDialog.pathFld.setText(pathStr);
            return true;
        }
    }

    private interface Cmd {
        Process execute(File src, File dst) throws IOException;
    }

    private class WinCmd implements Cmd {

        private final String cmd;

        public WinCmd(String cmd) {
            this.cmd = cmd;
        }

        @Override
        public Process execute(File src, File dst) throws IOException {
            String cmd = this.cmd + " " + src.getAbsolutePath();
            if (dst != null) {
                cmd += " " + dst.getAbsolutePath() + "/" + src.getName().replace(".xml", "");
            }
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
            pb.environment().put("PATH", pathStr);
            pb.redirectErrorStream(true);
            return pb.start();
        }
    }

    private class UnixCmd implements Cmd {

        private final String cmd;

        public UnixCmd(String cmd) {
            this.cmd = cmd;
        }

        @Override
        public Process execute(File src, File dst) throws IOException {
            String cmd = this.cmd + " " + src.getAbsolutePath();
            if (dst != null) {
                cmd += " " + dst.getAbsolutePath() + "/" + src.getName().replace(".xml", "");
            }
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            pb.environment().put("PATH", pathStr);
            return pb.start();
        }

    }

}
