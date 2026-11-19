module art.moodframe {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.desktop;
    requires java.sql;
    requires java.net.http;

    /*  The HTTP server of the MCP server  */
    requires jdk.httpserver;
    
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
    
    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    
    // Icons - Ikonli Material Design 2 Support
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    
    // AtlantaFX Modern Theme Framework
    requires atlantafx.base;

    /*  Exported: only the root, for the JavaFX launcher, which creates the application class; no other module uses a
        type of the application, the reflection of FXML and Jackson on its fields needs the opens below  */
    exports art.moodframe;

    // Open for reflection (required for FXML, Jackson); the root holds no controller and nothing Jackson maps
    opens art.moodframe.ui.controller to javafx.fxml;
    opens art.moodframe.ui.controller.settings to javafx.fxml;
    opens art.moodframe.ui.component to javafx.fxml;
    opens art.moodframe.ui.panel to javafx.fxml;
    opens art.moodframe.model to com.fasterxml.jackson.databind;

    // The job answers of BFL (GenerationJobInfo, GenerationJobResult); the clients in its subpackages map no class
    opens art.moodframe.service.provider to com.fasterxml.jackson.databind;
}