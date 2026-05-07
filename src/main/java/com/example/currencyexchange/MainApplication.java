package com.example.currencyexchange;

import atlantafx.base.theme.PrimerLight;
import com.example.currencyexchange.service.ExchangeRateAutoUpdateService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    private ExchangeRateAutoUpdateService exchangeRateAutoUpdateService;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1400, 900);
        stage.setTitle("Сеть касс обмена валют");
        stage.setScene(scene);
        stage.show();

        exchangeRateAutoUpdateService = new ExchangeRateAutoUpdateService();
        exchangeRateAutoUpdateService.start();
    }

    @Override
    public void stop() {
        if (exchangeRateAutoUpdateService != null) {
            exchangeRateAutoUpdateService.stop();
        }
    }
}
