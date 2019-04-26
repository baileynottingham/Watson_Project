import edu.stanford.nlp.simple.Sentence;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.clulab.processors.Processor;
import org.clulab.processors.corenlp.CoreNLPProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class WatsonPOS {

    public static void main(String args[]) {
        // Parse Questions
        File questionFile = new File("/Users/baileynottingham/Nottingham_Final_Project/src/main/resources/questions.txt");
        ArrayList<Question> questions = new ArrayList<>();
        try {
            Scanner scanner = new Scanner(questionFile);
            while(scanner.hasNextLine()) {
                String category = scanner.nextLine();
                String question = scanner.nextLine();
                String answer = scanner.nextLine();
                questions.add(new Question(category, question, answer));
                scanner.nextLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // Questions Parsed
        ArrayList<Question> correctlyAnswered = new ArrayList<>();
        ArrayList<Question> incorrectlyAnswered = new ArrayList<>();

        // Fetch the Index
        WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
        int hitsPerPage = 1;
        String indexPath = "/Volumes/Samsung_T5/Watson_Project_Indexes/lemma_index";
        Directory index = null;
        try {
            index = FSDirectory.open(Paths.get(indexPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
        IndexSearcher searcher = new IndexSearcher(reader);

        for(Question question : questions) {
            edu.stanford.nlp.simple.Document coreNLPDoc = new edu.stanford.nlp.simple.Document(question.getQuestion());
            String resultDataForDocument = "";
            for (Sentence sent : coreNLPDoc.sentences()) {
                for (String str : sent.lemmas()) {
                    resultDataForDocument += str + " ";
                }
            }
            Query query = null;
            Query phraseQuery;
            TopDocs doc = null;
            TopDocs phraseDocs = null;
            ScoreDoc[] hits = null;
            ScoreDoc[] hitsPhrase = null;
            try {
 //               Processor proc = new CoreNLPProcessor(true, false, 0, 100);
//                proc.annotateFromTokens()
                query = new QueryParser("data", analyzer).parse(resultDataForDocument);
                phraseQuery = new QueryParser("data",analyzer).parse("\"Island country\"~0 AND \"establish in 1912\"~0" );
                doc = searcher.search(query, hitsPerPage);
                phraseDocs = searcher.search(phraseQuery, 50);
                hits = doc.scoreDocs;
                hitsPhrase = phraseDocs.scoreDocs;
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            int docId = hits[0].doc;
            Document d = null;
            try {
                d = searcher.doc(docId);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(question.getAnswer().equals(d.get("title"))) {
                // Correct Answer!
                correctlyAnswered.add(question);
                System.out.println("Correct:");
                System.out.println("    Question: " + question.getQuestion());
                System.out.println("    Answer: " + d.get("title"));


                System.out.println(d.get("data"));
                System.out.print("  Phrase Docs: ");
                for(int i = 0; i < hitsPhrase.length; i++) {
                    Document dPhrase;
                    try {
                        dPhrase = searcher.doc(hitsPhrase[i].doc);
                        System.out.print(dPhrase.get("title") + " , ");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println();
            }
            else {
                // Incorrect Answer!
                incorrectlyAnswered.add(question);
                System.out.println("Incorrect:");
                System.out.println("    Question: " + question.getQuestion());
                System.out.println("    Answer: " + d.get("title"));
                System.out.println("    Correct Answer: " + question.getAnswer());
            }
        }

        System.out.println("Results:");
        System.out.println("   Correct: " + correctlyAnswered.size());
        System.out.println("   Incorrect: " + incorrectlyAnswered.size());
    }
}
