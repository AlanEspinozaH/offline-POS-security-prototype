    /*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pepitoapp.model;

import java.util.Objects;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Item {
    
    public SimpleStringProperty descripcion;
    public SimpleDoubleProperty precioUnitario;
    public SimpleIntegerProperty cantidad;

    public Item(String descripcion, double precioUnitario, int cantidad ) {
        this.descripcion = new SimpleStringProperty(descripcion);
        this.precioUnitario = new SimpleDoubleProperty(precioUnitario);
        this.cantidad = new SimpleIntegerProperty(cantidad); 
    }

    public int getCantidad() {
        return cantidad.get();
    }
    public void setCantidad(int cantidad) {
        this.cantidad.set(cantidad);
    }
    public SimpleIntegerProperty cantidadProperty() {
        return cantidad;
    }
    
    
    public String getDescripcion() {
        return descripcion.get();
    }
    public void setDescripcion(String descripcion) {
        this.descripcion.set(descripcion);
    }
    public SimpleStringProperty descripcionProperty() {
        return descripcion;
    }

        
    public double getPrecioUnitario() {
        return precioUnitario.get();
    }
    public void setPrecioUnitario(double precioUnitario) {
        this.precioUnitario.set(precioUnitario);
    }
    public SimpleDoubleProperty precioUnitarioProperty() {
        return precioUnitario;
    }

    // Se sobreescribe el metodo equals para que no haya problemas al usar contains (la comparacion no distingue y es erronea)
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Item item = (Item) obj;
        return descripcion.get().equals(item.descripcion.get()) &&
               precioUnitario.get() == item.precioUnitario.get();
    }
  
}



