package com.stefanolupo.models;

public class IdAlreadyUsedException extends Exception {

    private final FactoryAnnotatedClass existingClass;

    IdAlreadyUsedException(FactoryAnnotatedClass factoryAnnotatedClass) {
        this.existingClass = factoryAnnotatedClass;
    }

    public FactoryAnnotatedClass getExisting() {
        return existingClass;
    }
}
