import net.semanticmetadata.lire.builders.DocumentBuilder;
import net.semanticmetadata.lire.builders.GlobalDocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.features.global.CEDD;
import net.semanticmetadata.lire.searchers.GenericFastImageSearcher;
import net.semanticmetadata.lire.searchers.ImageSearchHits;
import net.semanticmetadata.lire.searchers.ImageSearcher;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    static StandardAnalyzer analyzer = null;
    static FSDirectory fsDirectory = null;
    static ArrayList<File> pdfFiles = null;

    static HashMap<String, PdfPicture> images;

    public static void main(String[] args) {
        String DIR = ".";
        String SEARCHSTRING = "";

        System.out.println("- Please enter the directory containing the PDFs.");
        System.out.println("- The directory containing the jar-file is \".\" so a subdirectory would be accessed as \"./subdir\"");
        System.out.print("> ");

        Scanner scanner = new Scanner(System.in);
        scanner.useDelimiter(Pattern.compile("[\\n]"));
        ArrayList<String> documentsText = new ArrayList<String>();
        images = new HashMap<String, PdfPicture>();

        DIR = scanner.next();

        File folder = new File(DIR);
        while (folder.listFiles() == null || documentsText.size() == 0) {
            if (folder.listFiles() == null) {
                System.out.println("- The directory \"" + DIR + "\" does not exist, please enter a valid directory. (did you forget the dot?)");
                System.out.print("> ");
            } else {
                System.out.println("");
                System.out.println("- Indexing PDF-Text. This may take some time...");
                extractImages(DIR);

                if(images.size() != 0)
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

        System.out.println("- Finished Indexing PDF-Text.");
        System.out.println("");

        System.out.println("- Indexing PDF-Images. This may take some time...");
        indexImages();
        System.out.println("- Finished Indexing PDF-Images.");
        System.out.println("");

        System.out.println("- Please select image (i) or text(t) based search: ");
        System.out.print("> ");
        String searchType = scanner.next();

        if(searchType.toLowerCase().equals("i"))
        {
            System.out.println("Preparing...");
            openWindow();
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
    public static void openWindow()
    {
        final int BUTTON_SIZE = 400;
        int button_id = 0;
        JFrame frame = new JFrame("Image Based Search");
        frame.setLayout(new GridLayout(1, 2));
        //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(800 * 2, 1000));

        final GridLayout grid = new GridLayout(0, 1);
        final GridLayout subGrid = new GridLayout(1, 0);

        JPanel content = new JPanel();
        content.setLayout(grid);
        content.setMaximumSize(new Dimension(400, images.size() * 400));

        final JPanel result = new JPanel();
        result.setSize(new Dimension(400, 600));
        result.setLayout(grid);
        JLabel tutorial = new JLabel("<html>Choose an image on the left,<br><br>" +
                "and similar images will be displayed here.</html>", SwingConstants.CENTER);
        result.add(tutorial);
        final JScrollPane resultPane = new JScrollPane(result);

        Iterator itr = images.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry<String, PdfPicture> pair = (Map.Entry<String, PdfPicture>) itr.next();
            final PdfPicture pdfPicture = pair.getValue();
            int imageWidth = pdfPicture.image.getWidth();
            int imageHeight = pdfPicture.image.getHeight();
            int scaledWidth, scaledHeight = 0;
            if(imageWidth > imageHeight) {
                scaledWidth = (int) (BUTTON_SIZE);
                scaledHeight = (int) (BUTTON_SIZE * (imageHeight / (float) imageWidth));
            } else {
                scaledWidth = (int) (BUTTON_SIZE * (imageWidth / (float) imageHeight));
                scaledHeight = (int) (BUTTON_SIZE );
            }

            JButton button = new JButton(new ImageIcon(pdfPicture.image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH)));
            button.setSize(BUTTON_SIZE, BUTTON_SIZE);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setName(pair.getKey());

            // **************************** Button Logic ****************************
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JButton btn = (JButton) e.getSource();
                    ArrayList<String> ids = searchImages(images.get(btn.getName()).image, 10);

                    result.removeAll();
                    ids.remove(0);
                    for (int i = ids.size() - 1; i >= 0; i--) {
                        String id = ids.get(i);
                        PdfPicture pdfPicture = images.get(id);
                        int imageWidth = pdfPicture.image.getWidth();
                        int imageHeight = pdfPicture.image.getHeight();
                        int scaledWidth, scaledHeight = 0;
                        if (imageWidth > imageHeight) {
                            scaledWidth = (int) (BUTTON_SIZE);
                            scaledHeight = (int) (BUTTON_SIZE * (imageHeight / (float) imageWidth));
                        } else {
                            scaledWidth = (int) (BUTTON_SIZE * (imageWidth / (float) imageHeight));
                            scaledHeight = (int) (BUTTON_SIZE);
                        }

                        JButton button = new JButton(new ImageIcon(pdfPicture.image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH)));
                        button.setSize(BUTTON_SIZE, BUTTON_SIZE);
                        button.setOpaque(false);
                        button.setContentAreaFilled(false);

                        JPanel subPanel = new JPanel();
                        subPanel.setLayout(subGrid);
                        subPanel.setPreferredSize(new Dimension(400, button.getHeight()));
                        subPanel.add(button);

                        String infoText = "<html>" + pdfPicture.pdfName + "<br><br> score: " + pdfPicture.score;
                        JLabel info = new JLabel(infoText, SwingConstants.CENTER);
                        subPanel.add(info);
                        result.add(subPanel);
                    }
                    JViewport jv = resultPane.getViewport();
                    jv.setViewPosition(new Point(0, 0)); // scroll to top
                    resultPane.setViewportView(result);
                }
            });
            // ********************************************************************

            JPanel subPanel = new JPanel();
            subPanel.setLayout(subGrid);
            subPanel.setPreferredSize(new Dimension(400, button.getHeight()));
            subPanel.add(button);

            JLabel info = new JLabel(pdfPicture.pdfName, SwingConstants.CENTER);
            subPanel.add(info);

            content.add(subPanel);
        }
        JScrollPane scrollPane = new JScrollPane(content);

        frame.add(scrollPane);
        frame.add(resultPane);
        frame.pack();
        frame.setVisible(true);
    }

    private static void indexImages() {
        try{
            GlobalDocumentBuilder docBuilder = new GlobalDocumentBuilder(CEDD.class);
            IndexWriter indexWriter = LuceneUtils.createIndexWriter("image_index", true, LuceneUtils.AnalyzerType.WhitespaceAnalyzer);

            Iterator itr = images.entrySet().iterator();
            while(itr.hasNext())
            {
                Map.Entry<String, PdfPicture> pair = (Map.Entry<String, PdfPicture>) itr.next();
                PdfPicture pdfPicture = pair.getValue();

                BufferedImage convertedImage = new BufferedImage(pdfPicture.image.getWidth(), pdfPicture.image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                convertedImage.getGraphics().drawImage(pdfPicture.image, 0, 0, null);
                Document document = docBuilder.createDocument(convertedImage, pdfPicture.id + "");
                indexWriter.addDocument(document);
            }

            LuceneUtils.closeWriter(indexWriter);
        } catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    private static void extractImages(String directory)
    {
        int id = 0;
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
                            PDXObject o = pdResources.getXObject(n);
                            if(o instanceof PDImageXObject)
                            {
                                PDImageXObject im = (PDImageXObject)o;
                                BufferedImage img = im.getImage();

                                PdfPicture pdfPicture = new PdfPicture(img, file.getName());
                                images.put(pdfPicture.id + "", pdfPicture);
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
            //searcher.setSimilarity(new FairSim());
            topDocs = searcher.search(query, numberTopDocs);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }

        return topDocs;
    }

    private static ArrayList<String> searchImages(BufferedImage img, int numberTopDocs) {
        ArrayList<String> pdfPictureIds = new ArrayList<String>();
        try {
            IndexReader ir = DirectoryReader.open(FSDirectory.open(Paths.get("image_index")));
            ImageSearcher searcher = new GenericFastImageSearcher(numberTopDocs, CEDD.class);

            BufferedImage convertedImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            convertedImage.getGraphics().drawImage(img, 0, 0, null);
            ImageSearchHits hits = searcher.search(convertedImage, ir);

            for(int i = 0; i < hits.length(); i++) {
                String id = ir.document(hits.documentID(i)).getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
                images.get(id).score = hits.score(i) + "";
                pdfPictureIds.add(id);
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        return pdfPictureIds;
    }

    private static ScoreDoc[] defaultScorer(TopDocs topDocs) {
	ScoreDoc[] scoreDocs = topDocs.scoreDocs;
	return scoreDocs;
    }
}

/*
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
*/