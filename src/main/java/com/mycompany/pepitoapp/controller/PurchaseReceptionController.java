package com.mycompany.pepitoapp.controller;

import com.mycompany.pepitoapp.ApplicationServices;
import com.mycompany.pepitoapp.domain.product.ProductSnapshot;
import com.mycompany.pepitoapp.domain.purchase.Purchase;
import com.mycompany.pepitoapp.domain.purchase.PurchaseLine;
import com.mycompany.pepitoapp.domain.supplier.Supplier;
import com.mycompany.pepitoapp.presentation.MoneyFormats;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class PurchaseReceptionController {
    private final ApplicationServices services=ApplicationServices.get();
    private final List<PurchaseLine> lines=new ArrayList<>();
    private ProductSnapshot currentProduct;
    private String unknownBarcode;

    @FXML private ComboBox<Supplier> supplierCombo;
    @FXML private TextField newSupplierName;
    @FXML private TextField referenceField;
    @FXML private TextField notesField;
    @FXML private TextField scanField;
    @FXML private Label productNameLabel;
    @FXML private Label barcodeLabel;
    @FXML private TextField presentationField;
    @FXML private TextField unitsPerPackField;
    @FXML private TextField paidPacksField;
    @FXML private TextField bonusPacksField;
    @FXML private TextField bonusUnitsField;
    @FXML private TextField packPriceField;
    @FXML private TextField discountField;
    @FXML private Label netAmountLabel;
    @FXML private Label receivedUnitsLabel;
    @FXML private Label effectiveCostLabel;
    @FXML private Label lastCostLabel;
    @FXML private Label currentSalePriceLabel;
    @FXML private Label marginLabel;
    @FXML private Label statusLabel;
    @FXML private Label purchaseTotalLabel;
    @FXML private ListView<String> linesList;
    @FXML private VBox unknownPanel;
    @FXML private Label unknownBarcodeLabel;
    @FXML private TextField existingProductField;
    @FXML private TextField unknownNameField;
    @FXML private TextField unknownSalePriceField;

    @FXML private void initialize() {
        reloadSuppliers();
        scanField.setOnAction(event->scan());
        unitsPerPackField.setOnAction(event->paidPacksField.requestFocus());
        paidPacksField.setOnAction(event->bonusPacksField.requestFocus());
        bonusPacksField.setOnAction(event->bonusUnitsField.requestFocus());
        bonusUnitsField.setOnAction(event->packPriceField.requestFocus());
        packPriceField.setOnAction(event->discountField.requestFocus());
        discountField.setOnAction(event->addLine());
        for(TextField field:List.of(unitsPerPackField,paidPacksField,bonusPacksField,bonusUnitsField,packPriceField,discountField)) {
            field.textProperty().addListener((obs,oldValue,newValue)->refreshPreview());
        }
        unknownPanel.setVisible(false); unknownPanel.setManaged(false);
        clearLine();
        Platform.runLater(scanField::requestFocus);
    }

    @FXML private void addSupplier() {
        try {
            Supplier supplier=services.suppliers().create(newSupplierName.getText(),null);
            reloadSuppliers(); supplierCombo.getSelectionModel().select(supplier); newSupplierName.clear();
            showStatus("✓ Proveedor creado",false); scanField.requestFocus();
        } catch(Exception ex) { showStatus(ex.getMessage(),true); }
    }

    @FXML private void scan() {
        if(supplierCombo.getValue()==null) { showStatus("Seleccione o cree un proveedor antes de escanear",true); supplierCombo.requestFocus(); return; }
        String barcode=scanField.getText()==null?"":scanField.getText().trim();
        if(barcode.isEmpty()) return;
        try {
            var found=services.productQuery().findByBarcode(barcode);
            if(found.isEmpty()) { showUnknown(barcode); return; }
            currentProduct=found.get();
            productNameLabel.setText(currentProduct.name()); barcodeLabel.setText("Código: "+barcode);
            currentSalePriceLabel.setText(MoneyFormats.salePrice(currentProduct.currentSalePriceCents()));
            lastCostLabel.setText(MoneyFormats.preciseCost(currentProduct.latestUnitCostCents()));
            var presentation=services.suppliers().findPresentation(supplierCombo.getValue().id(),currentProduct.barcode());
            presentationField.setText(presentation.map(p->p.presentationName()).orElse("Caja"));
            unitsPerPackField.setText(Integer.toString(presentation.map(p->p.unitsPerPack()).orElse(1)));
            packPriceField.setText(presentation.filter(p->p.lastPackPriceCents()!=null).map(p->BigDecimal.valueOf(p.lastPackPriceCents(),2).toPlainString()).orElse(""));
            paidPacksField.setText("1"); bonusPacksField.setText("0"); bonusUnitsField.setText("0"); discountField.setText("0.00");
            hideUnknown(); unitsPerPackField.selectAll(); unitsPerPackField.requestFocus(); refreshPreview();
        } catch(Exception ex) { showStatus(ex.getMessage(),true); scanField.selectAll(); }
    }

    @FXML private void addLine() {
        if(currentProduct==null) { showStatus("Escanee primero un producto registrado",true); scanField.requestFocus(); return; }
        try {
            PurchaseLine line=buildCurrentLine(); lines.add(line);
            linesList.getItems().add(currentProduct.name()+" — "+line.paidPacks()+" "+line.presentationName()+" — "+MoneyFormats.salePrice(line.netAmountCents()));
            showStatus("✓ "+currentProduct.name()+" añadida",false); refreshPurchaseTotal(); clearLine(); scanField.requestFocus();
        } catch(Exception ex) { showStatus(ex.getMessage(),true); }
    }

    @FXML private void removeSelectedLine() {
        int index=linesList.getSelectionModel().getSelectedIndex();
        if(index>=0) { lines.remove(index); linesList.getItems().remove(index); refreshPurchaseTotal(); }
        scanField.requestFocus();
    }

    @FXML private void confirmPurchase() {
        if(supplierCombo.getValue()==null || lines.isEmpty()) { showStatus("Seleccione proveedor y añada al menos una línea",true); return; }
        try {
            var result=services.registerPurchase().register(new Purchase(supplierCombo.getValue().id(),Instant.now(),referenceField.getText(),notesField.getText(),lines));
            String warnings=result.lines().stream().filter(r->r.warning()!=null).map(r->r.warning()).distinct().reduce((a,b)->a+" · "+b).orElse("");
            showStatus("✓ Compra #"+result.purchaseId()+" confirmada"+(warnings.isEmpty()?"":" · "+warnings),false);
            lines.clear(); linesList.getItems().clear(); referenceField.clear(); notesField.clear(); refreshPurchaseTotal(); clearLine(); scanField.requestFocus();
        } catch(Exception ex) { showStatus(ex.getMessage(),true); }
    }

    @FXML private void associateUnknown() {
        try { services.catalogMaintenance().associateBarcode(unknownBarcode,existingProductField.getText().trim()); rescanUnknown(); }
        catch(Exception ex) { showStatus(ex.getMessage(),true); }
    }

    @FXML private void createUnknown() {
        try { services.catalogMaintenance().createProduct(unknownBarcode,unknownNameField.getText(),MoneyFormats.parseSolesToCents(unknownSalePriceField.getText())); rescanUnknown(); }
        catch(Exception ex) { showStatus(ex.getMessage(),true); }
    }

    @FXML private void cancelUnknown() { hideUnknown(); clearLine(); scanField.requestFocus(); }

    private PurchaseLine buildCurrentLine() {
        return services.addPurchaseLine().create(currentProduct.barcode(),integer(unitsPerPackField),integer(paidPacksField),integer(bonusPacksField),integer(bonusUnitsField),MoneyFormats.parseSolesToCents(packPriceField.getText()),MoneyFormats.parseSolesToCents(discountField.getText()),presentationField.getText());
    }

    private void refreshPreview() {
        if(currentProduct==null) return;
        try {
            var preview=services.addPurchaseLine().preview(buildCurrentLine());
            netAmountLabel.setText(MoneyFormats.salePrice(preview.netAmountCents()));
            receivedUnitsLabel.setText(Long.toString(preview.totalReceivedUnits()));
            effectiveCostLabel.setText(MoneyFormats.preciseCost(preview.effectiveUnitCostCents()));
            marginLabel.setText(currentProduct.currentSalePriceCents()>0?MoneyFormats.percent(com.mycompany.pepitoapp.domain.pricing.PricingCalculator.grossMargin(currentProduct.currentSalePriceCents(),preview.effectiveUnitCostCents())):"—");
        } catch(Exception ignored) { netAmountLabel.setText("—"); receivedUnitsLabel.setText("—"); effectiveCostLabel.setText("—"); marginLabel.setText("—"); }
    }

    private void refreshPurchaseTotal() { long total=lines.stream().mapToLong(PurchaseLine::netAmountCents).sum(); purchaseTotalLabel.setText(MoneyFormats.salePrice(total)); }
    private int integer(TextField field) { return Integer.parseInt(field.getText().trim()); }
    private void reloadSuppliers() { try { supplierCombo.getItems().setAll(services.suppliers().listActive()); } catch(Exception ex) { showStatus(ex.getMessage(),true); } }
    private void showUnknown(String barcode) { unknownBarcode=barcode; currentProduct=null; unknownBarcodeLabel.setText(barcode); unknownPanel.setVisible(true); unknownPanel.setManaged(true); showStatus("PRODUCTO NO REGISTRADO",true); existingProductField.clear(); unknownNameField.clear(); unknownSalePriceField.clear(); existingProductField.requestFocus(); }
    private void hideUnknown() { unknownPanel.setVisible(false); unknownPanel.setManaged(false); unknownBarcode=null; }
    private void rescanUnknown() { String code=unknownBarcode; hideUnknown(); scanField.setText(code); scan(); showStatus("✓ Código registrado; complete la línea",false); }
    private void clearLine() { currentProduct=null; scanField.clear(); productNameLabel.setText("Escanee un producto"); barcodeLabel.setText(""); presentationField.setText("Caja"); unitsPerPackField.setText("1"); paidPacksField.setText("1"); bonusPacksField.setText("0"); bonusUnitsField.setText("0"); packPriceField.clear(); discountField.setText("0.00"); netAmountLabel.setText("—"); receivedUnitsLabel.setText("—"); effectiveCostLabel.setText("—"); lastCostLabel.setText("—"); currentSalePriceLabel.setText("—"); marginLabel.setText("—"); }
    private void showStatus(String message,boolean error) { statusLabel.setText(message==null?"Error inesperado":message); statusLabel.setStyle(error?"-fx-text-fill:#b42318":"-fx-text-fill:#087443"); }
}
