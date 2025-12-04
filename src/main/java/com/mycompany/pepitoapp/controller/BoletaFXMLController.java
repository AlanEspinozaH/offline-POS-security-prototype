/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package com.mycompany.pepitoapp.controller;

import static com.mycompany.pepitoapp.controller.BodegaFXMLController.boleta_RUC;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.boleta_nombre;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.listaItem;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.mostrarAlerta;

import com.mycompany.pepitoapp.model.Calendario;
import com.mycompany.pepitoapp.model.Item;
import com.mycompany.pepitoapp.security.crypto.CryptoService;
import com.mycompany.pepitoapp.security.ledger.LedgerService;
import java.net.URL;
import java.time.Instant;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.text.Text;
import javafx.util.converter.IntegerStringConverter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class BoletaFXMLController implements Initializable {

    private static final String DEFAULT_KEY_ID = "ed25519-pos";
    private static final char[] DEFAULT_PASSPHRASE = System.getenv()
            .getOrDefault("PEPITO_PASSPHRASE", "pepito-demo-pass").toCharArray();
    private final CryptoService cryptoService = new CryptoService();
    private final LedgerService ledgerService = new LedgerService();

    @FXML private TableView<Item> tableView;
    @FXML private TableColumn<Item, String> descripcionColumn;
    @FXML private TableColumn<Item, Double> precioUnitarioColumn;
    @FXML private TableColumn<Item, Integer> cantidadColumn;
    @FXML private Button btnEliminarProducto;
    @FXML private Button btnConfirmarCompra;
    @FXML private Button btnActualizar;
    @FXML private Text txtFecha;
    @FXML private Text txtNombre;
    @FXML private Text txtRUC;
    @FXML private TextField tfTotal;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tableView.setEditable(true);
        descripcionColumn.setCellValueFactory(cellData -> cellData.getValue().descripcionProperty());
        precioUnitarioColumn.setCellValueFactory(cellData -> cellData.getValue().precioUnitarioProperty().asObject());
        cantidadColumn.setCellValueFactory(cellData -> cellData.getValue().cantidadProperty().asObject());

        cantidadColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        cantidadColumn.setOnEditCommit(event -> {
            Item itemSeleccionado = event.getRowValue();
            itemSeleccionado.setCantidad(event.getNewValue());
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue)->{});
        tableView.setItems(FXCollections.observableArrayList(listaItem));

        // Uso de la clase Calendario.java
        Calendario cal = new Calendario();
        txtFecha.setText(cal.toString());

        txtNombre.setText(boleta_nombre);
        txtRUC.setText(boleta_RUC);

        calcularTotal();
    }

    @FXML
    private void eliminarProducto() {
        Item selectedItem = tableView.getSelectionModel().getSelectedItem();

        if (selectedItem != null) {
            listaItem.remove(selectedItem);
            tableView.setItems(FXCollections.observableArrayList(listaItem));

        } else mostrarAlerta("Error", "Producto no seleccionado");

        calcularTotal();
    }

    @FXML private void confirmarCompra(){
        if (listaItem.isEmpty()){
            mostrarAlerta ("Error", "Primero añada algún producto a su carrito");
            return;
        }

        double total = calcularTotal();
        guardarHistorial();
        boolean recorded = registrarEnBitacora(total);
        if (recorded) {
            mostrarAlerta("Compra", "Su compra se ha registrado y firmado correctamente");
        } else {
            mostrarAlerta("Advertencia", "Compra guardada pero no se pudo registrar en la bitácora de seguridad");
        }
    }

    private double calcularTotal() {
        double total = 0.0;
        for (Item item : listaItem) {
            total += item.getPrecioUnitario() * item.getCantidad();
        }
        tfTotal.setText(String.valueOf(total));
        return total;
    }

    private void guardarHistorial() {
        String nombreArchivo = "historial.txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(nombreArchivo, true))) {
            for (Item item : listaItem) {
                writer.println(item.getDescripcion() + "\t" + item.getPrecioUnitario() + "\t" + item.getCantidad());
            }
            writer.println("Total: " + tfTotal.getText());
            writer.println("-----------");
        } catch (IOException e) {
            e.printStackTrace();
            mostrarAlerta("Error", "Error al guardar el historial.");
        }
    }

    private boolean registrarEnBitacora(double total) {
        try {
            String saleJson = buildSaleJson(total);
            return ledgerService.appendSignedEntry(saleJson, DEFAULT_KEY_ID, DEFAULT_PASSPHRASE, cryptoService);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private String buildSaleJson(double total) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"timestamp\":").append(Instant.now().getEpochSecond()).append(',');
        builder.append("\"cliente\":{\"nombre\":\"").append(escape(boleta_nombre)).append("\",\"ruc\":\"")
                .append(escape(boleta_RUC)).append("\"},");
        builder.append("\"total\":").append(total).append(',');
        builder.append("\"items\":[");
        for (int i = 0; i < listaItem.size(); i++) {
            Item item = listaItem.get(i);
            builder.append('{')
                    .append("\"descripcion\":\"").append(escape(item.getDescripcion())).append("\",")
                .append("\"precio\":").append(item.getPrecioUnitario()).append(',')
                    .append("\"cantidad\":").append(item.getCantidad())
                    .append('}');
            if (i < listaItem.size() - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FXML private void actualizar() {
    calcularTotal();
    }

}
