import edu.stanford.nlp.simple.Sentence;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.InvalidFormatException;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class Watson {

    public static void main(String args[]) {
        // Parse Questions
        File questionFile = new File(args[0]);
        ArrayList<Question> questions = new ArrayList<>();
        try {
            Scanner scanner = new Scanner(questionFile);
            while (scanner.hasNextLine()) {
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
        String indexPath = args[1];
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
        if (args.length == 5 && args[2].equals("-tfidf")) {
            searcher.setSimilarity(new ClassicSimilarity());
        }
        if (args.length == 5 && args[2].equals("-boolean")) {
            searcher.setSimilarity(new BooleanSimilarity());
        }
        if (args.length == 5 && args[2].equals("-BM25")) {
            // Tweak Paramaters
            float k1 = 0.1f;
            float b = 0.75f;
            searcher.setSimilarity(new BM25Similarity(k1, b));
        }
        for (Question question : questions) {
            String resultDataForDocument = "";
            if (args.length == 5 && args[3].equals("-lemma")) {
                edu.stanford.nlp.simple.Document coreNLPDoc = new edu.stanford.nlp.simple.Document(question.getQuestion());
                for (Sentence sent : coreNLPDoc.sentences()) {
                    for (String str : sent.lemmas()) {
                        resultDataForDocument += str + " ";
                    }
                }
            } else {
                resultDataForDocument = question.getQuestion();
            }
            // If positonal index, compute noun phrases in question
            ArrayList<ArrayList<String>> nounPhrases = null;
            BooleanQuery phraseQuery = null;
            if (args.length == 5 && args[4].equals("-pos")) {
                InputStream modelIn = null;
                ChunkerModel model = null;
                InputStream modelInPOS = null;
                POSModel modelPOS = null;

                try {
                    modelIn = new FileInputStream("en-chunker.bin");
                    modelInPOS = new FileInputStream("en-pos-maxent.bin");
                    model = new ChunkerModel(modelIn);
                    modelPOS = new POSModel(modelInPOS);
                } catch (InvalidFormatException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ChunkerME chunker = new ChunkerME(model);
                POSTaggerME tagger = new POSTaggerME(modelPOS);

                Scanner scannerString = new Scanner(resultDataForDocument);
                ArrayList<String> queryList = new ArrayList<>();
                while (scannerString.hasNext()) {
                    queryList.add(scannerString.next());
                }


                String queryArray[] = new String[queryList.size()];
                for (int i = 0; i < queryList.size(); i++) {
                    queryArray[i] = queryList.get(i);
                }

                String tags[] = tagger.tag(queryArray);

                String tag[] = chunker.chunk(queryArray, tags);

                nounPhrases = computeNounPhrases(queryArray, tag);
                if (nounPhrases != null) {
                    ArrayList<PhraseQuery> phraseQueries = new ArrayList<>();
                    for (ArrayList<String> nouns : nounPhrases) {
                        if (nouns.size() > 1) {
                            PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
                            phraseBuilder.setSlop(20);
                            for (String str : nouns) {
                                phraseBuilder.add(new Term("data", str));
                            }
                            phraseQueries.add(phraseBuilder.build());
                        }
                    }
                    BooleanQuery.Builder booleanBuilder = new BooleanQuery.Builder();
                    for (PhraseQuery phrase : phraseQueries) {
                        booleanBuilder.add(phrase, BooleanClause.Occur.SHOULD);
                    }
                    try {
                        Query query = null;
                        if((args.length == 5 && args[3].equals("-nolemma"))) {
                            query = new QueryParser("data", analyzer).parse(QueryParser.escape(resultDataForDocument));
                        }
                        else {
                            query = new QueryParser("data", analyzer).parse(resultDataForDocument);
                        }
                        Query category = new QueryParser("data", analyzer).parse(QueryParser.escape(question.getCategory()));
                        booleanBuilder.add(query, BooleanClause.Occur.MUST);
                        booleanBuilder.add(category, BooleanClause.Occur.SHOULD);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    phraseQuery = booleanBuilder.build();
                }
            }
            BooleanQuery queryWithCategory = null;
            Query query = null;
            TopDocs doc = null;
            ScoreDoc[] hits = null;
            try {
                BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
                if((args.length == 5 && args[3].equals("-nolemma"))) {
                    query = new QueryParser("data", analyzer).parse(QueryParser.escape(resultDataForDocument));
                }
                else {
                    query = new QueryParser("data", analyzer).parse(resultDataForDocument);
                }
                Query category = new QueryParser("data", analyzer).parse(QueryParser.escape(question.getCategory()));
                booleanQuery.add(query, BooleanClause.Occur.MUST);
                booleanQuery.add(category, BooleanClause.Occur.SHOULD);
                queryWithCategory = booleanQuery.build();
                if (args.length == 5 && args[4].equals("-pos") && phraseQuery != null) {
                    doc = searcher.search(phraseQuery, hitsPerPage);
                } else {
                    doc = searcher.search(queryWithCategory, hitsPerPage);
                }
                hits = doc.scoreDocs;
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (hits.length > 0) {
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
            } else {
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

    private static ArrayList<ArrayList<String>> computeNounPhrases(String[] test, String[] tag) {
        ArrayList<ArrayList<String>> result = new ArrayList<>();
        ArrayList<String> currentNounPhrase = null;
        int i = 0;
        for (String str : tag) {
            if (str.equals("B-NP")) {
                if (currentNounPhrase == null) {
                    currentNounPhrase = new ArrayList<>();
                    currentNounPhrase.add(test[i]);
                } else {
                    // Add previous as a phrase
                    result.add(currentNounPhrase);
                    currentNounPhrase = new ArrayList<>();
                    currentNounPhrase.add(test[i]);
                }
            }
            if (str.equals("I-NP")) {
                currentNounPhrase.add(test[i]);
            }
            i++;
        }
        if (currentNounPhrase != null && currentNounPhrase.size() > 0) {
            result.add(currentNounPhrase);
        }
        return result;
    }
}
