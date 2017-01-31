package omcgui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Created by zeejfps on 1/30/17.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene scene = new Scene(new Gui(primaryStage), 480, 320);
        scene.getStylesheets().add("styles.css");
        primaryStage.setTitle("Ogre Mesh Converter GUI v0.5");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

}
