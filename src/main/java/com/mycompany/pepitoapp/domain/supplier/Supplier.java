package com.mycompany.pepitoapp.domain.supplier;

public record Supplier(long id, String name, String taxId) {
    @Override public String toString() { return name; }
}
