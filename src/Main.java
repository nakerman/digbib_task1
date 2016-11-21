import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Main{
    public static void main(String[] args) {
        if(args.length != 2) {
            System.out.println("Usage: 'java -jar <programname> <dir> <keyword");
            System.exit(2);
        }

        final String DIR = args[0];
        final String KEYWORD = args[1];


        // EXTRACT TEXT:

        ArrayList<File> files = new ArrayList<File>();
        ArrayList<String> documentsTitles = new ArrayList<String>();
        ArrayList<String> documentsText = new ArrayList<String>();

        getFilesInDirectory(files, DIR);

        try {
            PDFTextStripper stripper = new PDFTextStripper();

            for(File file : files) {
                if(!file.getName().substring(file.getName().length()-4).equals(".pdf")) continue;

                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                PDFParser parser =  new PDFParser(randomAccessFile);
                parser.parse();

                COSDocument cosDocument = parser.getDocument();
                PDDocument pdDocument = new PDDocument(cosDocument);
                PDDocumentInformation info = pdDocument.getDocumentInformation();

                String documentText = stripper.getText(pdDocument);
                documentsTitles.add(info.getTitle());
                documentsText.add(documentText);
                pdDocument.close();
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }


        // INDEX AND SEARCH

        try {

            // INDEX

            StandardAnalyzer analyzer = new StandardAnalyzer();
            Directory directory = new RAMDirectory();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter indexWriter = new IndexWriter(directory, config);

            for(int i = 0; i < documentsText.size(); i++)
            {
                String documentText = documentsText.get(i);
                String documentTitle = documentsTitles.get(i);

                FieldType fieldType = new FieldType(TextField.TYPE_STORED);
                fieldType.setStoreTermVectors(true);

                Document document = new Document();
                document.add(new Field("content", documentText, fieldType));
                indexWriter.addDocument(document);
            }

            indexWriter.close();

            // SEARCH
            Query query = new QueryParser("content", analyzer).parse(KEYWORD);

            int numberTopDocs = 1;
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, numberTopDocs);
            System.out.println("Found " + topDocs.totalHits + " hits.");

            /*
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;
            for(int i = 0; i < scoreDocs.length; i++) {
                Terms termVector = reader.getTermVector(scoreDocs[i].doc, "content");
                TermsEnum itr = termVector.iterator();
                BytesRef term = null;

                while((term = itr.next()) != null) {
                    String termText = term.utf8ToString();
                    Term termInstance = new Term("contents", term);
                    long termFreq = reader.totalTermFreq(termInstance);
                    long docCount = reader.docFreq(termInstance);

                    System.out.println("term: " + termText + ", termFreq = " + termFreq + ", docCount = " + docCount);
                }
            }
            */
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        catch(ParseException ex) {
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
