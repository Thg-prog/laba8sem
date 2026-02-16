package telemetry;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public class TelemetryDialog extends JFrame {
    private Dim dim;
    private DatXML datXML;
    private ReadTMI reader;

    private JList<String> paramList;
    private DefaultListModel<String> listModel;
    private JTextArea valueArea;
    private JTextArea statsArea;

    private JTextField txtTmFile;
    private JTextField txtXmlFile;
    private JTextField txtDimFile;
    private JButton btnLoad;

    // Массив пунктов для выборочной статистики
    private static final String[] STAT_ITEMS = {
            "Общее количество записей",
            "Служебные записи",
            "Полезные записи",
            "Неизвестный тип",
            "Long (0)",
            "Double (1)",
            "Code (2)",
            "Point (3)",
            "Уникальные параметры"
    };

    public TelemetryDialog() {
        setTitle("Telemetry Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);

        initUI();
        dim = new Dim();
        datXML = new DatXML();
        reader = null;
        updateStatsPlaceholder();
    }

    public void setDefaultFiles(String tmFile, String xmlFile, String dimFile) {
        txtTmFile.setText(tmFile);
        txtXmlFile.setText(xmlFile);
        txtDimFile.setText(dimFile);
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Панель выбора файлов
        JPanel filePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // TM-файл
        gbc.gridx = 0; gbc.gridy = 0;
        filePanel.add(new JLabel("TM-файл:"), gbc);
        txtTmFile = new JTextField(30);
        txtTmFile.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0;
        filePanel.add(txtTmFile, gbc);
        JButton btnTm = new JButton("Обзор...");
        btnTm.addActionListener(this::chooseTmFile);
        gbc.gridx = 2; gbc.weightx = 0;
        filePanel.add(btnTm, gbc);

        // XML-файл
        gbc.gridx = 0; gbc.gridy = 1;
        filePanel.add(new JLabel("XML-файл:"), gbc);
        txtXmlFile = new JTextField(30);
        txtXmlFile.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0;
        filePanel.add(txtXmlFile, gbc);
        JButton btnXml = new JButton("Обзор...");
        btnXml.addActionListener(this::chooseXmlFile);
        gbc.gridx = 2; gbc.weightx = 0;
        filePanel.add(btnXml, gbc);

        // Файл размерностей
        gbc.gridx = 0; gbc.gridy = 2;
        filePanel.add(new JLabel("Размерности:"), gbc);
        txtDimFile = new JTextField(30);
        txtDimFile.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0;
        filePanel.add(txtDimFile, gbc);
        JButton btnDim = new JButton("Обзор...");
        btnDim.addActionListener(this::chooseDimFile);
        gbc.gridx = 2; gbc.weightx = 0;
        filePanel.add(btnDim, gbc);

        // Кнопка загрузки
        gbc.gridx = 1; gbc.gridy = 3;
        btnLoad = new JButton("Загрузить данные");
        btnLoad.addActionListener(this::loadDataAction);
        filePanel.add(btnLoad, gbc);

        // Кнопка сохранения статистики
        JButton btnSaveStats = new JButton("Сохранить статистику");
        btnSaveStats.addActionListener(this::saveStatistics);
        gbc.gridx = 2; gbc.gridy = 3;
        filePanel.add(btnSaveStats, gbc);

        add(filePanel, BorderLayout.NORTH);

        // Список параметров и область значений
        listModel = new DefaultListModel<>();
        paramList = new JList<>(listModel);
        paramList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paramList.addListSelectionListener(this::paramSelected);

        valueArea = new JTextArea();
        valueArea.setEditable(false);
        valueArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(paramList), new JScrollPane(valueArea));
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);

        // Статистика
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statsArea.setBackground(new Color(240, 240, 240));
        add(new JScrollPane(statsArea), BorderLayout.SOUTH);
    }

    private void chooseTmFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите TM-файл (.KNP)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtTmFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseXmlFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите XML-файл с параметрами");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtXmlFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseDimFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите файл размерностей (dimens.ion)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtDimFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadDataAction(ActionEvent e) {
        String tmPath = txtTmFile.getText().trim();
        String xmlPath = txtXmlFile.getText().trim();
        String dimPath = txtDimFile.getText().trim();

        if (tmPath.isEmpty() || xmlPath.isEmpty() || dimPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Выберите все три файла.");
            return;
        }

        btnLoad.setEnabled(false);
        btnLoad.setText("Загрузка...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Dim newDim = new Dim();
                newDim.load(dimPath);
                DatXML newDat = new DatXML();
                newDat.load(xmlPath);
                ReadTMI newReader = new ReadTMI();
                newReader.load(tmPath, newDim, newDat);
                dim = newDim;
                datXML = newDat;
                reader = newReader;
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // проверить исключения
                    updateUIAfterLoad();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(TelemetryDialog.this,
                            "Ошибка загрузки:\n" + ex,
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnLoad.setEnabled(true);
                    btnLoad.setText("Загрузить данные");
                }
            }
        };
        worker.execute();
    }

    private void updateUIAfterLoad() {
        listModel.clear();
        for (String name : reader.getRecordsByName().keySet()) {
            listModel.addElement(name);
        }
        buildStatistics();
        valueArea.setText("");
    }

    private void paramSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || reader == null) return;
        String selected = paramList.getSelectedValue();
        if (selected == null) return;

        List<TmDat> records = reader.getRecordsByName().get(selected);
        if (records == null) return;

        records.sort(Comparator.comparingLong(TmDat::getTime));

        StringBuilder sb = new StringBuilder();
        sb.append("Параметр: ").append(selected).append("\n");
        sb.append("Всего записей: ").append(records.size()).append("\n");
        sb.append("--------------------------------------------------\n");
        for (TmDat rec : records) {
            sb.append(TmDat.formatTime(rec.getTime()))
                    .append("  ")
                    .append(rec.getValueAsString())
                    .append("\n");
        }
        valueArea.setText(sb.toString());
        valueArea.setCaretPosition(0);
    }

    private void buildStatistics() {
        if (reader == null) {
            statsArea.setText("Нет загруженных данных.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Статистика по файлу:\n");
        sb.append("  Общее количество ТМ-записей: ").append(reader.getTotalRecords()).append("\n");
        sb.append("  Служебных записей: ").append(reader.getServiceRecords()).append("\n");
        sb.append("  Полезных записей: ").append(reader.getUsefulRecords()).append("\n");
        sb.append("  Записей с неизвестным типом: ").append(reader.getUnknownRecords()).append("\n");

        int[] typeCounts = reader.getTypeCounts();
        sb.append("  Распределение полезных по типам:\n");
        sb.append("    Long  (0): ").append(typeCounts[0]).append("\n");
        sb.append("    Double(1): ").append(typeCounts[1]).append("\n");
        sb.append("    Code  (2): ").append(typeCounts[2]).append("\n");
        sb.append("    Point (3): ").append(typeCounts[3]).append("\n");

        sb.append("  Уникальных параметров: ").append(reader.getRecordsByName().size()).append("\n");
        statsArea.setText(sb.toString());
    }

    private void updateStatsPlaceholder() {
        statsArea.setText("Загрузите данные для отображения статистики.");
    }

    // ========== Сохранение статистики с выбором ==========

    private void saveStatistics(ActionEvent e) {
        if (reader == null) {
            JOptionPane.showMessageDialog(this,
                    "Нет загруженных данных. Сначала выполните загрузку.",
                    "Предупреждение", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] options = {"Вся статистика", "Статистика выбранного параметра", "Отдельный пункт"};
        int choice = JOptionPane.showOptionDialog(this,
                "Выберите, что сохранить:",
                "Сохранение статистики",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        String content = null;
        String defaultFileName = "statistics.txt";

        switch (choice) {
            case 0: // Вся статистика
                content = statsArea.getText();
                defaultFileName = "full_statistics.txt";
                break;
            case 1: // Статистика выбранного параметра
                String selectedParam = paramList.getSelectedValue();
                if (selectedParam == null) {
                    JOptionPane.showMessageDialog(this,
                            "Не выбран параметр. Выберите параметр из списка.",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                content = getParameterStatistics(selectedParam);
                defaultFileName = "param_" + selectedParam.replace('/', '_') + ".txt";
                break;
            case 2: // Отдельный пункт
                String item = (String) JOptionPane.showInputDialog(this,
                        "Выберите пункт статистики:",
                        "Отдельный пункт",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        STAT_ITEMS,
                        STAT_ITEMS[0]);
                if (item == null) return; // отмена
                content = getSingleStatisticItem(item);
                defaultFileName = "stat_item.txt";
                break;
            default:
                return; // отмена
        }

        // Сохраняем в файл
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Сохранить статистику");
        chooser.setSelectedFile(new File(defaultFileName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(content);
                JOptionPane.showMessageDialog(this,
                        "Статистика сохранена в файл:\n" + file.getAbsolutePath(),
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Ошибка при сохранении: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Формирует статистику по одному параметру (имя и количество записей). */
    private String getParameterStatistics(String paramName) {
        List<TmDat> records = reader.getRecordsByName().get(paramName);
        if (records == null) return "Параметр не найден: " + paramName;

        StringBuilder sb = new StringBuilder();
        sb.append("Статистика по параметру: ").append(paramName).append("\n");
        sb.append("Количество записей: ").append(records.size()).append("\n");
        // Можно добавить дополнительную информацию, например, временной диапазон
        if (!records.isEmpty()) {
            long minTime = records.stream().mapToLong(TmDat::getTime).min().getAsLong();
            long maxTime = records.stream().mapToLong(TmDat::getTime).max().getAsLong();
            sb.append("Диапазон времени: ")
                    .append(TmDat.formatTime(minTime))
                    .append(" - ")
                    .append(TmDat.formatTime(maxTime))
                    .append("\n");
        }
        return sb.toString();
    }

    /** Возвращает строку с одним выбранным пунктом общей статистики. */
    private String getSingleStatisticItem(String item) {
        if (reader == null) return "Нет данных";
        int[] tc = reader.getTypeCounts();
        switch (item) {
            case "Общее количество записисей":
                return "Общее количество записей: " + reader.getTotalRecords();
            case "Служебные записи":
                return "Служебные записи: " + reader.getServiceRecords();
            case "Полезные записи":
                return "Полезные записи: " + reader.getUsefulRecords();
            case "Неизвестный тип":
                return "Записей с неизвестным типом: " + reader.getUnknownRecords();
            case "Long (0)":
                return "Long (0): " + tc[0];
            case "Double (1)":
                return "Double (1): " + tc[1];
            case "Code (2)":
                return "Code (2): " + tc[2];
            case "Point (3)":
                return "Point (3): " + tc[3];
            case "Уникальные параметры":
                return "Уникальных параметров: " + reader.getRecordsByName().size();
            default:
                return "Неизвестный пункт";
        }
    }
}