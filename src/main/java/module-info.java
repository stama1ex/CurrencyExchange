module com.example.currencyexchange {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.net.http;
    requires java.xml;
    requires org.controlsfx.controls;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    opens com.example.currencyexchange to javafx.fxml;
    opens com.example.currencyexchange.controller to javafx.fxml;
    opens com.example.currencyexchange.model to javafx.base;

    exports com.example.currencyexchange;
    exports com.example.currencyexchange.controller;
    exports com.example.currencyexchange.model;
    exports com.example.currencyexchange.enums;
    exports com.example.currencyexchange.service;
    exports com.example.currencyexchange.util;
}
