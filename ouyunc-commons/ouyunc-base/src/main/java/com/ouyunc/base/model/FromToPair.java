package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FromToPair that = (FromToPair) o;
        return Objects.equals(from, that.from) && Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }
}
