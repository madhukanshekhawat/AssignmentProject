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
            if (supplierDetails != null) {
                csvData.add(new String[]{supplierDetails[0]});
                csvData.add(new String[]{supplierDetails[1]});
                csvData.add(new String[]{supplierDetails[2]});
            }

            // Step 2: Extract payment details
            String[] paymentDetails = extractPaymentDetails(pdfPath);
            if (paymentDetails != null) {
                csvData.add(new String[]{paymentDetails[0]});
                csvData.add(new String[]{paymentDetails[1]});
            }

            // Add the header row
            addBoldHeaderRow(csvData);

            // Step 3: Extract table data
            List<String[]> tableData = extractTableDataFromPdf(pdfPath);
            csvData.addAll(tableData);

            // Step 4: Calculate total gross amount
            double totalGrossAmount = calculateTotalGrossAmount(tableData);
            csvData.add(new String[]{"","","Total Sum", String.format("%.2f", totalGrossAmount)});

            // Write to CSV
            writeTableToCSV(csvPath, csvData);

            System.out.println("Table data successfully converted to CSV");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addBoldHeaderRow(List<String[]> csvData) {
        csvData.add(new String[]{
                "Invoice Document", "Invoice Number", "Invoice Date",
                "Gross Amount", "Discount Amount", "Net Amount"
        });
    }

    private static double calculateTotalGrossAmount(List<String[]> tableData) {
        double total = 0.0;
        for(String[] row : tableData){
            try{
                double grossAmount = Double.parseDouble(row[3].replace(",", ""));
                total += grossAmount;
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return total;
    }

    private static String[] extractPaymentDetails(String pdfPath) throws IOException{
        try(PDDocument document = PDDocument.load(new File(pdfPath))){
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);

                for(Table table : tables){
                    for(int row=0; row<table.getRowCount(); row++){
                        String paymentDocument = table.getCell(row,0).getText().trim();
                        String currency = table.getCell(row, 2).getText().trim();

                        if(paymentDocument.toLowerCase().contains("payment document")){
                            extractor.close();
                            return new String[]{paymentDocument, currency};
                        }
                    }
                }
            }
            extractor.close();
        }
        return null;
    }

    private static String[] extractSupplierDetails(String pdfPath) throws IOException {
        try (
                PDDocument document = PDDocument.load(new File(pdfPath))) {
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);

                if (!tables.isEmpty()) {
                    Table table = tables.get(0);
                    if (table.getRowCount() > 0 && table.getColCount() > 1) {
                        String supplier = table.getCell(1, 1).getText().trim();
                        String supplerNo = table.getCell(1, 3).getText().trim();
                        String date = table.getCell(1,4).getText();
                        extractor.close();
                        return new String[]{"Supplier: " + supplier,
                                "Supplier No.: " + supplerNo,
                                date };
                    }
                }
            }
            extractor.close();
        }
        return null;
    }


    private static List<String[]> extractTableDataFromPdf(String pdfPath) throws IOException {
        List<String[]> tableData = new ArrayList<>();

        try(PDDocument document = PDDocument.load(new File(pdfPath))){
            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();

            for (PageIterator it = extractor.extract(); it.hasNext(); ) {
                Page page = it.next();
                List<Table> tables = algo.extract(page);
                for(Table table : tables){
                    for(int row=0; row<table.getRowCount(); row++){
                        String firstColumn = table.getCell(row,0).getText().trim();
                        if(row < 3) continue;

                        if(firstColumn.toLowerCase().contains("sum total") || firstColumn.toLowerCase().contains("balance carry forward") || firstColumn.toLowerCase().contains("invoice document") || firstColumn.toLowerCase().contains("payment document")){
                            continue;
                        }

                        int columnCount = table.getColCount();
                        String[] rowData = new String[columnCount];

                        for(int col=0;col<columnCount ;col++){
                            rowData[col]=table.getCell(row,col).getText().trim();
                        }
                        tableData.add(rowData);
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



//String supplier = table.getCell(row,0).getText();
//String supplerNo = table.getCell(row,1).getText();
//String invoiceDocument = table.getCell(row,2).getText();
//String invoiceNumber = table.getCell(row,3).getText();
//String invoiceDate = table.getCell(row,4).getText();
//String grossAmount = table.getCell(row,5).getText();
//String discountAmount = table.getCell(row,6).getText();
//String netAmount = table.getCell(row,7).getText();
//
//                        filteredData.add(new String[]{
//    supplier, supplerNo, invoiceDocument, invoiceNumber, invoiceDate, grossAmount, discountAmount, netAmount,
//});


//        String pdfPath = "C:\\Users\\MadhuShekhawat\\Downloads\\GA.PDF";
//        File file = new File(pdfPath);
//        if (file.exists()) {
//            System.out.println("File found!");
//        } else {
//            System.out.println("File not found!");
//        }
