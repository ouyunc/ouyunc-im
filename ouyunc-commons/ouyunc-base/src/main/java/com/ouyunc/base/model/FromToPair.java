package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * from to 的pair
 */
public class FromToPair implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String from;

    private String to;


    public FromToPair(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public FromToPair() {
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }
}
