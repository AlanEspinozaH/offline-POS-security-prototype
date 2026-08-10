package com.mycompany.pepitoapp.controller;

import com.mycompany.pepitoapp.ApplicationServices;
import com.mycompany.pepitoapp.application.pricing.PriceReviewRow;
import com.mycompany.pepitoapp.presentation.MoneyFormats;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public final class PriceReviewController {
    private final ApplicationServices services=ApplicationServices.get();
    private PriceReviewRow selected;
    @FXML private TableView<PriceReviewRow> table;
    @FXML private TableColumn<PriceReviewRow,String> productColumn;
    @FXML private TableColumn<PriceReviewRow,String> priceColumn;
    @FXML private TableColumn<PriceReviewRow,String> costColumn;
    @FXML private TableColumn<PriceReviewRow,String> marginColumn;
    @FXML private TableColumn<PriceReviewRow,String> variationColumn;
    @FXML private TableColumn<PriceReviewRow,String> statusColumn;
    @FXML private Label currentPriceLabel;
    @FXML private Label previousCostLabel;
    @FXML private Label latestCostLabel;
    @FXML private Label averageCostLabel;
    @FXML private Label marginLabel;
    @FXML private Label theoreticalLabel;
    @FXML private Label suggestedLabel;
    @FXML private Label protectedLabel;
    @FXML private TextField newPriceField;
    @FXML private DatePicker effectiveDatePicker;
    @FXML private TextField reasonField;
    @FXML private DatePicker lockedUntilPicker;
    @FXML private TextField targetMarginField;
    @FXML private TextField minimumMarginField;
    @FXML private Label feedbackLabel;

    @FXML private void initialize() {
        productColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().productName()));
        priceColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(MoneyFormats.salePrice(c.getValue().currentPriceCents())));
        costColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(MoneyFormats.preciseCost(c.getValue().latestCostCents())));
        marginColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(MoneyFormats.percent(c.getValue().grossMargin())));
        variationColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(MoneyFormats.percent(c.getValue().costVariation())));
        statusColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().status()));
        table.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue)->showDetail(newValue));
        effectiveDatePicker.setValue(LocalDate.now().plusDays(1));
        refresh();
    }

    @FXML private void refresh() {
        try { table.getItems().setAll(services.priceReview().list()); if(!table.getItems().isEmpty()) table.getSelectionModel().selectFirst(); showFeedback("Datos actualizados",false); }
        catch(Exception ex) { showFeedback(ex.getMessage(),true); }
    }

    @FXML private void schedulePrice() {
        if(selected==null) return;
        try {
            long cents=MoneyFormats.parseSolesToCents(newPriceField.getText());
            LocalDate date=effectiveDatePicker.getValue();
            if(date==null) throw new IllegalArgumentException("Seleccione la fecha de vigencia");
            services.scheduleSalePrice().schedule(selected.productId(),cents,date.atStartOfDay(ZoneId.systemDefault()).toInstant(),reasonField.getText(),null);
            showFeedback("✓ Precio "+MoneyFormats.salePrice(cents)+" programado desde "+date,false); refresh();
        } catch(Exception ex) { showFeedback(ex.getMessage(),true); }
    }

    @FXML private void savePolicy() {
        if(selected==null) return;
        try {
            int target=parseBasisPoints(targetMarginField.getText()); int minimum=parseBasisPoints(minimumMarginField.getText());
            services.priceReview().configurePolicy(selected.productId(),target,minimum,lockedUntilPicker.getValue());
            showFeedback("✓ Política y período de estabilidad guardados",false); refresh();
        } catch(Exception ex) { showFeedback(ex.getMessage(),true); }
    }

    private void showDetail(PriceReviewRow row) {
        selected=row; if(row==null) return;
        currentPriceLabel.setText(MoneyFormats.salePrice(row.currentPriceCents()));
        previousCostLabel.setText(MoneyFormats.preciseCost(row.previousCostCents()));
        latestCostLabel.setText(MoneyFormats.preciseCost(row.latestCostCents()));
        averageCostLabel.setText(MoneyFormats.preciseCost(row.historicalWeightedCostCents()));
        marginLabel.setText(MoneyFormats.percent(row.grossMargin()));
        theoreticalLabel.setText(MoneyFormats.preciseCost(row.theoreticalPriceCents()));
        suggestedLabel.setText(MoneyFormats.salePrice(row.suggestedPriceCents()));
        protectedLabel.setText(row.lockedUntil()==null?"Sin protección":"Precio protegido hasta "+row.lockedUntil());
        newPriceField.setText(BigDecimal.valueOf(row.suggestedPriceCents(),2).toPlainString());
        lockedUntilPicker.setValue(row.lockedUntil());
        targetMarginField.setText(BigDecimal.valueOf(row.targetMarginBasisPoints(),2).stripTrailingZeros().toPlainString());
        minimumMarginField.setText(BigDecimal.valueOf(row.minimumMarginBasisPoints(),2).stripTrailingZeros().toPlainString());
        reasonField.setText("Ajuste aprobado por revisión de margen");
    }

    private int parseBasisPoints(String text) { return new BigDecimal(text.trim().replace(",",".")).movePointRight(2).intValueExact(); }
    private void showFeedback(String message,boolean error) { feedbackLabel.setText(message==null?"Error inesperado":message); feedbackLabel.setStyle(error?"-fx-text-fill:#b42318":"-fx-text-fill:#087443"); }
}
