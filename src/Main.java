import com.sun.org.apache.xpath.internal.objects.XObject;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdmodel.graphics.*;
import org.apache.pdfbox.pdmodel.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    static StandardAnalyzer analyzer = null;
    static FSDirectory fsDirectory = null;
    static ArrayList<File> pdfFiles = null;

    public static void main(String[] args) {
        String DIR = ".";
        String SEARCHSTRING = "";

        System.out.println("- Please enter the directory containing the PDFs.");
        System.out.println("- The directory containing the jar-file is \".\" so a subdirectory would be accessed as \"./subdir\"");
        System.out.print("> ");

        Scanner scanner = new Scanner(System.in);
        scanner.useDelimiter(Pattern.compile("[\\n]"));
        ArrayList<String> documentsText = new ArrayList<String>();
        Map<PDDocument, ArrayList<BufferedImage>> imgs = new HashMap<PDDocument, ArrayList<BufferedImage>>();

        DIR = scanner.next();

        File folder = new File(DIR);
        while (folder.listFiles() == null || documentsText.size() == 0) {
            if (folder.listFiles() == null) {
                System.out.println("- The directory \"" + DIR + "\" does not exist, please enter a valid directory. (did you forget the dot?)");
                System.out.print("> ");
            } else {
                System.out.println("");
                System.out.println("- Indexing PDFs. This may take some time...");
                imgs = extractImages(DIR);
                if(imgs.size() != 0)
                    break;
                // EXTRACT TEXT:
                //documentsText = extractTextFromPdfs(DIR);

               // if (documentsText.size() != 0) break;

                //System.out.println("- The directory \"" + DIR + "\" does not contain any PDFs, please enter a valid directory.");
                //System.out.print("> ");
            }

            scanner = new Scanner(System.in);
            DIR = scanner.next();
            folder = new File(DIR);
        }


        //INDEX
        //indexPdfs(documentsText, DIR);

        System.out.println("- Finished Indexing PDFs.");
        System.out.println("");

        System.out.println("- Please select image (I) or text(T) based search: ");
        System.out.print("> ");
        String searchType = scanner.next();

        if(searchType.equals("I"))
        {
            openWindow(imgs);
        }

        System.out.println("- Please enter the term(s) you would like to search for. You may use AND and/or OR as keywords.");
        System.out.println("- Examples: \"apple\"; \"apple AND pear\"; \"(apple OR pear) AND sauce\".");
        System.out.print("> ");

        while (true) {
            SEARCHSTRING = scanner.next();
            if (SEARCHSTRING.equals("diglib quit")) break;

            TopDocs topDocs = null;
            try {
                //SEARCH
                topDocs = searchPdfs(SEARCHSTRING, 10);
            } catch (ParseException ex) {
                System.out.println("");
                System.out.println("- The search-string you entered contains invalid syntax. Please enter a valid search-string.");
                System.out.print("> ");
                continue;
            }
            ScoreDoc[] scoreDocs = defaultScorer(topDocs);

            System.out.println("- A total of " + topDocs.totalHits + " PDFs matched your query.");
            System.out.println("- The top results are:");

            for (int id = 0; id < scoreDocs.length; id++) {
                System.out.println("- " + pdfFiles.get(scoreDocs[id].doc).getName() + " with a score of: " + scoreDocs[id].score);
            }

            System.out.println("");
            System.out.println("- Please enter the term(s) for your next search. You may quit anytime by entering \"diglib quit\".");
            System.out.print("> ");
        }
    }

    /**
     * @param directory directory containing the pdfs (subdirectories are searched aswel)
     * @return list containing the text of the pdfs
     */
    private static ArrayList<String> extractTextFromPdfs(String directory) {
        ArrayList<String> documentsText = new ArrayList<String>();
        ArrayList<File> files = new ArrayList<File>();
        pdfFiles = new ArrayList<File>();

        getFilesInDirectory(files, directory);

        try {
            PDFTextStripper stripper = new PDFTextStripper();

            for (File file : files) {
                if (!file.getName().substring(file.getName().length() - 4).equals(".pdf")) continue;
                pdfFiles.add(file);
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                PDFParser parser = new PDFParser(randomAccessFile);
                parser.parse();

                COSDocument cosDocument = parser.getDocument();
                PDDocument pdDocument = new PDDocument(cosDocument);

                String documentText = stripper.getText(pdDocument);
                documentsText.add(documentText);
                pdDocument.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return documentsText;
    }

    /// Open image browser
    public static void openWindow(Map<PDDocument, ArrayList<BufferedImage>> imgs)
    {
        JFrame frame = new JFrame("Image Based Search");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GridBagConstraints gc=new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        //gc.anchor = GridBagConstraints.LINE_START;
        gc.insets = new Insets(0, 0, 0, 0);
        gc.weightx = 1.0;

        List<ArrayList<BufferedImage>> imMap = new ArrayList<ArrayList<BufferedImage>>(imgs.values());
        List<BufferedImage> imList = new ArrayList<BufferedImage>();
        for(ArrayList<BufferedImage> a: imMap)
        {
            imList.addAll(a);
        }

        GridBagLayout gridLayout = new GridBagLayout();
        JPanel contentPane = new JPanel(gridLayout);

        ArrayList<JLabel> labels = new ArrayList<JLabel>();
        int counter = 0;
        for(int i = 0; i < imList.size(); i+=3)
        {
            JPanel pan = new JPanel();

            labels.add(new JLabel(new ImageIcon(imList.get(i))));
            labels.get(i).setPreferredSize(new Dimension(200, 200));
            pan.add(labels.get(i));

            if(i + 1 < imList.size()) {
                labels.add(new JLabel(new ImageIcon(imList.get(i + 1))));
                labels.get(i + 1).setPreferredSize(new Dimension(200, 200));
                pan.add(labels.get(i + 1));
            }

            if(i + 2 < imList.size()) {
                labels.add(new JLabel(new ImageIcon(imList.get(i + 2))));
                labels.get(i + 2).setPreferredSize(new Dimension(200, 200));
                pan.add(labels.get(i + 2));
            }
            contentPane.add(pan, gc);
            gc.gridy += 1;
        }
        JScrollPane scrollPane = new JScrollPane(contentPane);

        frame.add(scrollPane);
        frame.pack();
        frame.setVisible(true);
    }

    private static Map<PDDocument, ArrayList<BufferedImage>> extractImages(String directory)
    {
        Map<PDDocument, ArrayList<BufferedImage>> images = new HashMap<PDDocument, ArrayList<BufferedImage>>();
        ArrayList<File> files = new ArrayList<File>();
        pdfFiles = new ArrayList<File>();

        getFilesInDirectory(files, directory);

        try {
            for(File file : files) {
                if(!file.getName().substring(file.getName().length()-4).equals(".pdf")) continue;
                pdfFiles.add(file);
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                PDFParser parser =  new PDFParser(randomAccessFile);
                parser.parse();

                COSDocument cosDocument = parser.getDocument();
                PDDocument pdDocument = new PDDocument(cosDocument);

                PDPageTree pages = pdDocument.getDocumentCatalog().getPages();
                Iterator<PDPage> pageIter = pages.iterator();
                while(pageIter.hasNext())
                {
                    PDResources pdResources = pageIter.next().getResources();
                    //ArrayList<COSName> objList = (ArrayList<COSName>)pdResources.getXObjectNames();
                    for(COSName n: pdResources.getXObjectNames())
                    {
                        if(pdResources.isImageXObject(n))
                        {
                            images.putIfAbsent(pdDocument, new ArrayList<BufferedImage>());
                            PDXObject o = pdResources.getXObject(n);
                            if(o instanceof PDImageXObject)
                            {
                                PDImageXObject im = (PDImageXObject)o;
                                images.get(pdDocument).add(im.getImage());
                            }
                        }
                    }
                }

                pdDocument.close();
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        return images;
    }
    /**
     * @param files found pdfs
     * @param dirName directory to be searched in (subdirectories are also searched)
     */
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

    /**
     * @param documentsText list containing the text of the pdfs
     */
    private static void indexPdfs(ArrayList<String> documentsText, String directory) {
        try {
            analyzer = new StandardAnalyzer();
            fsDirectory = FSDirectory.open(Paths.get(directory + "/indexfiles"));
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter indexWriter = new IndexWriter(fsDirectory, config);

            for(int i = 0; i < documentsText.size(); i++)
            {
                String documentText = documentsText.get(i);

                FieldType fieldType = new FieldType(TextField.TYPE_STORED);
                fieldType.setStoreTermVectors(true);

                Document document = new Document();
                document.add(new Field("content", documentText, fieldType));

                //get first 100 words of string
	            ArrayList<String> stringArr = new ArrayList<String>(Arrays.asList(documentText.split("\\s+")));
		        String introString = String.join(" ", stringArr.subList(0, 101));
		        //if appears in first 100 words, then is more valid
                Field intro = new Field("intro", introString, fieldType);
                intro.setBoost(5.0f);
		        document.add(intro);
		
                indexWriter.addDocument(document);
            }

            indexWriter.close();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * @param searchString e.g.: "(apple OR pear) AND sauce"
     * @param numberTopDocs number of top results to be returned
     * @return pdfs with top results
     */
    private static TopDocs searchPdfs(String searchString, int numberTopDocs) throws ParseException {
        TopDocs topDocs = null;
        try {
            Query query = new QueryParser("content", analyzer).parse(searchString);

            IndexReader reader = DirectoryReader.open(fsDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new FairSim());
            topDocs = searcher.search(query, numberTopDocs);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }

        return topDocs;
    }

    private static ScoreDoc[] defaultScorer(TopDocs topDocs) {
	ScoreDoc[] scoreDocs = topDocs.scoreDocs;
	return scoreDocs;
    }
}

//modified similarity classes
class FairSim extends ClassicSimilarity {

    //@Override
    //does not take into account the length of the field, thus producing "fair" indexing
    public float lengthNorm (FieldInvertState state) {
	    return (float)(1.0 / state.getLength());
    }

    @Override
    //does not sqrt the frequency of term in document, thus considering only term freq
    public float tf(float freq) {
	return (float)freq;
    }

}
