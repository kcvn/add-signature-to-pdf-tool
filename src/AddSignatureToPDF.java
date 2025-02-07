import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import org.apache.tika.Tika;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class AddSignatureToPDF {
    public static void main(String[] args) {
//        String inputPdfPath = "U:/32. RPA (New)/Chen chu ky PO/AddSignatureToPDF/input.pdf";
//        String outputPdfPath = "U:/32. RPA (New)/Chen chu ky PO/AddSignatureToPDF/output.pdf";
//        String signaturePath = "U:/32. RPA (New)/Chen chu ky PO/AddSignatureToPDF/signature.png";
//        String outputFolderPath = "U:/32. RPA (New)/Chen chu ky PO/AddSignatureToPDF";
        String inputPdfPath = "D:/AddSignatureToPDF/input.pdf";
        String outputPdfPath = "D:/AddSignatureToPDF/output.pdf";
        String signaturePath = "D:/AddSignatureToPDF/signature.png";
        String outputFolderPath = "D:/AddSignatureToPDF";
        try {
            addSignature(inputPdfPath, outputPdfPath, signaturePath);
            processPDF(outputPdfPath, outputFolderPath);

            JOptionPane.showMessageDialog(null,
                    "Thành công!",
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Đã xảy ra lỗi: " + e.getMessage(),
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void addSignature(String inputPdfPath, String outputPdfPath, String signaturePath) {
        try {
            // Đọc tệp PDF
            PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdfPath), new PdfWriter(outputPdfPath));
            Document document = new Document(pdfDoc);

            // Đọc tệp ảnh chữ ký
            ImageData imageData = ImageDataFactory.create(signaturePath);
            Image signature = new Image(imageData);

            // Kích thước chữ ký
            float dpi = 120;
            float signatureWidthInPoints = (66 / dpi) * 60;
            float signatureHeightInPoints = (312 / dpi) * 60;

            // Đặt kích thước chữ ký
            signature.scaleToFit(signatureWidthInPoints, signatureHeightInPoints);

            // Vị trí chữ ký
            float x = 555;  // Khoảng cách từ mép dưới
            float y = 100; // Khoảng cách từ mép trái

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                // Đặt vị trí của chữ ký cho từng trang
                signature.setFixedPosition(i, x, y);
                document.add(signature);
            }

            document.close();

            System.out.println("Success: " + outputPdfPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void processPDF(String inputFilePath, String outputFolderPath) {
        try {
            // Mở tài liệu PDF đầu vào
            PdfReader reader = new PdfReader(inputFilePath);
            PdfDocument pdfDoc = new PdfDocument(reader);

            Map<String, PdfDocument> supplierDocs = new HashMap<>();

            // Tạo thư mục chung "output"
            String mainOutputFolder = outputFolderPath + "/output";
            File mainFolder = new File(mainOutputFolder);
            if (!mainFolder.exists()) {
                mainFolder.mkdirs();
            }

            for (int pageNum = 1; pageNum <= pdfDoc.getNumberOfPages(); pageNum++) {
                // Tạo một tài liệu mới chỉ chứa 1 trang tạm thời
                PdfDocument singlePageDoc = new PdfDocument(new PdfWriter(outputFolderPath + "/temp_page.pdf"));
                pdfDoc.copyPagesTo(pageNum, pageNum, singlePageDoc);
                singlePageDoc.close();

                // Lấy mã nhà cung cấp từ trang PDF
                String pageContent = getContent(outputFolderPath + "/temp_page.pdf");
                String supplierCode = extractFormattedString(pageContent);
                String orderSheet = extractWordBeforeOrderSheet(pageContent);

                // Đường dẫn lưu file PDF trực tiếp trong thư mục "output"
                String supplierPdfPath;
                assert orderSheet != null;
                if (orderSheet.equals("PURCHASE")) {
                    supplierPdfPath = mainOutputFolder + "/" + supplierCode + ".pdf";
                } else {
                    supplierPdfPath = mainOutputFolder + "/" + supplierCode + "-COS" + ".pdf";
                }

                // Mở hoặc tạo file PDF cho mã nhà cung cấp
                PdfDocument supplierPdfDoc = supplierDocs.computeIfAbsent(supplierPdfPath, path -> {
                    try {
                        return new PdfDocument(new PdfWriter(path));
                    } catch (Exception e) {
                        throw new RuntimeException("Lỗi khi tạo file PDF: " + e.getMessage());
                    }
                });

                // Thêm trang vào file PDF
                PdfDocument tempDoc = new PdfDocument(new PdfReader(outputFolderPath + "/temp_page.pdf"));
                tempDoc.copyPagesTo(1, 1, supplierPdfDoc);
                tempDoc.close();
            }

            for (PdfDocument doc : supplierDocs.values()) {
                doc.close();
            }
            pdfDoc.close();
            reader.close();

            // Xóa file tạm
            new File(outputFolderPath + "/temp_page.pdf").delete();

            File outputPdfFile = new File(outputFolderPath + "/output.pdf");
            File newOutputPdfFile = new File(mainOutputFolder + "/output.pdf");
            if (outputPdfFile.exists()) {
                Files.move(outputPdfFile.toPath(), newOutputPdfFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (newOutputPdfFile.exists()) {
                newOutputPdfFile.delete();
            }

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình xử lý PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Hàm đọc nội dung file PDF sử dụng Apache Tika
    public static String getContent(String filePath) {
        String content = "";
        try {
            Tika tika = new Tika();
            content = tika.parseToString(new File(filePath));
        } catch (Exception e) {
            System.err.println("Lỗi khi đọc file: " + e.getMessage());
        }
        return content;
    }

    public static String extractFormattedString(String input) {
        String[] lines = input.split("\n");
        String supplierCode = lines[28].split(" ")[0].trim();
        String companyName = normalizeText(lines[30].trim());
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (companyName.contains("KINTETSU")) {
            return "KINTETSU-" + currentDate;
        }
        return supplierCode + "-" + companyName + "-" + currentDate;
    }

    public static String extractWordBeforeOrderSheet(String text) {
        String target = "ORDER SHEET";
        int index = text.indexOf(target);
        if (index > 0) {
            String[] words = text.substring(0, index).trim().split("\\s+");
            return words.length > 0 ? words[words.length - 1] : null;
        }
        return null;
    }

    public static String normalizeText(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        return normalized.replace("�", " ");
    }
}
