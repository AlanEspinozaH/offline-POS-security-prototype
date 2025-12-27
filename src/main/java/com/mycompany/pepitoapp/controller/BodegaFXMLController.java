
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package com.mycompany.pepitoapp.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.LinkedList;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.event.ActionEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


import com.mycompany.pepitoapp.model.Item;
/**
 * FXML Controller class
 *
 * @author Equipo A
 */
public class BodegaFXMLController implements Initializable {
    public static LinkedList<Item> listaItem = new LinkedList<>();

    @FXML private Button btnAñadirCarrito;
    @FXML private Button btnBuscarProducto;
    @FXML private Button btnEmitirBoleta;
    @FXML private TextField tfCodigo;
    @FXML private TextField tfNombreProducto;
    @FXML private TextField tfCantidad; 
    @FXML private TextField tfTipo;
    @FXML private TextField tfPrecioUnitarioCosto;
    @FXML private TextField tfPrecioUnitarioVenta;
    @FXML private TextField tfStock;
    @FXML private TextField tfLote;
    @FXML private TextField tfNombre;
    @FXML private TextField tfRUC;
    public static String boleta_nombre;
    public static String boleta_RUC;
    
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        btnAñadirCarrito.setDisable(true);
        tfCantidad.setDisable(true);
    }    
    
    
    @FXML private void accionBuscarProducto(){
        String codigoIngresado = tfCodigo.getText();

        if (codigoIngresado.isEmpty() || !esNumeroValido(codigoIngresado)) {
            mostrarAlerta("Entrada inválida", "Ingrese un número válido antes de buscar.");
            return; 
        }

        String url = "jdbc:sqlite:C:\\Users\\USUARIO\\Documents\\NetBeansProjects\\PepitoApp\\productos2.db";

        try (Connection connection = DriverManager.getConnection(url)) {
         
            String sql = "SELECT * FROM productos WHERE id_productos = ?";

            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

               // int id = Integer.parseInt(codigoIngresado);
               // preparedStatement.setInt(1, id);
               preparedStatement.setString(1, codigoIngresado);
               

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {

                        tfNombreProducto.setEditable(false);
                        tfTipo.setEditable(false);
                        tfPrecioUnitarioCosto.setEditable(false);
                        tfPrecioUnitarioVenta.setEditable(false);
                        tfStock.setEditable(false);
                        tfLote.setEditable(false);
                        
                        tfCantidad.setText("0");
                        tfTipo.setText(resultSet.getString("tipo"));
                        tfStock.setText(Integer.toString(resultSet.getInt("stock_unidades")));
                        tfNombreProducto.setText(resultSet.getString("nombre"));
                        tfPrecioUnitarioCosto.setText(Double.toString(resultSet.getDouble("precio_unitario_costo")));
                        tfLote.setText(resultSet.getString("fecha_de_caducidad"));
                        tfPrecioUnitarioVenta.setText(Double.toString(resultSet.getDouble("precio_unitario_venta")));


                    } else {
                        limpiarCampos();     //cuando codigo ingresado se convertia a entero "id" iba en vez de "codigoIngresado"
                        mostrarAlerta("Producto no encontrado", "El producto con ID " + codigoIngresado + " no fue encontrado.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarAlerta("Error de Base de Datos", "Ocurrió un error al acceder a la base de datos: " + e.getMessage());
        }
        btnAñadirCarrito.setDisable(false);
        tfCantidad.setDisable(false);
        
    }
    
    @FXML private void accionEmitirBoletaVenta() throws IOException{
        boleta_nombre = tfNombre.getText();
        boleta_RUC = tfRUC.getText();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/mycompany/pepitoapp/view/boletaFXML.fxml"));
            Parent root = loader.load();
        
            Stage newStage = new Stage();
            newStage.setScene(new Scene(root));
            newStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    public void limpiarCampos() {
        tfCodigo.clear();
        tfStock.clear();
        tfTipo.clear();
        tfNombreProducto.clear();
        tfCantidad.clear();
        tfPrecioUnitarioCosto.clear();
        tfLote.clear();
        tfPrecioUnitarioVenta.clear();
    }
    
    static public void mostrarAlerta(String titulo, String contenido) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    public boolean esNumeroValido(String texto) {
        try { //cuando id_productos se parseaba a int
            //Integer.parseInt(texto);  en vez de Long.parseLong(texto);
            Long.parseLong(texto);
            return true;
        } catch (NumberFormatException e) {
            return false;
          }
    }
    
    @FXML private void accionAñadirACarrito() {
        Item prod = new Item ("vacio", 0.0, 0);
        
        prod.setDescripcion (tfNombreProducto.getText());
        prod.setPrecioUnitario(Double.parseDouble(tfPrecioUnitarioVenta.getText()));
        prod.setCantidad(Integer.parseInt(tfCantidad.getText()));
        if (!prod.getDescripcion().equals("vacio") && prod.getPrecioUnitario() > 0.0 && prod.getCantidad()!=0) {

            if (listaItem.contains(prod)) {
                for (Item item: listaItem){
                    if (item.equals(prod)){
                        item.setCantidad(item.getCantidad() + prod.getCantidad());
                        break;
                    }
                }
            }
            else {
                listaItem.add(prod);
            }
            limpiarCampos();
            mostrarAlerta("Añadido al carrito", "El producto ha sido añadido al carrito correctamente.");           
        } else mostrarAlerta("Error", "Verifique que los campos estén rellenados correctamente");
        
        
        btnAñadirCarrito.setDisable(true);
        tfCantidad.setDisable(true);
    }

}
