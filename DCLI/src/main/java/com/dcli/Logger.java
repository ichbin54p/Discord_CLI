package com.dcli;

public class Logger {
    private final String name;
    public Logger(String name){
        this.name = name;
    }

    public void info(Object value){
        if(true)return;
        System.out.print("[" + name + "] ");
        System.out.println(value);
    }
}
