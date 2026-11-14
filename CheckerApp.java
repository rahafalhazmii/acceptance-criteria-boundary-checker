import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CheckerApp extends JFrame {

    private final JTextField variableField =
            new JTextField("orderValue", 12);

    private final JTextField minField = new JTextField("0", 6);
    private final JTextField maxField = new JTextField("1000", 6);

    private final JTextArea criteriaArea = new JTextArea(6, 60);
    private final JTextArea outputArea = new JTextArea(18, 60);

    private final JButton extractButton =
            new JButton("1. Extract rules");

    private final JButton analyzeButton =
            new JButton("2. Confirm rules & analyze");

    private final JButton chooseButton =
            new JButton("3. Choose alternative");
    
    private final JButton saveButton = 
            new JButton("Save report");

    private final JComboBox<String> alternativesBox =
            new JComboBox<>();

    private final JLabel statusLabel =
            new JLabel("Ready");

    private final List<RepairGenerator.Alternative> verified =
            new ArrayList<>();

    private Rule[] originalRules;
    private int domainMin;
    private int domainMax;
    private String variable;
    private boolean busy;

    public CheckerApp() {

        super("Acceptance Criteria Checker");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 780);
        setMinimumSize(new Dimension(820, 620));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );
        setContentPane(root);

        JLabel title = new JLabel(
                "Boundary Gap Detection & Verified Repair Alternatives"
        );
        title.setFont(new Font("SansSerif", Font.BOLD, 20));

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fields.add(new JLabel("Variable:"));
        fields.add(variableField);
        fields.add(new JLabel("Integer domain — Min:"));
        fields.add(minField);
        fields.add(new JLabel("Max:"));
        fields.add(maxField);

        criteriaArea.setText("""
                AC1: If orderValue is less than 100, the shipping fee is 15.
                AC2: If orderValue is greater than 100, shipping is free.
                """);

        criteriaArea.setLineWrap(true);
        criteriaArea.setWrapStyleWord(true);
        criteriaArea.setFont(new Font("SansSerif", Font.PLAIN, 15));

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                "Acceptance criteria — English, one AC per line"
        ));
        inputPanel.add(new JScrollPane(criteriaArea), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(extractButton);
        actions.add(analyzeButton);
        actions.add(saveButton);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(fields);
        top.add(inputPanel);
        top.add(actions);

        root.add(top, BorderLayout.NORTH);

        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));

        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(
                BorderFactory.createTitledBorder("Rules & results")
        );
        root.add(outputScroll, BorderLayout.CENTER);

        JPanel selection = new JPanel(new BorderLayout(8, 8));
        selection.add(alternativesBox, BorderLayout.CENTER);
        selection.add(chooseButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(5, 8));
        bottom.add(selection, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);

        analyzeButton.setEnabled(false);
        chooseButton.setEnabled(false);

        extractButton.addActionListener(event -> extractRules());
        analyzeButton.addActionListener(event -> analyzeRules());
        chooseButton.addActionListener(event -> chooseAlternative());
        saveButton.addActionListener(event -> saveReport());

        // أي تعديل في المدخلات يلغي النتائج القديمة
        DocumentListener listener = new DocumentListener() {

            public void insertUpdate(DocumentEvent event) {
                invalidateResults();
            }

            public void removeUpdate(DocumentEvent event) {
                invalidateResults();
            }

            public void changedUpdate(DocumentEvent event) {
                invalidateResults();
            }
        };

        criteriaArea.getDocument().addDocumentListener(listener);
        variableField.getDocument().addDocumentListener(listener);
        minField.getDocument().addDocumentListener(listener);
        maxField.getDocument().addDocumentListener(listener);

        outputArea.setText(
                "Enter your criteria, then click Extract rules.\n"
                        + "Review the extracted conditions and outcomes "
                        + "before confirming."
        );
    }

    private void invalidateResults() {

        originalRules = null;
        verified.clear();
        alternativesBox.removeAllItems();
        analyzeButton.setEnabled(false);
        chooseButton.setEnabled(false);

        outputArea.setText(
                "Inputs changed. Extract the rules again."
        );
        statusLabel.setText("Ready for extraction");
    }

    private void setBusy(boolean value) {

        busy = value;
        extractButton.setEnabled(!value);
        criteriaArea.setEditable(!value);
        variableField.setEditable(!value);
        minField.setEditable(!value);
        maxField.setEditable(!value);
    }

    private void extractRules() {

        if (busy) {
            return;
        }

        String text = criteriaArea.getText().trim();
        String inputVariable = variableField.getText().trim();

        final int inputMin;
        final int inputMax;

        try {
            if (text.isBlank()) {
                throw new IllegalArgumentException(
                        "Enter acceptance criteria first."
                );
            }

            if (!inputVariable.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException(
                        "Use a variable name such as orderValue."
                );
            }

            inputMin = Integer.parseInt(minField.getText().trim());
            inputMax = Integer.parseInt(maxField.getText().trim());

            long size = (long) inputMax - inputMin + 1;

            if (size <= 0 || size > 10000) {
                throw new IllegalArgumentException(
                        "Domain must contain 1 to 10000 integer values."
                );
            }

        } catch (IllegalArgumentException exception) {
            showError(exception);
            return;
        }

        invalidateResults();
        setBusy(true);
        outputArea.setText("Extracting rules using Gemini...\n");
        statusLabel.setText("Please wait...");

        // تشغيل طلب الإنترنت في الخلفية حتى لا تتجمد الواجهة
        new SwingWorker<Rule[], Void>() {

            @Override
            protected Rule[] doInBackground() throws Exception {

                JsonObject extracted = RuleExtractor.extract(
                        text, inputVariable, inputMin, inputMax
                );

                return RuleValidator.validate(
                        extracted, text, inputVariable
                );
            }

            @Override
            protected void done() {

                try {
                    originalRules = get();
                    variable = inputVariable;
                    domainMin = inputMin;
                    domainMax = inputMax;

                    outputArea.setText(
                            "Extracted rules — review before confirming:\n\n"
                    );

                    appendRules(originalRules);

                    outputArea.append(
                            "\nCheck that these conditions and outcomes "
                                    + "match your original text.\n"
                                    + "Structure validation does not prove "
                                    + "correct interpretation.\n\n"
                                    + "If correct, click "
                                    + "Confirm rules & analyze."
                    );

                    analyzeButton.setEnabled(true);
                    statusLabel.setText("Waiting for your rule review");

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted");

                } catch (ExecutionException exception) {
                    showError(exception.getCause());

                } finally {
                    setBusy(false);
                    outputArea.setCaretPosition(0);
                }
            }
        }.execute();
    }

    private void analyzeRules() {

            if (originalRules == null || busy) {
                    return;
            }

            verified.clear();
            alternativesBox.removeAllItems();
            chooseButton.setEnabled(false);

            outputArea.setText("Confirmed extracted rules:\n\n");
            appendRules(originalRules);

            BoundaryChecker.Result result = BoundaryChecker.check(
                            originalRules, domainMin, domainMax);

            long valuesChecked = (long) domainMax - domainMin + 1;

            long exactlyOneMatch = valuesChecked - result.gaps.size()
                            - result.overlaps.size();

            outputArea.append(
                            "\nANALYSIS SUMMARY — ORIGINAL RULES\n"
                                            + "Variable: " + variable + "\n"
                                            + "Integer domain: " + domainMin
                                            + " to " + domainMax + "\n"
                                            + "Rules analyzed: "
                                            + originalRules.length + "\n"
                                            + "Integer values checked: "
                                            + valuesChecked + "\n"
                                            + "Values matching exactly one rule: "
                                            + exactlyOneMatch + "\n"
                                            + "Uncovered values count: "
                                            + result.gaps.size() + "\n"
                                            + "Overlapping values count: "
                                            + result.overlaps.size() + "\n"
                                            + "\nDETECTED VALUES\n"
                                            + "Uncovered values: "
                                            + result.gaps + "\n"
                                            + "Overlapping values: "
                                            + result.overlaps + "\n");

            outputArea.setCaretPosition(0);

            if (!result.overlaps.isEmpty()) {
                    outputArea.append(
                                    "\nManual review required: original rules overlap.\n"
                                                    + "Automatic repair is stopped.\n");
                    statusLabel.setText("Overlaps detected");
                    return;
            }

            if (result.gaps.isEmpty()) {
                    outputArea.append(
                                    "\nNo gaps within the specified integer domain.\n");
                    statusLabel.setText("Analysis complete — no gaps");
                    return;
            }

            List<RepairGenerator.Alternative> candidates = RepairGenerator.generate(
                            originalRules, domainMin, domainMax);

            outputArea.append("\nRepair alternatives:\n");

            for (RepairGenerator.Alternative candidate : candidates) {

                    RepairVerifier.Verification check = RepairVerifier.verify(
                                    originalRules, candidate,
                                    domainMin, domainMax);

                    outputArea.append(
                                    "\n" + candidate.description
                                                    + "\nStatus: "
                                                    + (check.valid ? "VERIFIED" : "REJECTED")
                                                    + "\nReason: " + check.reason + "\n");

                    if (check.valid) {
                            verified.add(candidate);
                            alternativesBox.addItem(
                                            verified.size() + ". "
                                                            + candidate.description);
                    }
            }

            if (verified.isEmpty()) {
                    outputArea.append(
                                    "\nNo verified repair within the allowed operations.\n");
            } else {
                    outputArea.append(
                                    "\nEach alternative is independent. "
                                                    + "Choose the intended business outcome below.\n");
                    chooseButton.setEnabled(true);
            }

            statusLabel.setText(
                            "Analysis complete — verified alternatives: "
                                            + verified.size());
    }

    private void chooseAlternative() {

        int index = alternativesBox.getSelectedIndex();

        if (originalRules == null || index < 0
                || index >= verified.size()) {
            return;
        }

        RepairGenerator.Alternative selected = verified.get(index);

        RepairVerifier.Verification check =
                RepairVerifier.verify(
                        originalRules, selected, domainMin, domainMax
                );

        if (!check.valid) {
            JOptionPane.showMessageDialog(
                    this, check.reason, "Repair rejected",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        BoundaryChecker.Result after =
                BoundaryChecker.check(
                        selected.rules, domainMin, domainMax
                );

        outputArea.append(
                "\n\nSELECTED ALTERNATIVE:\n"
                        + selected.description
                        + "\n\nRules for this alternative:\n"
        );

        appendRules(selected.rules);

        outputArea.append(
                "\nRemaining uncovered values: " + after.gaps
                        + "\nOverlapping values: " + after.overlaps
                        + "\nSelection applies to this session only.\n"
        );

        statusLabel.setText(
                "Alternative selected — original rules retained"
        );
        outputArea.setCaretPosition(
                outputArea.getDocument().getLength()
        );
    }

    private void appendRules(Rule[] rules) {

        for (Rule rule : rules) {

            List<String> conditions = new ArrayList<>();

            if (rule.lower != null) {
                conditions.add(
                        variable + (rule.includeLower ? " >= " : " > ")
                                + rule.lower
                );
            }

            if (rule.upper != null) {
                conditions.add(
                        variable + (rule.includeUpper ? " <= " : " < ")
                                + rule.upper
                );
            }

            String condition = conditions.isEmpty()
                    ? "Any integer"
                    : String.join(" AND ", conditions);

            outputArea.append(
                    rule.id + ": " + condition
                            + " -> " + rule.outcome + "\n"
            );
        }
    }

    private void showError(Throwable exception) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        String key = System.getenv("GEMINI_API_KEY");

        if (key != null && !key.isBlank()) {
            message = message.replace(key.trim(), "[HIDDEN]");
        }

        outputArea.setText("Analysis stopped:\n" + message);
        statusLabel.setText("Could not complete analysis");

        JOptionPane.showMessageDialog(
                this, message, "Analysis stopped",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void saveReport() {

            if (busy || originalRules == null) {
                    JOptionPane.showMessageDialog(
                                    this,
                                    "Extract the rules successfully before saving.",
                                    "Report not ready",
                                    JOptionPane.INFORMATION_MESSAGE);
                    return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save analysis report");
            chooser.setSelectedFile(
                            new java.io.File("acceptance-criteria-report.txt"));

            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return;
            }

            java.io.File file = chooser.getSelectedFile();

            if (!file.getName().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".txt")) {
                    file = new java.io.File(file.getAbsolutePath() + ".txt");
            }

            if (file.exists()) {
                    int choice = JOptionPane.showConfirmDialog(
                                    this,
                                    "This file already exists. Replace it?",
                                    "Replace report",
                                    JOptionPane.YES_NO_OPTION);

                    if (choice != JOptionPane.YES_OPTION) {
                            return;
                    }
            }

            String report = "ACCEPTANCE CRITERIA ANALYSIS REPORT\n"
                            + "Saved at: " + java.time.ZonedDateTime.now() + "\n"
                            + "Model: gemini-3.5-flash-lite\n"
                            + "Variable: " + variable + "\n"
                            + "Integer domain: " + domainMin
                            + " to " + domainMax + "\n\n"
                            + "ORIGINAL ACCEPTANCE CRITERIA\n"
                            + criteriaArea.getText().trim() + "\n\n"
                            + "DISPLAYED SESSION LOG\n"
                            + outputArea.getText() + "\n\n"
                            + "NOTES\n"
                            + "Verification applies to the extracted rules "
                            + "within the specified integer domain.\n"
                            + "It does not prove that the LLM interpretation "
                            + "matches the intended requirements.\n"
                            + "Alternatives are independent. If multiple selections "
                            + "appear, the last selection is the latest.\n";

            String key = System.getenv("GEMINI_API_KEY");

            if (key != null && !key.isBlank()) {
                    report = report.replace(key.trim(), "[HIDDEN]");
            }

            try {
                    java.nio.file.Files.writeString(
                                    file.toPath(),
                                    report,
                                    java.nio.charset.StandardCharsets.UTF_8);

                    statusLabel.setText("Report saved: " + file.getName());

                    JOptionPane.showMessageDialog(
                                    this,
                                    "Report saved successfully:\n"
                                                    + file.getAbsolutePath(),
                                    "Report saved",
                                    JOptionPane.INFORMATION_MESSAGE);

            } catch (java.io.IOException exception) {
                    JOptionPane.showMessageDialog(
                                    this,
                                    "Could not save report:\n"
                                                    + exception.getMessage(),
                                    "Save failed",
                                    JOptionPane.ERROR_MESSAGE);
            }
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new CheckerApp().setVisible(true);
        });
    }
}