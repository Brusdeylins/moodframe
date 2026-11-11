module tools.moodframe {
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
    exports tools.moodframe;
    exports tools.moodframe.ui.controller;
    exports tools.moodframe.ui.component;
    exports tools.moodframe.ui.dialog;
    exports tools.moodframe.model;

    // Open for reflection (required for FXML, Jackson)
    opens tools.moodframe to javafx.fxml, com.fasterxml.jackson.databind;
    opens tools.moodframe.ui.controller to javafx.fxml;
    opens tools.moodframe.ui.component to javafx.fxml;
    opens tools.moodframe.ui.dialog to javafx.fxml;
    opens tools.moodframe.model to com.fasterxml.jackson.databind;
}