package telemetry;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TelemetryDialog dialog = new TelemetryDialog();

            // Попытка загрузить конфигурацию и установить пути по умолчанию
            try {
                Config config = new Config();
                config.load("/Users/anton/Documents/lab8sem/src/telemetry/config.xml");
                dialog.setDefaultFiles(config.getTmFile(), config.getXmlFile(), config.getDimFile());
            } catch (Exception e) {
                // Конфигурация не обязательна
            }

            dialog.setVisible(true);
        });
    }
}