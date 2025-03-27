package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        try{
            System.out.println("Enter Pdf File Path:");
            String pdfPath = reader.readLine().replace("file:///", "").replace("/", "\\\\");

            System.out.println("Enter output CSV file path: ");
            String csvPath = reader.readLine().replace("file:///", "").replace("/", "\\\\");

            List<String[]> csvData = new ArrayList<>();
            // Step 1: Extract supplier details
            String[] supplierDetails = extractSupplierDetails(pdfPath);

            // Step 2: Extract payment details
            String[] paymentDetails = extractPaymentDetails(pdfPath);

            // Add the header row
            addBoldHeaderRow(csvData);

            // Step 3: Extract table data and prepend supplier/payment details
            List<String[]> tableData = extractTableDataFromPdf(pdfPath);
            if (supplierDetails != null && paymentDetails != null) {

                // Prepend supplier and payment details to each row in tableData
                List<String[]> updatedTableData = new ArrayList<>();
                for (String[] row : tableData) {
                    String[] updatedRow = new String[row.length + 5];
                    if (supplierDetails != null) {
                        updatedRow[0] = supplierDetails[0];
                        updatedRow[1] = supplierDetails[1];
                        updatedRow[2] = supplierDetails[2].replaceAll("^Date:\\s*", "").trim();
                    }
                    if (paymentDetails != null) {
                        updatedRow[3] = paymentDetails[0].replace("Payment Document:", "").trim();
                        updatedRow[4] = paymentDetails[1].replace("Currency:", "").trim();
                    }

                    // Copy the rest of the row data
                    System.arraycopy(row, 0, updatedRow, 5, row.length);

                    updatedTableData.add(updatedRow);
                }

                csvData.addAll(updatedTableData);
            }

            // Step 4: Calculate total gross amount
            double totalGrossAmount = calculateTotalGrossAmount(tableData);
            csvData.add(new String[]{"","","","","","","","Total Sum", String.format("%.2f", totalGrossAmount)});

            // Write to CSV
            writeTableToCSV(csvPath, csvData);

            System.out.println("Table data successfully converted to CSV");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addBoldHeaderRow(List<String[]> csvData) {
        csvData.add(new String[]{
                "Supplier", "Supplier No.", "Date", "Payment Document", "Currency",
                "Invoice Document", "Invoice Number", "Invoice Date",
                "Gross Amount", "Discount Amount", "Net Amount"
        });
    }

    private static double calculateTotalGrossAmount(List<String[]> tableData) {
        double total = 0.0;

        for (String[] row : tableData) {
            if (row.length > 3) { // Check if the row has at least 4 columns
                try {
                    String grossAmountStr = row[3].trim();
                    if (!grossAmountStr.isEmpty()) { // Check if the gross amount is not empty
                        double grossAmount = Double.parseDouble(grossAmountStr.replace(",", ""));
                        total += grossAmount;
                    }
                } catch (NumberFormatException e) {
                    // Handle invalid number format gracefully
                    System.err.println("Invalid gross amount format in row: " + e.getMessage());
                }
            } else {
                System.err.println("Row has fewer than 4 columns, skipping.");
            }
        }

        return total;
    }

    private static String[] extractPaymentDetails(String pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);

                for (Table table : tables) {
                    for (int row = 0; row < table.getRowCount(); row++) {
                        // Ensure row has enough columns before accessing
                        if (table.getColCount() > 2) {
                            String paymentDocument = table.getCell(row, 0).getText().trim();
                            String currency = table.getCell(row, 2).getText().trim();

                            // Validate that both fields are non-empty
                            if (!paymentDocument.isEmpty() && !currency.isEmpty() &&
                                    paymentDocument.toLowerCase().contains("payment document")) {
                                extractor.close();
                                return new String[]{paymentDocument, currency};
                            }
                        } else {
                            System.err.println("Row does not have enough columns, skipping.");
                        }
                    }
                }
            }
            extractor.close();
        }
        return null;
    }


    private static String[] extractSupplierDetails(String pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);

                if (!tables.isEmpty()) {
                    Table table = tables.get(0);
                    if (table.getRowCount() > 0 && table.getColCount() > 1) {
                        // Extracting values and checking for empty fields
                        String supplier = table.getCell(1, 1).getText().trim();
                        String supplierNo = table.getCell(1, 3).getText().trim();
                        String date = table.getCell(1, 4).getText().trim();

                        if (!supplier.isEmpty() && !supplierNo.isEmpty() && !date.isEmpty()) {
                            extractor.close();
                            return new String[]{
                                    supplier,
                                    supplierNo,
                                    date
                            };
                        }
                    }
                }
            }
            extractor.close();
        }
        return null;
    }


    private static List<String[]> extractTableDataFromPdf(String pdfPath) throws IOException {
        List<String[]> tableData = new ArrayList<>();

        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);

                for (Table table : tables) {
                    for (int row = 0; row < table.getRowCount(); row++) {
                        String firstColumn = table.getCell(row, 0).getText().trim();
                        if (row < 3) continue;

                        if (firstColumn.toLowerCase().contains("sum total") ||
                                firstColumn.toLowerCase().contains("balance carry forward") ||
                                firstColumn.toLowerCase().contains("invoice document") ||
                                firstColumn.toLowerCase().contains("payment document")) {
                            continue;
                        }

                        int columnCount = table.getColCount();
                        String[] rowData = new String[columnCount];
                        boolean isEmpty = false;

                        for (int col = 0; col < columnCount; col++) {
                            String cellText = table.getCell(row, col).getText().trim();
                            rowData[col] = cellText;

                            if (cellText.isEmpty()) {
                                isEmpty = true;
                            }
                        }

                        if (!isEmpty) {
                            tableData.add(rowData);
                        }
                    }
                }
            }
            extractor.close();
        }
        return tableData;
    }


    private static void writeTableToCSV(String csvPath, List<String[]> data) throws IOException {
        /**
         * Using Apache Commons CSV instead of OpenCSV
         * OpenCSV dependency (CSVWriter) was previously used but has some limitations:
         *  - It can be complex for advanced use cases.
         *  - Slower compared to Apache Commons CSV for large datasets.
         *  - Requires more manual handling for certain operations, such as quoting or formatting.
         * We are now using the Apache Commons CSV dependency, which is more efficient, flexible, and easier to use.
         */
//        try(CSVWriter writer = new CSVWriter(new FileWriter(csvPath))){
//            writer.writeAll(data);
//        }
        try(FileWriter out = new FileWriter(csvPath);
            CSVPrinter printer = new CSVPrinter(out,CSVFormat.DEFAULT)){
            for(String[] row: data){
                printer.printRecord((Object[]) row);
            }
        }
    }
}
