import java.awt.image.BufferedImage;

public class PdfPicture {

    static int idCounter;
    public int id;
    public String pdfName;
    public String score;
    public BufferedImage image;

    PdfPicture() {
        id = idCounter;
        idCounter++;
    }

    PdfPicture(BufferedImage image, String pdfName) {
        id = idCounter;
        idCounter++;

        this.image = image;
        this.pdfName = pdfName;
    }
}
