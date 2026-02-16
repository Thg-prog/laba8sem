package telemetry;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    // Список чекбоксов для общей статистики
    private JList<String> statListGeneral;
    private boolean[] statSelectedGeneral;

    // Список чекбоксов для статистики по параметру
    private JList<String> statListParam;
    private boolean[] statSelectedParam;

    private JButton btnShowSelected;
    private JButton btnResetStats;
    private JButton btnSaveStats;
    private JButton btnClearValues;

    // Названия пунктов общей статистики (13 пунктов)
    private static final String[] STAT_ITEMS_GENERAL = {
            "Общее количество записей",
            "Служебные записи",
            "Полезные записи",
            "Записей с неизвестным типом",
            "Long (0)",
            "Double (1)",
            "Code (2)",
            "Point (3)",
            "Уникальные параметры",
            "Point < 4 байт",
            "Point >= 4 байт",
            "Code < 8 разрядов",
            "Code >= 8 разрядов"
    };

    // Названия пунктов статистики по параметру (10 пунктов)
    private static final String[] STAT_ITEMS_PARAM = {
            "Всего записей параметра",
            "Long (0)",
            "Double (1)",
            "Code (2)",
            "Point (3)",
            "Неизвестный тип",
            "Point < 4 байт",
            "Point >= 4 байт",
            "Code < 8 разрядов",
            "Code >= 8 разрядов"
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

        // ---------- Панель выбора файлов ----------
        JPanel filePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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

        gbc.gridx = 1; gbc.gridy = 3;
        btnLoad = new JButton("Загрузить данные");
        btnLoad.addActionListener(this::loadDataAction);
        filePanel.add(btnLoad, gbc);

        add(filePanel, BorderLayout.NORTH);

        // ---------- Центральная часть: список параметров и значения ----------
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

        // ---------- Нижняя панель: два списка чекбоксов и кнопки ----------
        JPanel southPanel = new JPanel(new BorderLayout());

        // Панель для двух списков (горизонтально)
        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // Список общей статистики
        JPanel generalPanel = new JPanel(new BorderLayout());
        generalPanel.setBorder(BorderFactory.createTitledBorder("Общая статистика"));
        statSelectedGeneral = new boolean[STAT_ITEMS_GENERAL.length];
        DefaultListModel<String> generalModel = new DefaultListModel<>();
        for (String item : STAT_ITEMS_GENERAL) {
            generalModel.addElement(item);
        }
        statListGeneral = new JList<>(generalModel);
        statListGeneral.setCellRenderer(new CheckBoxListRenderer(statSelectedGeneral));
        statListGeneral.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = statListGeneral.locationToIndex(e.getPoint());
                if (index != -1) {
                    statSelectedGeneral[index] = !statSelectedGeneral[index];
                    statListGeneral.repaint();
                }
            }
        });
        JScrollPane generalScroll = new JScrollPane(statListGeneral);
        generalScroll.setPreferredSize(new Dimension(200, 150));
        generalPanel.add(generalScroll, BorderLayout.CENTER);
        listsPanel.add(generalPanel);

        // Список статистики по параметру
        JPanel paramStatPanel = new JPanel(new BorderLayout());
        paramStatPanel.setBorder(BorderFactory.createTitledBorder("Статистика выбранного параметра"));
        statSelectedParam = new boolean[STAT_ITEMS_PARAM.length];
        DefaultListModel<String> paramModel = new DefaultListModel<>();
        for (String item : STAT_ITEMS_PARAM) {
            paramModel.addElement(item);
        }
        statListParam = new JList<>(paramModel);
        statListParam.setCellRenderer(new CheckBoxListRenderer(statSelectedParam));
        statListParam.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = statListParam.locationToIndex(e.getPoint());
                if (index != -1) {
                    statSelectedParam[index] = !statSelectedParam[index];
                    statListParam.repaint();
                }
            }
        });
        JScrollPane paramScroll = new JScrollPane(statListParam);
        paramScroll.setPreferredSize(new Dimension(200, 150));
        paramStatPanel.add(paramScroll, BorderLayout.CENTER);
        listsPanel.add(paramStatPanel);

        // Панель для кнопок
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnShowSelected = new JButton("Показать выбранное");
        btnShowSelected.addActionListener(this::showSelectedStatistics);
        buttonPanel.add(btnShowSelected);

        btnResetStats = new JButton("Полная статистика");
        btnResetStats.addActionListener(e -> buildStatistics());
        buttonPanel.add(btnResetStats);

        btnSaveStats = new JButton("Сохранить статистику");
        btnSaveStats.addActionListener(this::saveCurrentStatistics);
        buttonPanel.add(btnSaveStats);

        btnClearValues = new JButton("Очистить значения");
        btnClearValues.addActionListener(e -> valueArea.setText(""));
        buttonPanel.add(btnClearValues);

        // Сборка нижней панели
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(listsPanel, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        southPanel.add(controlPanel, BorderLayout.NORTH);

        // Текстовая область для отображения статистики
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statsArea.setBackground(new Color(240, 240, 240));
        southPanel.add(new JScrollPane(statsArea), BorderLayout.CENTER);

        add(southPanel, BorderLayout.SOUTH);
    }

    // Рендерер для чекбоксов
    private static class CheckBoxListRenderer extends JCheckBox implements ListCellRenderer<String> {
        private final boolean[] selected;

        public CheckBoxListRenderer(boolean[] selected) {
            this.selected = selected;
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setText(value);
            setSelected(selected[index]);
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

    // Методы выбора файлов
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

    // Загрузка данных
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

    // Обновление интерфейса после загрузки
    private void updateUIAfterLoad() {
        listModel.clear();
        for (String name : reader.getRecordsByName().keySet()) {
            listModel.addElement(name);
        }
        buildStatistics();
        valueArea.setText("");
        // Сбросить состояния чекбоксов
        for (int i = 0; i < statSelectedGeneral.length; i++) {
            statSelectedGeneral[i] = false;
        }
        for (int i = 0; i < statSelectedParam.length; i++) {
            statSelectedParam[i] = false;
        }
        statListGeneral.repaint();
        statListParam.repaint();
    }

    // Обработчик выбора параметра (добавление значений с разделителем)
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

        String currentText = valueArea.getText();
        if (!currentText.isEmpty()) {
            valueArea.setText(currentText + "\n----------------------\n\n" + sb.toString());
        } else {
            valueArea.setText(sb.toString());
        }
        valueArea.setCaretPosition(0);
    }

    // Полная статистика (общая)
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
        sb.append("  Point < 4 байт: ").append(reader.getPointLess4()).append("\n");
        sb.append("  Point >= 4 байт: ").append(reader.getPointGreater4()).append("\n");
        sb.append("  Code < 8 разрядов: ").append(reader.getCodeLess8()).append("\n");
        sb.append("  Code >= 8 разрядов: ").append(reader.getCodeGreater8()).append("\n");
        statsArea.setText(sb.toString());
    }

    private void updateStatsPlaceholder() {
        statsArea.setText("Загрузите данные для отображения статистики.");
    }

    // Отображение выбранной статистики из обоих списков
    private void showSelectedStatistics(ActionEvent e) {
        if (reader == null) {
            JOptionPane.showMessageDialog(this, "Нет загруженных данных.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Выборочная статистика\n");
        sb.append("=====================\n\n");

        boolean anySelected = false;

        // --- Общая статистика ---
        int[] tc = reader.getTypeCounts();
        if (statSelectedGeneral[0]) {
            sb.append(STAT_ITEMS_GENERAL[0]).append(": ").append(reader.getTotalRecords()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[1]) {
            sb.append(STAT_ITEMS_GENERAL[1]).append(": ").append(reader.getServiceRecords()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[2]) {
            sb.append(STAT_ITEMS_GENERAL[2]).append(": ").append(reader.getUsefulRecords()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[3]) {
            sb.append(STAT_ITEMS_GENERAL[3]).append(": ").append(reader.getUnknownRecords()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[4]) {
            sb.append(STAT_ITEMS_GENERAL[4]).append(": ").append(tc[0]).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[5]) {
            sb.append(STAT_ITEMS_GENERAL[5]).append(": ").append(tc[1]).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[6]) {
            sb.append(STAT_ITEMS_GENERAL[6]).append(": ").append(tc[2]).append("\n");
                    anySelected = true;
        }
        if (statSelectedGeneral[7]) {
            sb.append(STAT_ITEMS_GENERAL[7]).append(": ").append(tc[3]).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[8]) {
            sb.append(STAT_ITEMS_GENERAL[8]).append(": ").append(reader.getRecordsByName().size()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[9]) {
            sb.append(STAT_ITEMS_GENERAL[9]).append(": ").append(reader.getPointLess4()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[10]) {
            sb.append(STAT_ITEMS_GENERAL[10]).append(": ").append(reader.getPointGreater4()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[11]) {
            sb.append(STAT_ITEMS_GENERAL[11]).append(": ").append(reader.getCodeLess8()).append("\n");
            anySelected = true;
        }
        if (statSelectedGeneral[12]) {
            sb.append(STAT_ITEMS_GENERAL[12]).append(": ").append(reader.getCodeGreater8()).append("\n");
            anySelected = true;
        }

        // --- Статистика по выбранному параметру ---
        String selectedParam = paramList.getSelectedValue();
        if (selectedParam != null) {
            List<TmDat> paramRecords = reader.getRecordsByName().get(selectedParam);
            if (paramRecords != null) {
                // Подсчёт по типам для параметра
                int paramTotal = paramRecords.size();
                int paramLong = 0, paramDouble = 0, paramCode = 0, paramPoint = 0, paramUnknown = 0;
                int paramPointLess4 = 0, paramPointGreater4 = 0;
                int paramCodeLess8 = 0, paramCodeGreater8 = 0;

                for (TmDat rec : paramRecords) {
                    if (rec instanceof TmUnknown) {
                        paramUnknown++;
                    } else {
                        switch (rec.getValueType()) {
                            case 0: paramLong++; break;
                            case 1: paramDouble++; break;
                            case 2: {
                                paramCode++;
                                TmCode c = (TmCode) rec;
                                if (c.getCodeLength() < 8) paramCodeLess8++;
                                else if (c.getCodeLength() > 8) paramCodeGreater8++;
                                break;
                            }
                            case 3: {
                                paramPoint++;
                                TmPoint p = (TmPoint) rec;
                                if (p.getDataLength() < 4) paramPointLess4++;
                                else if (p.getDataLength() > 4) paramPointGreater4++;
                                break;
                            }
                            default: paramUnknown++;
                        }
                    }
                }

                // Вывод отмеченных пунктов для параметра
                if (statSelectedParam[0]) {
                    sb.append("\n").append(STAT_ITEMS_PARAM[0]).append(" (").append(selectedParam).append("): ").append(paramTotal).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[1]) {
                    sb.append(STAT_ITEMS_PARAM[1]).append(" (").append(selectedParam).append("): ").append(paramLong).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[2]) {
                    sb.append(STAT_ITEMS_PARAM[2]).append(" (").append(selectedParam).append("): ").append(paramDouble).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[3]) {
                    sb.append(STAT_ITEMS_PARAM[3]).append(" (").append(selectedParam).append("): ").append(paramCode).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[4]) {
                    sb.append(STAT_ITEMS_PARAM[4]).append(" (").append(selectedParam).append("): ").append(paramPoint).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[5]) {
                    sb.append(STAT_ITEMS_PARAM[5]).append(" (").append(selectedParam).append("): ").append(paramUnknown).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[6]) {
                    sb.append(STAT_ITEMS_PARAM[6]).append(" (").append(selectedParam).append("): ").append(paramPointLess4).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[7]) {
                    sb.append(STAT_ITEMS_PARAM[7]).append(" (").append(selectedParam).append("): ").append(paramPointGreater4).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[8]) {
                    sb.append(STAT_ITEMS_PARAM[8]).append(" (").append(selectedParam).append("): ").append(paramCodeLess8).append("\n");
                    anySelected = true;
                }
                if (statSelectedParam[9]) {
                    sb.append(STAT_ITEMS_PARAM[9]).append(" (").append(selectedParam).append("): ").append(paramCodeGreater8).append("\n");
                    anySelected = true;
                }
            }
        } else {
            // Если параметр не выбран, но отмечены пункты из второго списка, предупреждение
            for (boolean b : statSelectedParam) {
                if (b) {
                    sb.append("\nВнимание: для статистики параметра необходимо выбрать параметр.\n");
                    break;
                }
            }
        }

        if (!anySelected) {
            statsArea.setText("Ничего не выбрано.");
        } else {
            statsArea.setText(sb.toString());
        }
    }

    // Сохранение текущего содержимого statsArea в файл
    private void saveCurrentStatistics(ActionEvent e) {
        if (reader == null) {
            JOptionPane.showMessageDialog(this, "Нет загруженных данных.");
            return;
        }
        String content = statsArea.getText();
        if (content == null || content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Нет статистики для сохранения.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Сохранить статистику");
        chooser.setSelectedFile(new File("statistics.txt"));
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
}