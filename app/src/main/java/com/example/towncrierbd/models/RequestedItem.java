package com.example.towncrierbd.models;

/** Quantity includes the unit, e.g. "2 kg" or "4 pieces". */
public class RequestedItem {
    private String product;
    private String quantity;
    public RequestedItem() {}
    public RequestedItem(String product, String quantity) {
        this.product = product; this.quantity = quantity;
    }
    public String getProduct() { return product; }
    public void setProduct(String value) { product = value; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String value) { quantity = value; }
}
