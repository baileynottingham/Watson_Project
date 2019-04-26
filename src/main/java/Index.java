import edu.stanford.nlp.simple.Sentence;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class Index {

    public static void main(String args[]) {
        File wikiDirectory = new File("/Users/baileynottingham/Nottingham_Final_Project/src/main/resources/wiki-subset-20140602");

        // Create Lucene Index
        WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
        String indexPath = "/Volumes/Samsung_T5/Watson_Project_Indexes/lemma_index_boolean";
        Directory index = null;
        try {
            index = FSDirectory.open(Paths.get(indexPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        if(args[0].equals("-tfidf")) {
            config.setSimilarity(new ClassicSimilarity());
        }
        if(args[0].equals("-boolean")) {
            config.setSimilarity(new BooleanSimilarity());
        }
        IndexWriter w = null;
        try {
            w = new IndexWriter(index, config);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int counter = 1;
        for(int i = 0; i < wikiDirectory.listFiles().length; i++) {
            File file = wikiDirectory.listFiles()[i];
            try {
                DocumentType currentDocument = null;
                Scanner scanner = new Scanner(file);
                while (scanner.hasNextLine()) {
                    String currentLine = scanner.nextLine();
                    if (currentLine.matches("\\[\\[(?!File|Image:|Media:).*\\]\\]")) {
                        // This is a title
                        if (currentDocument == null) {
                            // initialize new document
                            currentDocument = new DocumentType(currentLine);
                        } else {
                            // Write previous to index
                            currentDocument.removeUnecessaryCharacters();
                            edu.stanford.nlp.simple.Document coreNLPDoc = new edu.stanford.nlp.simple.Document(currentDocument.getData());
                            String resultDataForDocument = "";
                            for (Sentence sent : coreNLPDoc.sentences()) {
                                for (String str : sent.lemmas()) {
                                    resultDataForDocument += str + " ";
                                }
                            }
                            addDoc(w, currentDocument.getTitle(), resultDataForDocument);
                            System.out.println(counter + ": Added Doc: " + currentDocument.getTitle());
                            counter++;
                            // Create new document
                            currentDocument = new DocumentType(currentLine);
                        }
                    } else {
                        // Not a title name
                        if(currentDocument != null) {
                            currentDocument.concatData(currentLine);
                        }
                    }
                }
                if(currentDocument != null) {
                    currentDocument.removeUnecessaryCharacters();
                    edu.stanford.nlp.simple.Document coreNLPDoc = new edu.stanford.nlp.simple.Document(currentDocument.getData());
                    String resultDataForDocument = "";
                    for (Sentence sent : coreNLPDoc.sentences()) {
                        for (String str : sent.lemmas()) {
                            resultDataForDocument += str + " ";
                        }
                    }
                    addDoc(w, currentDocument.getTitle(), resultDataForDocument);
                    System.out.println(counter + ": Added Doc: " + currentDocument.getTitle());
                    counter++;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        try {
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done");
    }

    private static void addDoc(IndexWriter w, String title, String data) {
        Document doc = new Document();
        doc.add(new StringField("title", title, Field.Store.YES));
        doc.add(new TextField("data", data, Field.Store.YES));
        try {
            w.addDocument(doc);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
