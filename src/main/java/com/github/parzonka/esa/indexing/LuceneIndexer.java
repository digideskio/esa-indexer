/*******************************************************************************
 * Copyright 2013 Mateusz Parzonka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.github.parzonka.esa.indexing;

import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.uimafit.util.JCasUtil.select;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Stem;

/**
 * Creates an external Lucene index created based on stems.
 * 
 * @author Mateusz Parzonka
 * 
 */
public class LuceneIndexer extends JCasAnnotator_ImplBase {

	/**
	 * Path to the directory where the Lucene index is stored.
	 */
	public static final String PARAM_INDEX_PATH = "IndexPath";
	@ConfigurationParameter(name = PARAM_INDEX_PATH, mandatory = true)
	private String indexPath;

	/**
	 * Minimal number of terms per document. Note: Terms are counted *after* the
	 * relevance filter is applied.
	 */
	public static final String PARAM_MIN_TERMS_PER_DOCUMENT = "MinTermsPerDocument";
	@ConfigurationParameter(name = PARAM_MIN_TERMS_PER_DOCUMENT, mandatory = false, defaultValue = "50")
	private int minTermsPerDocument;

	// has to be equal to the private static field LuceneVectorReader.FIELD_NAME
	private final static String FIELD_NAME = "token";
	private final static Matcher characterPattern = Pattern.compile("[a-zA-Z]*").matcher("");

	private File indexDir;
	private IndexWriter indexWriter;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		indexDir = new File(indexPath);
		deleteQuietly(indexDir);
		indexDir.mkdirs();
		
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_35, null);
		try {
			indexWriter = new IndexWriter(FSDirectory.open(indexDir), config);
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		final List<String> terms = new ArrayList<String>();
		// aggregate relevant terms
		for (Stem stem : select(jCas, Stem.class)) {
			final String term = stem.getValue();
			if (isRelevant(term)) {
				terms.add(term);
			}
		}
		// index all terms if the document is long enough
		if (terms.size() > minTermsPerDocument) {
			final Document doc = new Document();
			for (String term : terms) {
				doc.add(new Field(FIELD_NAME, term, Field.Store.YES, Field.Index.NOT_ANALYZED));
			}
			doc.add(new Field("id", DocumentMetaData.get(jCas).getDocumentTitle(), Field.Store.YES,
					Field.Index.NOT_ANALYZED));
			try {
				indexWriter.addDocument(doc);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Returns true if the given term contains only characters and has a length
	 * greater or equals to 3.
	 * 
	 * @param term
	 * @return true if the term is relevant for indexing
	 */
	protected boolean isRelevant(String term) {
		return term.length() >= 3 && matches(characterPattern, term);
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		super.collectionProcessComplete();
		try {
			indexWriter.commit();
			indexWriter.close();
		} catch (CorruptIndexException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private static boolean matches(Matcher matcher, String string) {
		matcher.reset(string);
		return matcher.matches();
	}

}
