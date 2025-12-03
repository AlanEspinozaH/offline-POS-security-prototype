/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pepitoapp.model;

/**
 *
 * @author Jesus Diego
 */
public class Cliente extends Persona{
    String ruc_cliente;
    
    public String getRuc_Cliente(){
        return ruc_cliente;
    }
    
    public void setRuc_Cliente(String ruc_cliente){
        this.ruc_cliente = ruc_cliente;
    }
}
