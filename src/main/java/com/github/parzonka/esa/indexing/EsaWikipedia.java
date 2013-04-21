package com.github.parzonka.esa.indexing;

import static org.uimafit.factory.AnalysisEngineFactory.createPrimitive;
import static org.uimafit.factory.CollectionReaderFactory.createCollectionReader;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.uimafit.pipeline.SimplePipeline;

import de.tudarmstadt.ukp.dkpro.core.io.jwpl.WikipediaReaderBase;
import de.tudarmstadt.ukp.dkpro.core.snowball.SnowballStemmer;
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants.Language;

/**
 * Indexes Wikipedia and creates an inverted index to be used with ESA. 
 * 
 * @author Mateusz Parzonka
 *
 */
public class EsaWikipedia {
	
	private final static String luceneIndexPath = "target/lucene";
	private final static String esaIndexPath = "target/esa";
	
	public static void main(String[] args) throws Exception {

		createLuceneWikipediaIndex();
		createInvertedIndex();
		
	}

	/**
	 * Creates a Lucene index from Wikipedia based on lower cased stems with length >=3 containing only characters.
	 * 
	 * @throws UIMAException
	 * @throws IOException
	 */
	private static void createLuceneWikipediaIndex() throws UIMAException, IOException {
		CollectionReader reader = createCollectionReader(
				ExtendedWikipediaArticleReader.class,
				WikipediaReaderBase.PARAM_HOST, "localhost",
				WikipediaReaderBase.PARAM_DB, "DEWIKI",
				WikipediaReaderBase.PARAM_USER, "root",
				WikipediaReaderBase.PARAM_PASSWORD, "jimmywales",
				WikipediaReaderBase.PARAM_LANGUAGE, Language.german);
		
		AnalysisEngine segmenter = createPrimitive(
				BreakIteratorSegmenter.class,
				BreakIteratorSegmenter.PARAM_LOCALE, Locale.GERMAN);
		
		AnalysisEngine stemmer = createPrimitive(
				SnowballStemmer.class,
				SnowballStemmer.PARAM_LANGUAGE, "de",
				SnowballStemmer.PARAM_LOWER_CASE, true);
		
		AnalysisEngine indexTermGenerator = createPrimitive(
				LuceneIndexer.class,
				LuceneIndexer.PARAM_INDEX_PATH, luceneIndexPath,
				LuceneIndexer.PARAM_MIN_TERMS_PER_DOCUMENT, 50);

		SimplePipeline.runPipeline(reader, segmenter, stemmer, indexTermGenerator);
		
	}
	
	/**
	 * Creates an inverted index for ESA
	 * 
	 * @throws Exception
	 */
	private static void createInvertedIndex() throws Exception {
		IndexInverter indexInverter = new IndexInverter(new File(luceneIndexPath), new File(esaIndexPath));
		indexInverter.createInvertedIndex();
	}

}
