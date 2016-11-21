import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Main{
    public static void main(String[] args) {
        if(args.length != 1) {
            System.out.println("Usage: 'java -jar <programname> <command>");
            System.exit(2);
        }

        ArrayList<File> files = new ArrayList<
                File>();
        getFilesInDirectory(files, args[0]);

        try {
            PDFTextStripper stripper = new PDFTextStripper();

            for(File file : files) {
                if(!file.getName().substring(file.getName().length()-4).equals(".pdf")) continue;

                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                PDFParser parser =  new PDFParser(randomAccessFile);
                parser.parse();

                COSDocument cosDocument = parser.getDocument();
                PDDocument pdDocument = new PDDocument(cosDocument);

                String text = stripper.getText(pdDocument);
                System.out.println(text);
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void getFilesInDirectory(ArrayList<File> files, String dirName) {
        File folder = new File(dirName);
        ArrayList<File> folderFiles = new ArrayList<File>(Arrays.asList(folder.listFiles()));

        for(File file : folderFiles) {
            if(file.isFile()) {
                files.add(file);
            }
            else if(file.isDirectory()) {
                getFilesInDirectory(files, dirName + "/" + file.getName());
            }
        }
    }
}
