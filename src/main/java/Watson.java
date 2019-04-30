import edu.stanford.nlp.simple.Sentence;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.InvalidFormatException;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

public class Watson {

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
        if(args.length == 5 && args[2].equals("-tfidf")) {
            searcher.setSimilarity(new ClassicSimilarity());
        }
        if(args.length == 5 && args[2].equals("-boolean")) {
            searcher.setSimilarity(new BooleanSimilarity());
        }
        if(args.length == 5 && args[2].equals("-BM25")) {
            // Tweak Paramaters
            float k1 = 0.1f;
            float b = 0.75f;
            searcher.setSimilarity(new BM25Similarity(k1, b));
        }
        for(Question question : questions) {
            String resultDataForDocument = "";
            if(args.length == 5 && args[3].equals("-lemma")) {
                edu.stanford.nlp.simple.Document coreNLPDoc = new edu.stanford.nlp.simple.Document(question.getQuestion());
                for (Sentence sent : coreNLPDoc.sentences()) {
                    for (String str : sent.lemmas()) {
                        resultDataForDocument += str + " ";
                    }
                }
            }
            else {
                resultDataForDocument = question.getQuestion();
            }
            // If positonal index, compute noun phrases in question
            if(args.length == 5 && args[4].equals("-pos")) {
                InputStream modelIn = null;
                ChunkerModel model = null;

                try {
                    modelIn = new FileInputStream("en-chunker.bin");
                    model = new ChunkerModel(modelIn);
                } catch (InvalidFormatException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ChunkerME chunker = new ChunkerME(model);

                String sent[] = new String[] { "Rockwell", "International", "Corp.", "'s",
                        "Tulsa", "unit", "said", "it", "signed", "a", "tentative", "agreement",
                        "extending", "its", "contract", "with", "Boeing", "Co.", "to",
                        "provide", "structural", "parts", "for", "Boeing", "'s", "747",
                        "jetliners", "." };

                String pos[] = new String[] { "NNP", "NNP", "NNP", "POS", "NNP", "NN",
                        "VBD", "PRP", "VBD", "DT", "JJ", "NN", "VBG", "PRP$", "NN", "IN",
                        "NNP", "NNP", "TO", "VB", "JJ", "NNS", "IN", "NNP", "POS", "CD", "NNS",
                        "." };

                String tag[] = chunker.chunk(sent, pos);
                System.out.println();
            }
            Query query = null;
            TopDocs doc = null;
            ScoreDoc[] hits = null;
            try {
                query = new QueryParser("data", analyzer).parse(resultDataForDocument);
                doc = searcher.search(query, hitsPerPage);
                hits = doc.scoreDocs;
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(hits.length > 0) {
                int docId = hits[0].doc;
                Document d = null;
                try {
                    d = searcher.doc(docId);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                boolean correct = false;
                if (question.getAnswer().contains("|")) {
                    String[] answers = question.getAnswer().split("\\|");
                    for (int i = 0; i < answers.length; i++) {
                        if (answers[i].toLowerCase().equals(d.get("title").toLowerCase())) {
                            correct = true;
                        }
                    }
                }
                if (correct || question.getAnswer().toLowerCase().equals(d.get("title").toLowerCase())) {
                    // Correct Answer!
                    correctlyAnswered.add(question);
                    System.out.println("Correct:");
                    System.out.println("    Question: " + question.getQuestion());
                    System.out.println("    Answer: " + d.get("title"));
                } else {
                    // Incorrect Answer!
                    incorrectlyAnswered.add(question);
                    System.out.println("Incorrect:");
                    System.out.println("    Question: " + question.getQuestion());
                    System.out.println("    Answer: " + d.get("title"));
                    System.out.println("    Correct Answer: " + question.getAnswer());
                }
            }
            else {
                // Incorrect Answer!
                incorrectlyAnswered.add(question);
                System.out.println("Incorrect:");
                System.out.println("    Question: " + question.getQuestion());
                System.out.println("    Answer: NO ANSWER!");
                System.out.println("    Correct Answer: " + question.getAnswer());
            }
        }

        System.out.println("Results:");
        System.out.println("   Correct: " + correctlyAnswered.size());
        System.out.println("   Incorrect: " + incorrectlyAnswered.size());
    }
}
