/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pepitoapp.model;

import java.time.LocalDate;

/**
 *
 * @author Jesus Diego
 */
public class Producto {
    private String codigo;
    private String nombre;
    private int cantidad;
    private String tipo;
    private double precio_unitario_costo;
    private double precio_unitario_venta;
    private int stock;
    private LocalDate fechaDeCaducidad;

    // Constructor
    public Producto(String codigo, String nombre, double valor) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.tipo = tipo;
        this.precio_unitario_costo = precio_unitario_costo;
        this.precio_unitario_venta = precio_unitario_venta;
        this.stock = stock;
        this.fechaDeCaducidad = fechaDeCaducidad;
        
    }

    // Getters y setters (puedes generarlos automáticamente en muchos IDE)
    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidad() {
        return cantidad;
    }
    
    public String getTipo() {
        return tipo;
    }
    
    public double getPrecio_unitario_costo() {
         return precio_unitario_costo;
    }
    
    public double getPrecio_unitario_venta() {
         return precio_unitario_venta;
    }
    
    public int getStock() {
         return stock;
    }
    
    public LocalDate getFechaDeCaducidad(){
         return fechaDeCaducidad;
    }
}
