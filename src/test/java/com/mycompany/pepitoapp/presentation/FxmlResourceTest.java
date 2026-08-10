package com.mycompany.pepitoapp.presentation;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FxmlResourceTest {
    @Test void purchaseAndPricingViewsAreWellFormedXml() throws Exception {
        parse("/com/mycompany/pepitoapp/view/purchaseReception.fxml");
        parse("/com/mycompany/pepitoapp/view/priceReview.fxml");
        parse("/com/mycompany/pepitoapp/view/bodegaFXML.fxml");
    }

    private void parse(String resource) throws Exception {
        try(InputStream input=FxmlResourceTest.class.getResourceAsStream(resource)) {
            assertNotNull(input,"Recurso no encontrado: "+resource);
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
        }
    }
}
