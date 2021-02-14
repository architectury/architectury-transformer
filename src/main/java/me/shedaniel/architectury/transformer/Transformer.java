package me.shedaniel.architectury.transformer;

import java.io.ObjectOutputStream;
import java.io.Serializable;

public interface Transformer extends Serializable {
    default void writeObject(ObjectOutputStream stream) {
    }
}
