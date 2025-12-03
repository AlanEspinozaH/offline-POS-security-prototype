/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package com.mycompany.pepitoapp.controller;

import static com.mycompany.pepitoapp.controller.BodegaFXMLController.boleta_RUC;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.boleta_nombre;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.listaItem;
import static com.mycompany.pepitoapp.controller.BodegaFXMLController.mostrarAlerta;

import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.text.Text;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;
import com.mycompany.pepitoapp.model.Calendario;
import javafx.scene.control.TextField;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.mycompany.pepitoapp.model.Item;

public class BoletaFXMLController implements Initializable {
    
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
        }
        else mostrarAlerta("Compra", "Su compra se ha realizado exitosamente");
       
        guardarHistorial();
        calcularTotal();
    }
    
    private void calcularTotal() {
        double total = 0.0;
        for (Item item : listaItem) {
            total += item.getPrecioUnitario() * item.getCantidad();
        }
        tfTotal.setText(String.valueOf(total));
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
    
    @FXML private void actualizar() {
    calcularTotal();
    }

}
