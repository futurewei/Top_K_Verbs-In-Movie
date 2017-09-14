package com.company;

import java.util.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import java.io.FileNotFoundException;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

//wrap up words information and occurrence for priorityQueue comparator use.
class Node {
    int occurrence;
    String str;

    public Node(String s, int o) {
        occurrence = o;
        str = s;
    }
}

public class Moogsoft {
    //this file is the movie script, used for NLP filtering.
    private static final String FILENAME = "./movie_script.txt";
    //this file is the output file of our NLP filtering results. It contains distinct verb words and is
    // sorted in descending order based on occurrence number.
    private static final String FREQUENCY_FILE = "./word_frequency.txt";
    //Thie file contains 42 distinct adj words. A small sample file to test if interval bucket works correctly.
    private static final String TESTING_FILE = "./testing_file.txt";
    private static final String PRIORITY_FILE = "./Priority_test.txt";
    private static final String PRIORITY_OUTPUT = "./Priority_output.txt";

    public static void main(String[] args) {
        //take out comment to use preProcessing function.
        //preProcessing(FILENAME, FREQUENCY_FILE);
        Movie_Interpreter interpreter = new Movie_Interpreter(FREQUENCY_FILE);
        System.out.println("--------Test consecutive retrieves------");
        interpreter.print_top_K(20);
        System.out.println("--------Second retrieve------");
        interpreter.print_top_K(30);
        System.out.println("--------Test end point--------");
        interpreter.print_top_K(41);
        System.out.println("--------Test Large File --------");
        interpreter.print_top_K(200);
        System.out.println("--------Test Large File II--------");
        interpreter.print_top_K(357);
        System.out.println("--------Test negative number------");
        interpreter.print_top_K(-100);
        System.out.println("--------Test out of bounds-------");
        interpreter.print_top_K(100000);
    }

    public static void preProcessing(String input_file, String output_file) {
        // creates a StanfordCoreNLP object, with POS tagging.
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        HashMap<String, Integer> wordCount = new HashMap<>();
        Annotation document;
        try (BufferedReader br = new BufferedReader(new FileReader(input_file))) {
            String line;

            while ((line = br.readLine()) != null) {

                document = new Annotation(line);

                // run all Annotators on this text
                pipeline.annotate(document);

                // these are all the sentences in this document
                // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
                List<CoreMap> sentences2 = document.get(SentencesAnnotation.class);

                for (CoreMap sentence : sentences2) {
                    // traversing the words in the current sentence
                    // a CoreLabel is a CoreMap with additional token-specific methods
                    for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                        // this is the text of the token
                        String word = token.get(TextAnnotation.class);
                        // this is the POS tag of the token
                        String pos = token.get(PartOfSpeechAnnotation.class);

                        //only keep verbs
                        if (pos.equals("VB")) {
                            if (!wordCount.containsKey((word))) {
                                wordCount.put(word, 1);
                            } else {
                                int prevCount = wordCount.get(word);
                                wordCount.put(word, ++prevCount);
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("---------NLP classification done---------");
        int totalWords = wordCount.size();
        PriorityQueue<Node> pq = new PriorityQueue<>(totalWords, new Comparator<Node>() {
            public int compare(Node n1, Node n2) {
                //in case of occurrence of two words are the same
                if (n1.occurrence == n2.occurrence) {
                    return n1.str.compareTo(n2.str);
                } else {
                    return n2.occurrence - n1.occurrence;
                }
            }
        });

        for (String word : wordCount.keySet()) {
            Node strNode = new Node(word, wordCount.get(word));
            pq.offer(strNode);
        }


        BufferedWriter bw = null;
        try {
            //Specify the file name and path here
            File file = new File(output_file);
            if (!file.exists()) {
                file.createNewFile();
            }
            System.out.println("--------Start writing to File---------");
            FileWriter fw = new FileWriter(file);
            bw = new BufferedWriter(fw);
            while (!pq.isEmpty()) {
                String word = pq.poll().str;
                bw.write(word + " ");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (bw != null)
                    bw.close();
            } catch (Exception ex) {
                System.out.println("Error in closing the BufferedWriter" + ex);
            }
        }
    }
}


class Movie_Interpreter {
    HashMap<Integer, String> interval_mem;
    private int capacity;
    private int interval = 50;

    public Movie_Interpreter(String file) {
        interval_mem = new HashMap<>();
        //initialize putting stuff to interval_mem;
        Scanner sc2 = null;
        int count = 0;
        try {
            sc2 = new Scanner(new File(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder line = new StringBuilder();
        while (sc2.hasNextLine()) {
            Scanner s2 = new Scanner(sc2.nextLine());
            while (s2.hasNext()) {
                String s = s2.next();
                line.append(" " + s);
                count++;
                //if we can completely fill a bucket
                if (count % interval == 0)
                {
                    //bucketID is count/interval: example 50/50= bucket 1   100/50=bucket2
                    interval_mem.put(count / interval, line.toString());
                    line.setLength(0);
                }
            }
        }
        //for the leftout
        if (count % interval != 0) {
            interval_mem.put(count / interval + 1, line.toString());
        }

        //total number of distinct words
        this.capacity = count;
    }

    public void print_top_K(int k) {
        if (k > capacity || k <= 0) {
            System.out.println("Please enter a number smaller or equal than " + capacity + ", and bigger than 0");
            return;
        }

        int bucket = 1;
        while (k >= bucket * interval) {
            System.out.println(interval_mem.get(bucket));
            bucket += 1;
        }

        //For remaining K. Suppose bucket size 5, retrive top 6. first 5 will be chosen from bucket1, extras from here
        if ((bucket - 1) * interval < k && k < (bucket) * interval) {
            int diff = k - (bucket - 1) * interval;
            String[] strs = interval_mem.get(bucket).split("\\s+");
            for (int i = 1; i <= diff; i++) {
                //start from index 1 since first one is a space.
                System.out.print(" " + strs[i]);
            }
            System.out.println();
        }
    }
}

/****************************Sample Output********************
--------Test consecutive retrieves------
 be get do have see
 Do take know make Look
 let say go kill tell
 understand feel die look ask
--------Second retrieve------
 be get do have see
 Do take know make Look
 let say go kill tell
 understand feel die look ask
 Come find Wait believe fuck
 give mean think wait work
--------Test end point--------
 be get do have see
 Do take know make Look
 let say go kill tell
 understand feel die look ask
 Come find Wait believe fuck
 give mean think wait work
 've Get Let Remember Thank
 keep leave like need start
 talk
--------Test negative number------
Please enter a number smaller or equal than 357, and bigger than 0
--------Test out of bounds-------
Please enter a number smaller or equal than 357, and bigger than 0
 */
