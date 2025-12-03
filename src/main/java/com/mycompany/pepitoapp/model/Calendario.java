/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pepitoapp.model;


import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 *
 * @author Jesus Diego
 */
public class Calendario {
    GregorianCalendar ahora = new GregorianCalendar();
    private int anio = ahora.get(Calendar.YEAR);
    private int mes = ahora.get(Calendar.MONTH) + 1;
    private int dia_mes = ahora.get(Calendar.DAY_OF_MONTH);
    private int hora_del_dia = ahora.get(Calendar.HOUR_OF_DAY);
    private int minuto = ahora.get(Calendar.MINUTE);
    private int segundo = ahora.get(Calendar.SECOND);
    public Calendario (/*int anio, int mes, int dia_mes*/){
       /* this.anio = anio;
        this.mes = mes;
        this.dia_mes = dia_mes;*/
    }
    @Override
    public String toString(){
        return this.dia_mes +"/" +this.mes +"/" +this.anio +"\t" +this.hora_del_dia +":" +this.minuto +":" +this.segundo;
    }
}