module art.moodframe {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;
    requires java.desktop;
    requires java.sql;
    requires java.net.http;
    
    // Jackson JSON
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    
    // HTTP Client
    requires okhttp3;
    requires kotlin.stdlib;  // Required by OkHttp 4.x

    // JSON library for Gemini API
    requires org.json;

    // Database
    requires org.xerial.sqlitejdbc;
    // JDBI is not modularized - handled via classpath
    
    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    
    // Icons - Ikonli Material Design 2 Support
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    
    // AtlantaFX Modern Theme Framework
    requires transitive atlantafx.base;
    
    // Export packages for reflection (FXML, etc.)
    exports art.moodframe;
    exports art.moodframe.ui.controller;
    exports art.moodframe.ui.component;
    exports art.moodframe.ui.dialog;
    exports art.moodframe.model;

    // Open for reflection (required for FXML, Jackson)
    opens art.moodframe to javafx.fxml, com.fasterxml.jackson.databind;
    opens art.moodframe.ui.controller to javafx.fxml;
    opens art.moodframe.ui.component to javafx.fxml;
    opens art.moodframe.ui.dialog to javafx.fxml;
    opens art.moodframe.model to com.fasterxml.jackson.databind;
}