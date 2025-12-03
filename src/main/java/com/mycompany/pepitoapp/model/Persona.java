/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pepitoapp.model;

/**
 *
 * @author Jesus Diego
 */
abstract class Persona {
    protected String nombre;
    protected String apellido;
    protected String dni;
    protected String numero_cel;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getNumero_cel() {
        return numero_cel;
    }

    public void setNumero_cel(String numero_cel) {
        this.numero_cel = numero_cel;
    }
    
}
