

package com.mycompany.pepitoapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 *
 * @author alulo
 */
public class PepitoApp extends Application {

    private static Scene scene;

    @Override
    public void start(Stage primaryStage) {
        try {
            String fxmlPath = "/com/mycompany/pepitoapp/view/bodegaFXML.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            scene = new Scene(root, 1200, 800);

            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.show();
        } catch (IOException ex) {
            System.err.println("Error al cargar el FXML: " + ex.getMessage());
        }
    }

    public static void setRoot(String fxml) {
        try {
            scene.setRoot(loadFXML(fxml));
        } catch (IOException e) {
            System.err.println("Error al cargar el FXML: " + e.getMessage());
        }
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(PepitoApp.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch();
    }
}
